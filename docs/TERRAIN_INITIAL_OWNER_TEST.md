# Initial terrain owner test — qualified build 1104

Source `543c9858c1e46f6bbd563e733d344b92510c0761`,
[full workflow1104](https://github.com/Vorith03/VulkanMod/actions/runs/39850115069)
SUCCESS including published-byte audit.
[JAR](https://github.com/Vorith03/VulkanMod/actions/runs/39850115069/artifacts/12457519722):
`VulkanMod_Forge_1.20.1-0.3.2-forge.2-build.1104-g543c9858-all.jar`,
11,135,570 bytes, SHA256
`4c430880956a16ae3ab94d676d47e34e6ba4aaba8af14df3b22c6b670ee98d9a`.
MC1.20.1/Forge47.3.0/Java17; experiments default OFF.

## Smallest next hardware check — two fresh65s processes

Restore the SAME offline backup of the existing saved VulkanMod_Benchmark before
each run. Keep the302-mod setup, config/fml.toml earlyWindowControl=false,
2552x1374,D32/S12,VSyncOFF,cap260, saved player0/192/0,yaw-90,pitch30 and game mode.
Keep unrelated RAM/GC settings. Replace previous VulkanMod properties to avoid
duplicate overrides. Preserve natural OS/driver caches and record trial order.

1. Untimed CPU correctness reference: same new JAR with
   [OWNER_TERRAIN_REFERENCE.jvmargs](OWNER_TERRAIN_REFERENCE.jvmargs).
   Ten terrain/selection experiments OFF, nine texture/particle features ON,
   tracing ON. Hold the saved view65s, then exit normally. Confirm late terrain
   is correct and converged; if still populating around55s, say so.
2. Candidate diagnostic: restore SAME offline snapshot, restart with
   [OWNER_TERRAIN_INITIAL.jvmargs](OWNER_TERRAIN_INITIAL.jvmargs).
   All19 integrated features and tracing ON. Hold the SAME view65s, exit normally.

Do not move, teleport, reload, change visibility/settings or preload geometry.
Return NEW latest.log and logs/vulkanmod-performance.log from EACH run, labelled
reference/candidate. Include an entry-to-converged clip when available, or first
usable-frame observations, terrain at2–3s/5s, holes and hitches. Logs stay private.
No unchanged old logs or new stationary post-convergence benchmark are needed.

The agent constructs the frozen nonempty section/layer reference; the owner need
not enumerate sections. Empty sections are not progress; unavailable members stay
holes; APPEND requires BOTH geometry halves. Nearby uses64-block horizontal
section-center radius, intended view the independent converged CPU section/layer
set under the same camera and visibility. Portal child views are separate.
50/90/99 are milestones, never complete; report unresolved holes even at99%.
World entry is first main-world rendering; eye/FOV convergence is recorded without
resetting that clock. Census55s/capture60s/normal exit65s allow existing fences.
A nonconverged CPU reference cannot prove a complete denominator.

These are correctness/pressure controls, NOT a matched speed comparison: terrain
feature configuration differs, tracing adds work, and cache order is uncontrolled.
Check turnover, actual GPU consumption/CPU displacement, CPU exceptions, rejection,
recovery, publication delay and pacing. Last-fence page retirement can cause later
isolated edits to allocate again; record hitches. Input charges exclude output
geometry and driver heap; sampled heap is not an exact peak.

## Matched appearance/performance comparison after correctness

Baseline: source272b7f81f09a05e4796e008d64a33c73a5270505,
[JAR1092](https://github.com/Vorith03/VulkanMod/actions/runs/39716738464/artifacts/12400315181),
11,103,766 bytes, actual SHA256
`2eedcf35295de61e4ecac2fad97d19b84c5813f2734838d7a8db884079fc6e88`.
It preserves texture/particle repairs.

Use [OWNER_TERRAIN_COMPARE.jvmargs](OWNER_TERRAIN_COMPARE.jvmargs) for BOTH baseline
and candidate: same19 features/profiling, tracing FALSE in both. Only JAR changes.
Restore identical offline save snapshots and preserve all owner settings. Record
cache conditions/order; no privileged cache manipulation. Initial entry, re-entry,
warm-cache and newly generated terrain are separate strata. New generation needs
its own immutable starting snapshot. Capture entry-to-converged views and fresh
logs in both; retain first usable frame,2s/3s/5s,50/90/99 coverage and remaining
holes. Reverse order/repeat when variance affects the conclusion. Compare pacing,
worst stalls, retained/observed memory, adoption, fallback and recovery.

True all-required-input arrival and first usable PRESENTED frame are unknown from
command/fence traces. REGION_SNAPSHOT_READY is a later renderer milestone; current
empty light queues cannot prove future packets or arbitrary Forge callback inputs.
On-screen evidence is required for2–3s/5s. Full appearance/performance/release
qualification remains OPEN until the hardware evidence is actually obtained.
