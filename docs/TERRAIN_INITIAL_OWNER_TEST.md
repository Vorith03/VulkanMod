# Initial terrain owner test — final artifact qualification pending

Do not run an unqualified candidate. The final executable/artifact/hash will be
filled in after the empty-input-page repair passes full integration and its actual bytes are checked.
The candidate preserves MC1.20.1 /Forge47.3.0 /Java17 and default-OFF experiments.

## Smallest next hardware check

Use the final JAR and [OWNER_TERRAIN_INITIAL.jvmargs](OWNER_TERRAIN_INITIAL.jvmargs).
Replace existing VulkanMod feature/profiler properties to avoid duplicate overrides;
keep unrelated memory/GC options. Keep the same302-mod setup, VulkanMod config,
config/fml.toml earlyWindowControl=false, framebuffer2552x1374,D32/S12,VSyncOFF,
cap260. This diagnostic explicitly enables the same19 integrated GPU features and
bounded tracing. It is not a net-speed benchmark: tracing adds work.

Start a fresh game process and enter the existing, saved VulkanMod_Benchmark view:
player0/192/0,yaw-90,pitch30, same saved game mode. No automatic teleport runs.
Keep camera/settings fixed for65s, then exit normally. Do not reload resources,
move, teleport, change distance/visibility or preload candidate geometry. Keep
natural OS/driver caches; note trial order and whether the game process was fresh.
Re-entry and newly generated terrain are different strata, not interchangeable
repeats of this initial existing-save trial.

Return NEW latest.log and logs/vulkanmod-performance.log. For actual appearance,
include an entry-to-converged-view clip if available, or observations of first
usable frame, nearby terrain at2–3s, intended view at5s, remaining holes, and worst
initial hitches. No new stationary post-convergence benchmark or unchanged old
logs are needed. The trace runs from first joined-world rendering, records the
entry eye/FOV transition separately, and performs a late production census at55s.
The65s wait lets existing frame fences confirm the last capture before shutdown.
If the view is still changing/populating after the census, say so; that run cannot
be called a converged full-view reference. Do not treat50/90/99 as complete.

The owner-specific questions are whether consumed inputs now turn over under
D32 pressure, whether output reservations/recovery or renderer handoff dominates,
and whether real modpack pixels remain correct. Wrong/stale terrain, missing CPU
exceptions, worse hitches or rising output failures count against the candidate.
The agent will extract generations, selected sources, producer ownership, queue /
publication gaps and fixed-reference command coverage. The owner does not need to
construct reference manifests or enumerate sections manually. Logs stay private.

## Matched visual/performance comparison after correctness

The separately verified pre-staging baseline is full1092, executable
272b7f81f09a05e4796e008d64a33c73a5270505,
[JAR artifact12400315181](https://github.com/Vorith03/VulkanMod/actions/runs/39716738464/artifacts/12400315181).
Actual11,103,766byte JAR SHA256:
`2eedcf35295de61e4ecac2fad97d19b84c5813f2734838d7a8db884079fc6e88`.
This retains the qualified texture/particle adoption repairs.

Use [OWNER_TERRAIN_COMPARE.jvmargs](OWNER_TERRAIN_COMPARE.jvmargs) for BOTH
executables: same19 features/profiling, tracing explicitly FALSE in both. An old
JAR silently ignoring a true tracing property would NOT have comparable overhead.
Only the candidate JAR changes. Keep identical modpack/config/framebuffer/FPS /
distances and restore the SAME offline save snapshot before each fresh-process
trial. Copying a save is not preloading geometry. Do not manipulate privileged
caches; record natural cache state and run order. An existing-save pair and a
new-generation pair need separate immutable starting snapshots and results.

Capture world entry through the fixed view using the same recording method in
both trials. Retain first usable frame,2s/3s/5s views, eventual reference view and
unresolved holes, plus fresh performance/latest logs for each executable. One A/B
pair is a screening comparison; repeat in reversed order when variance changes
the conclusion. Initial entry, re-entry and warm caches must remain separate.
The agent will check frame pacing/worst stalls, sampled memory/retained native
budgets, adoption/recovery and visible timing. Sampled heap is not an exact peak.
No FPS-only result or post-convergence settling notice establishes appearance.

The diagnostic/correctness capture and this performance comparison are different
controls. The first minimal capture can identify a correctness/pressure blocker
before spending time on repeated A/B trials. Neither script command percentages
nor queue/publish counts alone establish2–3s/5s on-screen success. True all-required
input availability remains unknown for unsupported arbitrary Forge callbacks;
REGION_SNAPSHOT_READY is explicitly a later renderer milestone. Future arrival of
light packets cannot be proved by an empty current light-work queue. These limits
remain visible in every report, and the full performance/release mission remains
open until the missing evidence is actually obtained.
