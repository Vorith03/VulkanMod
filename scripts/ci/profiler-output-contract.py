#!/usr/bin/env python3
"""Check actual Vulkan startup captures for summaries, JFR, and raw command evidence."""

import json
import pathlib
import struct
import subprocess
import sys
import uuid


def fields(line):
    return dict(token.split("=", 1) for token in line.split() if "=" in token)


def jfr_events(jfr, event_type):
    payload = subprocess.run(
        ["jfr", "print", "--json", "--events", event_type, str(jfr)],
        check=True,
        capture_output=True,
        text=True,
    ).stdout
    document = json.loads(payload)
    events = document.get("recording", {}).get("events", [])
    return [event.get("values", {}) for event in events if event.get("type") == event_type]


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

frame_events = jfr_events(jfr, "net.vulkanmod.RunTickFrame")
assert frame_events, "JFR contains no recorded frame events"
frame_sequences = [int(event["sequence"]) for event in frame_events]
assert all(sequence > 0 for sequence in frame_sequences), "frame sequence must be positive"
assert len(frame_sequences) == len(set(frame_sequences)), "duplicate JFR frame sequence"
frame_sequence_set = set(frame_sequences)

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

submission_events = jfr_events(jfr, "net.vulkanmod.VulkanSubmission")
successful_submissions = {
    int(event["sequence"]): event
    for event in submission_events
    if int(event.get("result", -1)) == 0 and int(event.get("commandBuffer", 0)) != 0
}
assert successful_submissions, "JFR contains no successful Vulkan submissions"
assert len(successful_submissions) == sum(
    1 for event in submission_events
    if int(event.get("result", -1)) == 0 and int(event.get("commandBuffer", 0)) != 0
), "duplicate JFR Vulkan submission sequence"

# Universal Vulkan boundaries carry the exact runTick envelope in which they were
# observed. Zero is deliberately reserved for work outside any Minecraft frame.
def verify_frame_links(event_type, events):
    linked = 0
    for event in events:
        assert "frameSequence" in event, f"{event_type} missing frameSequence field"
        sequence = int(event["frameSequence"])
        assert sequence >= 0, (event_type, sequence)
        if sequence != 0:
            assert sequence in frame_sequence_set, f"{event_type} references unknown frame {sequence}"
            linked += 1
    return linked

linked_submissions = verify_frame_links("VulkanSubmission", submission_events)
assert linked_submissions > 0, "no Vulkan submission is explicitly correlated to a Minecraft frame"
verify_frame_links("VulkanFenceWait", jfr_events(jfr, "net.vulkanmod.VulkanFenceWait"))
verify_frame_links("VulkanApi", jfr_events(jfr, "net.vulkanmod.VulkanApi"))

gpu_events = jfr_events(jfr, "net.vulkanmod.GpuCommandBuffer")
assert gpu_events, "JFR contains no completed GPU command-buffer timestamps"

# The fixed-width command sidecar is deliberately independent of JFR's event
# machinery. Prove a real launch creates a structurally complete stream and that
# command-buffer recordings join exactly to the canonical JFR submission sequence.
command_trace = jfr.with_suffix(".vkcmd")
assert command_trace.exists(), f"missing Vulkan command trace next to {jfr.name}"
raw = command_trace.read_bytes()
assert len(raw) >= 64 + 64, "command trace contains no records"
assert raw[:8] == b"VMVKCMD1", "bad command trace magic"
version, record_bytes = struct.unpack_from("<II", raw, 8)
assert version == 1, version
assert record_bytes == 64, record_bytes
assert (len(raw) - 64) % record_bytes == 0, "partial command trace record"

records = []
for offset in range(64, len(raw), record_bytes):
    opcode, ordinal, recording_id, a, b, c, d, e, f = struct.unpack_from("<IIQ6Q", raw, offset)
    records.append((opcode, ordinal, recording_id, a, b, c, d, e, f))
opcodes = [record[0] for record in records]
for opcode, label in ((1, "begin"), (2, "end"), (3, "submit"), (255, "capture footer")):
    assert opcode in opcodes, f"command trace missing {label} record"
assert opcodes[-1] == 255, "command trace footer must be last"

