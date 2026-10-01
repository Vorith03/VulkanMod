#!/usr/bin/env python3
"""Inventory and validate raw Vulkan command call sites against command-trace coverage.

The profiler is intended to be capture-first: a new vkCmd*/nvkCmd* producer must not
silently bypass the query-later command stream. Calls routed through
TracedVulkanCommands are already covered and are omitted from the raw inventory.

Check mode turns the remaining direct call sites into a hard CI contract. Each
manifest key must either name a concrete registered tracer or carry an explicit
EXEMPT:<reason> debt marker. Exemptions are exact call-site keys, never wildcard
source exclusions, so adding a new raw command still fails CI.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import dataclass
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
SOURCE_ROOT = REPO / "src/main/java/net/vulkanmod"
DEFAULT_MANIFEST = REPO / "scripts/ci/vulkan-command-trace-coverage.tsv"
MIXIN_CONFIG = REPO / "src/main/resources/vulkanmod.mixins.json"

COMMAND_RE = re.compile(r"\b(?P<command>n?vkCmd[A-Za-z0-9_]+)\s*\(")
METHOD_RE = re.compile(
    r"(?m)^[ \t]*"
    r"(?:(?:public|protected|private|static|final|synchronized|native|abstract|strictfp)\s+)*"
    r"(?:[A-Za-z_$][\w$\.\[\]<>?,]*\s+)+"
    r"(?P<name>[A-Za-z_$][\w$]*)\s*"
    r"\([^;{}]*\)\s*(?:throws\s+[^\{]+)?\{"
)
CONSTRUCTOR_RE = re.compile(
    r"(?m)^[ \t]*(?:(?:public|protected|private)\s+)?"
    r"(?P<name>[A-Z][\w$]*)\s*\([^;{}]*\)\s*(?:throws\s+[^\{]+)?\{"
)
FACADE_IMPORT_RE = re.compile(
    r"(?m)^[ \t]*import\s+static\s+"
    r"net\.vulkanmod\.render\.profiling\.TracedVulkanCommands\."
    r"(?P<command>\*|n?vkCmd[A-Za-z0-9_]+)\s*;"
)
QUALIFIER_RE = re.compile(r"(?P<owner>[A-Za-z_$][\w$]*)\s*\.\s*$")

EXCLUDED_PREFIXES = (
    "src/main/java/net/vulkanmod/mixin/profiling/",
    "src/main/java/net/vulkanmod/render/profiling/",
)
MIXIN_SOURCE_PREFIX = "src/main/java/net/vulkanmod/mixin/"
EXEMPT_PREFIX = "EXEMPT:"


@dataclass(frozen=True, order=True)
class CallSite:
    source: str
    method: str
    command: str
    line: int

    @property
    def key(self) -> tuple[str, str, str]:
        return self.source, self.method, self.command


@dataclass(frozen=True)
class MethodRange:
    start: int
    end: int
    name: str


def sanitize_java(text: str) -> str:
    """Blank comments and literals while preserving offsets/newlines."""
    chars = list(text)
    i = 0
    state = "code"
    quote = ""
    while i < len(chars):
        c = chars[i]
        n = chars[i + 1] if i + 1 < len(chars) else ""
        if state == "code":
            if c == "/" and n == "/":
                chars[i] = chars[i + 1] = " "
                i += 2
                state = "line"
                continue
            if c == "/" and n == "*":
                chars[i] = chars[i + 1] = " "
                i += 2
                state = "block"
                continue
            if c in ('"', "'"):
                quote = c
                chars[i] = " "
                i += 1
                state = "string"
                continue
        elif state == "line":
            if c == "\n":
                state = "code"
            else:
                chars[i] = " "
            i += 1
            continue
        elif state == "block":
            if c == "*" and n == "/":
                chars[i] = chars[i + 1] = " "
                i += 2
                state = "code"
                continue
            if c != "\n":
                chars[i] = " "
            i += 1
            continue
        elif state == "string":
            if c == "\\":
                chars[i] = " "
                if i + 1 < len(chars):
                    if chars[i + 1] != "\n":
                        chars[i + 1] = " "
                    i += 2
                    continue
            if c == quote:
                chars[i] = " "
                i += 1
                state = "code"
                continue
            if c != "\n":
                chars[i] = " "
            i += 1
            continue
        i += 1
    return "".join(chars)


def closing_brace(clean: str, opening: int) -> int:
    """Return the exclusive end of the block opened at opening."""
    depth = 0
    for index in range(opening, len(clean)):
        char = clean[index]
        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return index + 1
    return len(clean)


def method_ranges(clean: str) -> list[MethodRange]:
    """Locate method bodies so a call is attributed to its enclosing method."""
    ranges: list[MethodRange] = []
    for pattern in (METHOD_RE, CONSTRUCTOR_RE):
        for match in pattern.finditer(clean):
            opening = match.end() - 1
            ranges.append(MethodRange(match.start(), closing_brace(clean, opening), match.group("name")))
    ranges.sort(key=lambda method: (method.start, -method.end))
    return ranges


def method_for(position: int, methods: list[MethodRange]) -> str:
    enclosing = [method for method in methods if method.start <= position < method.end]
    if not enclosing:
        return "<class-init>"
    return max(enclosing, key=lambda method: method.start).name


def facade_imports(clean: str) -> set[str]:
    return {match.group("command") for match in FACADE_IMPORT_RE.finditer(clean)}


def routed_through_facade(clean: str, match: re.Match[str], imports: set[str]) -> bool:
    """Return true when this source-level invocation resolves to our traced facade."""
    command = match.group("command")
    prefix = clean[max(0, match.start() - 96):match.start()]
    qualifier = QUALIFIER_RE.search(prefix)
    if qualifier is not None:
        return qualifier.group("owner") == "TracedVulkanCommands"
    return "*" in imports or command in imports


def discover() -> list[CallSite]:
    found: list[CallSite] = []
    for path in sorted(SOURCE_ROOT.rglob("*.java")):
        rel = path.relative_to(REPO).as_posix()
        if rel.startswith(EXCLUDED_PREFIXES):
            continue
        text = path.read_text(encoding="utf-8")
        clean = sanitize_java(text)
        methods = method_ranges(clean)
        imports = facade_imports(clean)
        for match in COMMAND_RE.finditer(clean):
            if routed_through_facade(clean, match, imports):
                continue
            line = clean.count("\n", 0, match.start()) + 1
            found.append(CallSite(rel, method_for(match.start(), methods), match.group("command"), line))
    return sorted(found)


def load_manifest(path: Path) -> dict[tuple[str, str, str], str]:
    entries: dict[tuple[str, str, str], str] = {}
    for number, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        fields = line.split("\t")
        if len(fields) != 4:
            raise ValueError(f"{path}:{number}: expected 4 tab-separated fields")
        source, method, command, tracer = fields
        key = (source, method, command)
        if key in entries:
            raise ValueError(f"{path}:{number}: duplicate coverage key {key}")
        if tracer.startswith(EXEMPT_PREFIX) and not tracer[len(EXEMPT_PREFIX):].strip():
            raise ValueError(f"{path}:{number}: EXEMPT entry requires a reason")
        entries[key] = tracer
    return entries


def registered_mixins() -> set[str]:
    try:
        config = json.loads(MIXIN_CONFIG.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise ValueError(f"could not read mixin config {MIXIN_CONFIG}: {exc}") from exc
    registered: set[str] = set()
    for section in ("mixins", "client", "server"):
        values = config.get(section, [])
        if isinstance(values, list):
            registered.update(value for value in values if isinstance(value, str))
    return registered


def tracer_mixin_id(tracer: str) -> str | None:
    if not tracer.startswith(MIXIN_SOURCE_PREFIX) or not tracer.endswith(".java"):
        return None
    relative = tracer[len(MIXIN_SOURCE_PREFIX):-len(".java")]
    return relative.replace("/", ".")


def verify_tracer(
    key: tuple[str, str, str],
    tracer: str,
    registered: set[str],
) -> str | None:
    source, method, command = key
    if tracer.startswith(EXEMPT_PREFIX):
        return None
    path = REPO / tracer
    if not path.is_file():
        return f"{source}|{method}|{command}: tracer does not exist: {tracer}"
    text = path.read_text(encoding="utf-8")
    if "VulkanCommandTrace" not in text:
        return f"{source}|{method}|{command}: tracer never references VulkanCommandTrace: {tracer}"
    if command not in text:
        return f"{source}|{method}|{command}: tracer does not mention command: {tracer}"
    if method not in text:
        return f"{source}|{method}|{command}: tracer does not mention target method: {tracer}"
    mixin_id = tracer_mixin_id(tracer)
    if mixin_id is not None and mixin_id not in registered:
        return f"{source}|{method}|{command}: tracer mixin is not registered: {mixin_id}"
    return None


def print_inventory(calls: list[CallSite]) -> None:
    print(f"Direct Vulkan command call sites: {len(calls)}")
    for call in calls:
        print(f"TRACE_RAW_CALLSITE\t{call.source}\t{call.method}\t{call.command}\t{call.line}")


def check(calls: list[CallSite], manifest_path: Path) -> int:
    if not manifest_path.is_file():
        print(f"Coverage manifest missing: {manifest_path}", file=sys.stderr)
        return 1
    try:
        manifest = load_manifest(manifest_path)
        registered = registered_mixins()
    except ValueError as exc:
        print(exc, file=sys.stderr)
        return 1

    actual_by_key: dict[tuple[str, str, str], list[CallSite]] = {}
    for call in calls:
        actual_by_key.setdefault(call.key, []).append(call)

    actual = set(actual_by_key)
    expected = set(manifest)
    failures: list[str] = []
    for key in sorted(actual - expected):
        lines = ",".join(str(call.line) for call in actual_by_key[key])
        failures.append("uncovered Vulkan command callsite: " + "|".join(key) + f" (lines {lines})")
    for key in sorted(expected - actual):
        failures.append("stale Vulkan command coverage entry: " + "|".join(key))
    for key in sorted(actual & expected):
        problem = verify_tracer(key, manifest[key], registered)
        if problem:
            failures.append(problem)

    if failures:
        print_inventory(calls)
        print("\nVulkan command trace coverage FAILED:", file=sys.stderr)
        for failure in failures:
            print(f" - {failure}", file=sys.stderr)
        return 1

    exempt = sum(1 for tracer in manifest.values() if tracer.startswith(EXEMPT_PREFIX))
    traced = len(manifest) - exempt
    print(
        f"Vulkan command trace coverage OK: {len(actual)} unique raw callsite keys / "
        f"{len(calls)} raw calls ({traced} traced, {exempt} explicit exemptions)"
    )
    return 0


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--inventory",
        action="store_true",
        help="print direct Vulkan callsites not routed through the traced facade",
    )
    parser.add_argument("--manifest", type=Path, default=DEFAULT_MANIFEST)
    args = parser.parse_args()

    calls = discover()
    if args.inventory:
        print_inventory(calls)
        return 0
    return check(calls, args.manifest)


if __name__ == "__main__":
    raise SystemExit(main())
