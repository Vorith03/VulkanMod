# RD32 benchmark convergence correction

## Owner-machine evidence

Build #1003 (`f45aa7ceb8512ea3bfe90a8f1632cfe5d9a4c49f`) on the owner machine
started warming at 2026-10-07 01:52:12.465 and aborted at 02:07:12.476.
Max settle was 900 seconds; the user observed at most about four quiet seconds.
The Forge HUD fix was visible enough to report its progress.

The 165 five-second diagnostic samples show the distinction:

- At 60 s: 4818 non-empty sections, 36 low-priority tasks, 4 active workers,
  3 publication waiters, 16 publication results waiting. Initial filling was real.
- By about 80 s: 6559 non-empty sections and all work queues drained in that sample.
- Every sample thereafter retained 6559 non-empty sections through timeout.
- Between 80 and 150 s, every recorded sample had empty worker/queue counts,
  but recurring scheduled/published deltas still repeatedly reset quiet.
- From 600 to 900 s, 52 of 58 samples had no active/queued/waiting publication
  work. One-second scheduling deltas ranged 0–17; the cumulative scheduled span
  across those samples was 1829. Final one-second delta: 5 scheduled/5 published,
  with all instantaneous worker/queue counts zero.

These counters prove recurring renderer builds despite stable visible population;
they do not identify a responsible mod or prove every later task is maintenance.
The new classification measures that boundary directly rather than guessing a
rate threshold. This attempt contains no formal capture, texture route coverage,
O3/O4 speedup, or particle selection evidence.

Evidence SHA256:
- latest(7).log: `e7c6bace555617c231359087e70ec3a07426123464fb4de83970cf56a0ddb71b`
- debug(4).log: `099f52a5b39fd6d68cafb166d9571a1997d7bbfafd5dac0022deeeaefa3954f4`

## Corrected contract

`TerrainPopulationTracker` belongs to a TaskDispatcher. An initial build is one
whose RenderSection is UNCOMPILED at BuildTask construction. One ticket owns its
entire queued/building/publication lifetime, also for empty section results.
Ownership begins at async admission or synchronous execution; unqueued task
construction does not create outstanding work.
Synchronous builds use the same construction/worker path. Cancellation requests keep running/publishing work owned until retirement;
failed/cancelled worker exits or queue removal retire ownership; successful handoff keeps it until publication. The task's
existing cancellation flag still decides whether publication is accepted. Ticket
retirement is idempotent under cancellation/completion races. Dispatcher teardown
resets the epoch after workers stop and results are discarded; old completions
cannot decrement the new generation or count as new publications. Normal
non-automated operation allocates no tickets.

After minimum warmup, a stable sample requires positive unchanged non-empty
render-graph count, zero outstanding initial tickets at both samples, and unchanged
initial scheduled/published counts and epoch. Maintain that for the configured
10-second window. Ordinary already-compiled rebuilds/sorts execute normally and
remain measured. Worker/queue totals remain diagnostics, not an all-work veto.
No block updates, Forge callbacks, particles or world simulation are frozen.

Capture provenance states `settle_mode=initial_population_stable`, actual settle
time, initial epoch/counts/pending and `maintenance_rebuilds_included=true`. A
new initial task/publication or dispatcher epoch during capture aborts the formal
capture. The post-frame check allocates no snapshot. As with the prior gate,
readiness covers admitted renderer population and the render graph, not a promise
that every physical chunk in a radius has arrived from the server; late initial
admission is now explicitly detected during measurement.

## Validation and next action

Local Java 17 contract exercises queued initial ownership through publication,
duplicate retirement, cancellation, 100 completion/cancellation races, stale
epoch rejection, dispatcher reset, growing visible population, resumed population
capture rejection and a constant-6559 repeated-maintenance trace that reaches a
stable window. Existing HUD, attribution and chunk frame budget contracts pass.
Full local Gradle remains unavailable; new executable CI qualification is pending.

After CI is green, repeat the same O3/O4-only RD32 hardware capture using the
corrected JAR and maximum settle 300 seconds. Keep minimum 60, stability 10,
capture 180; do not change modpack/world/settings or enable unrelated terrain/
Flywheel options. If initial counters still increase after apparent visual
population, use their evidence rather than forcing a capture. No performance or
default-adoption claim follows from the correction itself.