footer = records[-1]
# Footer payload a/b/c = accepted records / dropped records / dropped chunks.
accepted, dropped_records, dropped_chunks = footer[3], footer[4], footer[5]
assert accepted > 0
assert accepted == len(records) - 1, (accepted, len(records) - 1)
assert dropped_records == 0, f"startup command trace overflowed: {dropped_records} records"
assert dropped_chunks == 0, f"startup command trace overflowed: {dropped_chunks} chunks"

by_recording = {}
for record in records[:-1]:
    by_recording.setdefault(record[2], []).append(record)

trace_submissions = {}
for recording_id, recording_records in by_recording.items():
    if recording_id == 0:
        continue
    ordinals = [record[1] for record in recording_records]
    assert ordinals == sorted(ordinals), f"out-of-order trace ordinals for recording {recording_id}"
    assert len(ordinals) == len(set(ordinals)), f"duplicate trace ordinal for recording {recording_id}"
    assert recording_records[0][0] == 1 and recording_records[0][1] == 0, \
        f"recording {recording_id} does not start with BEGIN ordinal zero"
    submit_records = [record for record in recording_records if record[0] == 3]
    assert len(submit_records) <= 1, f"recording {recording_id} has multiple SUBMIT records"
    if submit_records:
        submit = submit_records[0]
        assert submit is recording_records[-1], f"recording {recording_id} has commands after SUBMIT"
        assert any(record[0] == 2 for record in recording_records[:-1]), \
            f"submitted recording {recording_id} has no END"
        sequence = int(submit[3])
        assert sequence not in trace_submissions, f"duplicate trace submission sequence {sequence}"
        trace_submissions[sequence] = submit

assert trace_submissions, "command trace contains no submission joins"
missing_jfr = sorted(set(trace_submissions) - set(successful_submissions))
assert not missing_jfr, f"command trace submissions missing from JFR: {missing_jfr[:8]}"

for sequence, submit in trace_submissions.items():
    event = successful_submissions[sequence]
    # SUBMIT payload: a=sequence, b=queue, c=fence, d=commandBuffer.
    assert int(event["queue"]) == submit[4], (sequence, "queue")
    assert int(event["fence"]) == submit[5], (sequence, "fence")
    assert int(event["commandBuffer"]) == submit[6], (sequence, "commandBuffer")

# GPU timestamp events are allowed to be a subset (a device may expose fewer
# timestamp-capable command buffers), but every emitted GPU event must close the
# exact same frame -> JFR submission -> vkcmd -> GPU identity chain.
frame_linked_gpu = 0
for event in gpu_events:
    sequence = int(event["submissionSequence"])
    assert sequence in successful_submissions, f"GPU event references unknown JFR submission {sequence}"
    assert sequence in trace_submissions, f"GPU event references unknown vkcmd submission {sequence}"
    submit = trace_submissions[sequence]
    submission = successful_submissions[sequence]
    frame_sequence = int(submission["frameSequence"])
    if frame_sequence != 0:
        assert frame_sequence in frame_sequence_set
        frame_linked_gpu += 1
    assert int(event["commandBuffer"]) == submit[6] == int(submission["commandBuffer"])
    assert int(event["queue"]) == submit[4] == int(submission["queue"])
    assert int(event["fence"]) == submit[5] == int(submission["fence"])
    assert int(event["gpuNanos"]) >= 0
    assert int(event["elapsedTicks"]) >= 0
    assert int(event["timestampValidBits"]) > 0
assert frame_linked_gpu > 0, "no GPU timestamp closes the CPU-frame-to-GPU correlation chain"

command_metadata = pathlib.Path(str(command_trace) + ".meta.jsonl")
assert command_metadata.exists(), "missing Vulkan command object metadata sidecar"

print(
    f"Profiler output contract passed: {len(windows)} completed startup windows; "
    f"raw JFR={jfr.name} ({jfr.stat().st_size} bytes); "
    f"vkcmd={command_trace.name} ({len(records)} records); "
    f"submission_joins={len(trace_submissions)}; gpu_joins={len(gpu_events)}; "
    f"frame_submission_joins={linked_submissions}; frame_gpu_joins={frame_linked_gpu}"
)
