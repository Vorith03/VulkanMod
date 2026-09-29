#!/usr/bin/env python3
"""Check an actual Vulkan startup capture for parseable, consistent profiler windows."""

import pathlib
import sys


def fields(line):
    return dict(token.split("=", 1) for token in line.split() if "=" in token)


path = pathlib.Path(sys.argv[1])
lines = path.read_text(encoding="utf-8").splitlines()
assert any("[VulkanModPerf] capture_start " in line for line in lines), "missing profiler start"
assert any("[VulkanModPerf] environment " in line for line in lines), "missing environment"

windows = [index for index, line in enumerate(lines) if "[VulkanModPerf] window " in line]
assert windows, "no completed profiler windows in startup smoke"
for start, stop in zip(windows, windows[1:] + [len(lines)]):
    block = lines[start:stop]
    window = fields(block[0])
    frame_class = next(fields(line) for line in block if "[VulkanModPerf] frame_classes " in line)
    gap = next(fields(line) for line in block if "[VulkanModPerf] loop_gap " in line)
    frames = int(window["frames"])
    assert window["context"] in {"menu", "world", "transition"}
    assert frames > 0
    assert int(frame_class["tick_frames"]) + int(frame_class["render_only_frames"]) == frames
    assert int(gap["samples"]) <= frames
    for label in ("stage_avg_ms", "stage_p95_ms", "tick_stage_p95_ms", "accounting_overlap_frames", "jvm"):
        assert any(f"[VulkanModPerf] {label} " in line for line in block), label

print(f"Profiler output contract passed: {len(windows)} completed startup windows")
