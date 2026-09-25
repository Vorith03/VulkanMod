#!/usr/bin/env python3
"""Preflight real pack ZIPs and verify selected-pack survival in a Minecraft log."""

import argparse
import hashlib
import json
import sys
import zipfile
from pathlib import Path


def preflight(paths):
    for path in paths:
        with zipfile.ZipFile(path) as archive:
            names = archive.namelist()
            if "pack.mcmeta" not in names or len(names) != len(set(names)):
                raise ValueError(f"invalid resource pack directory or duplicate entries: {path}")
            if any(name.startswith("/") or ".." in Path(name).parts for name in names):
                raise ValueError(f"unsafe archive path: {path}")
            metadata = json.loads(archive.read("pack.mcmeta"))
            if metadata.get("pack", {}).get("pack_format") != 15:
                raise ValueError(f"expected Minecraft 1.20.1 pack_format 15: {path}")
            bad = archive.testzip()
            if bad:
                raise ValueError(f"corrupt ZIP entry in {path}: {bad}")
        digest = hashlib.sha256()
        with path.open("rb") as stream:
            for chunk in iter(lambda: stream.read(1024 * 1024), b""):
                digest.update(chunk)
        print(f"Resource pack ready: {path.name}, {len(names)} entries, sha256 {digest.hexdigest()}")


def verify(log_path, options_path, expected):
    log = log_path.read_text(errors="replace")
    first_reload = next((line for line in log.splitlines()
                         if "Reloading ResourceManager:" in line), None)
    if first_reload is None:
        raise ValueError("no initial Reloading ResourceManager line; pack load was not tested")
    missing = [pack for pack in expected if pack not in first_reload]
    if missing:
        raise ValueError(f"initial reload did not include selected pack(s): {missing}")
    if "Caught error loading resourcepacks, removing all selected resourcepacks" in log:
        raise ValueError("Minecraft rolled back the selected resource packs")
    options_line = next((line for line in options_path.read_text().splitlines()
                         if line.startswith("resourcePacks:")), None)
    if options_line is None:
        raise ValueError("options.txt lacks resourcePacks")
    selected = json.loads(options_line.removeprefix("resourcePacks:"))
    missing = [pack for pack in expected if pack not in selected]
    if missing:
        raise ValueError(f"pack(s) absent from final options.txt: {missing}")
    print(f"Initial reload and final selection retain {len(expected)} selected resource pack(s)")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    modes = parser.add_subparsers(dest="mode", required=True)
    input_mode = modes.add_parser("preflight")
    input_mode.add_argument("packs", nargs="+", type=Path)
    verify_mode = modes.add_parser("verify")
    verify_mode.add_argument("--log", required=True, type=Path)
    verify_mode.add_argument("--options", required=True, type=Path)
    verify_mode.add_argument("--expected", action="append", required=True)
    args = parser.parse_args()
    try:
        if args.mode == "preflight":
            preflight(args.packs)
        else:
            verify(args.log, args.options, args.expected)
    except (OSError, ValueError, zipfile.BadZipFile, json.JSONDecodeError) as error:
        parser.exit(1, f"Resource pack {args.mode} failed: {error}\n")


if __name__ == "__main__":
    main()
