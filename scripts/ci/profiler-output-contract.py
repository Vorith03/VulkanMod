#!/usr/bin/env python3
"""Check an actual Vulkan startup capture for parseable, consistent profiler windows."""

import pathlib
import sys
import uuid


def fields(line):
    return dict(token.split("=", 1) for token in line.split() if "=" in token)


path = pathlib.Path(sys.argv[1])
lines = path.read_text(encoding="utf-8").splitlines()
capture = next(fields(line) for line in lines if "[VulkanModPerf] capture_start " in line)
assert capture["gpu_timestamps"] == "true", "startup smoke must activate requested GPU timestamps"
assert capture["gpu_timestamps_requested"] == "true"
assert capture["gpu_timestamp_scope"] == "main_graphics_command_buffer"
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
    # A full window of overlapping stages indicates a broken timing boundary.
    # Permit isolated startup/OS scheduling anomalies without hiding a systemic error.
    if frames >= 5:
        assert int(accounting["top_level"]) < frames // 2, accounting
        assert float(accounting["top_level_excess_ms"]) < float(window["duration_s"]) * 1000 * .05, accounting
    for label in ("stage_avg_ms", "stage_p95_ms", "tick_stage_p95_ms", "accounting_overlap_frames", "jvm"):
        assert any(f"[VulkanModPerf] {label} " in line for line in block), label

print(f"Profiler output contract passed: {len(windows)} completed startup windows")
