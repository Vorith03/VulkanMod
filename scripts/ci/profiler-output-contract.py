#!/usr/bin/env python3
"""Check actual Vulkan startup captures for summaries, JFR, and raw command evidence."""

import pathlib
import struct
import subprocess
import sys
import uuid


def fields(line):
    return dict(token.split("=", 1) for token in line.split() if "=" in token)


path = pathlib.Path(sys.argv[1])
lines = path.read_text(encoding="utf-8").splitlines()
assert any("[VulkanModPerf] capture_start " in line for line in lines), "missing profiler start"
assert any("[VulkanModPerf] environment " in line for line in lines), "missing environment"
identity = next(fields(line) for line in lines if "[VulkanModPerf] capture_identity " in line)
assert identity["schema"] == "1"
uuid.UUID(identity["run_id"])
assert identity["vulkanmod_version"]
assert identity["automated"] == "false", "startup smoke must not manipulate a world"

windows = [index for index, line in enumerate(lines) if "[VulkanModPerf] window " in line]
assert windows, "no completed profiler windows in startup smoke"
for start, stop in zip(windows, windows[1:] + [len(lines)]):
    block = lines[start:stop]
    window = fields(block[0])
    frame_class = next(fields(line) for line in block if "[VulkanModPerf] frame_classes " in line)
    gap = next(fields(line) for line in block if "[VulkanModPerf] loop_gap " in line)
    accounting = next(fields(line) for line in block if "[VulkanModPerf] accounting_overlap_frames " in line)
    resources = next(fields(line) for line in block if "[VulkanModPerf] tick_resources " in line)
    overhead = next(fields(line) for line in block if "[VulkanModPerf] profiler_overhead " in line)
    examples = [fields(line) for line in block if "[VulkanModPerf] frame_example " in line]
    frames = int(window["frames"])
    assert window["context"] in {"menu", "world", "transition"}
    assert frames > 0
    assert 0 <= int(window["player_frames"]) <= frames
    assert 0 <= int(window["screen_frames"]) <= frames
    assert int(frame_class["tick_frames"]) + int(frame_class["render_only_frames"]) == frames
    assert int(gap["samples"]) <= frames
    assert int(resources["tick_frames"]) == int(frame_class["tick_frames"])
    assert 0 <= int(resources["cpu_samples"]) <= int(resources["tick_frames"])
    assert 0 <= int(resources["allocation_samples"]) <= int(resources["tick_frames"])
    assert float(overhead["summary_ms"]) >= 0
    for key in ("top_level", "tick_parent", "game_children", "world_detail", "tick_unbalanced"):
        assert 0 <= int(accounting[key]) <= frames, (key, accounting)
    assert sum(example["class"] == "tick" for example in examples) <= 6
    assert sum(example["class"] == "slow_render" for example in examples) <= 6
    assert len({example["frame_id"] for example in examples}) == len(examples)
    assert all(float(example["total_ms"]) >= 0 for example in examples)
    if frames >= 5:
        assert int(accounting["top_level"]) < frames // 2, accounting
        assert float(accounting["top_level_excess_ms"]) < float(window["duration_s"]) * 1000 * .05, accounting
    for label in ("stage_avg_ms", "stage_p95_ms", "tick_stage_p95_ms", "accounting_overlap_frames", "jvm"):
        assert any(f"[VulkanModPerf] {label} " in line for line in block), label

flight_recordings = sorted(
    path.parent.glob("vulkanmod-performance-flight-*.jfr"),
    key=lambda candidate: candidate.stat().st_mtime_ns,
)
assert flight_recordings, "missing raw JFR flight recording"
jfr = flight_recordings[-1]
assert jfr.stat().st_size > 0, "empty raw JFR flight recording"

summary = subprocess.run(
    ["jfr", "summary", str(jfr)],
    check=True,
    capture_output=True,
    text=True,
).stdout
assert "net.vulkanmod.RunTickFrame" in summary, "JFR missing VulkanMod frame event type"

frames_json = subprocess.run(
    ["jfr", "print", "--json", "--events", "net.vulkanmod.RunTickFrame", str(jfr)],
    check=True,
    capture_output=True,
    text=True,
).stdout
assert '"net.vulkanmod.RunTickFrame"' in frames_json, "JFR contains no recorded frame events"

metadata = subprocess.run(
    ["jfr", "metadata", str(jfr)],
    check=True,
    capture_output=True,
    text=True,
).stdout
for event_type in (
    "net.vulkanmod.VulkanSubmission",
    "net.vulkanmod.VulkanFenceWait",
    "net.vulkanmod.VulkanApi",
    "net.vulkanmod.GpuCommandBuffer",
):
    assert event_type in metadata, f"JFR metadata missing {event_type}"

# The fixed-width command sidecar is deliberately independent of JFR's event
# machinery. Prove that a real launch creates a structurally complete stream and
# that command-buffer lifetimes can be joined to the canonical JFR submissions.
command_trace = jfr.with_suffix(".vkcmd")
assert command_trace.exists(), f"missing Vulkan command trace next to {jfr.name}"
raw = command_trace.read_bytes()
assert len(raw) >= 64 + 64, "command trace contains no records"
assert raw[:8] == b"VMVKCMD1", "bad command trace magic"
version, record_bytes = struct.unpack_from("<II", raw, 8)
assert version == 1, version
assert record_bytes == 64, record_bytes
assert (len(raw) - 64) % record_bytes == 0, "partial command trace record"
opcodes = [
    struct.unpack_from("<I", raw, offset)[0]
    for offset in range(64, len(raw), record_bytes)
]
for opcode, label in ((1, "begin"), (2, "end"), (3, "submit"), (255, "capture footer")):
    assert opcode in opcodes, f"command trace missing {label} record"
assert opcodes[-1] == 255, "command trace footer must be last"

footer = struct.unpack_from("<II7Q", raw, len(raw) - record_bytes)
# Footer payload a/b/c = accepted records / dropped records / dropped chunks.
accepted, dropped_records, dropped_chunks = footer[3], footer[4], footer[5]
assert accepted > 0
assert dropped_records == 0, f"startup command trace overflowed: {dropped_records} records"
assert dropped_chunks == 0, f"startup command trace overflowed: {dropped_chunks} chunks"

command_metadata = pathlib.Path(str(command_trace) + ".meta.jsonl")
assert command_metadata.exists(), "missing Vulkan command object metadata sidecar"

print(
    f"Profiler output contract passed: {len(windows)} completed startup windows; "
    f"raw JFR={jfr.name} ({jfr.stat().st_size} bytes); "
    f"vkcmd={command_trace.name} ({len(opcodes)} records)"
)
