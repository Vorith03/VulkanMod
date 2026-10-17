# RX #1020 capture and bounded terrain backlog repair

## Live base and capture

Base `4ac8b84bf2031a3a91385fb80e093e8add92f50a` is documentation-only above
`e0807c626bfa27416ebadc53eddff519f9ac29ef`, the source-grouping repair. The delta
from the previous investigation `504617c` contains that repair and its evidence.
Full CI #1020 (`39038204099`, job `117412382928`) was rechecked green, including
native pixels/synchronization, Forge packaging and compatibility; private-pack
fixtures skipped. Open PR #12 is the separate historical flight-recorder work.

Owner capture UUID `e12d7c14-9f90-4c41-b213-96606e05a272` identifies
`0.3.2-forge.2-build.1020-ge0807c62`, RX 6900 XT/RADV, Java 17.0.15, RD32,
simulation distance 12, fixed 2552x1374 framebuffer and spectator camera
(0,192,0), yaw -90, pitch 30. Both texture pilots and float64 were active.
Settle time was 82.288 s; capture completed 180.016 s / 5,014 frames (~27.85 FPS),
with normal save/exit. No repeat of this unchanged single capture is needed.
The reported cap 260 consumed zero frame-limit time and does not explain this load.

Raw attachments remain outside public Git. SHA-256:

- Benchmark log: `a6c06b159c628b079944512ae2a63930e8adab198201a2459e6c164f6dae09f0`
- JFR: `f64686395c1133c4b4f8b729588ae02d98c9c925a08c071e9bbb7af7b5faedb0`
- latest(10).log: `f8663016cda2e663bc01ee7360f19e5b6c1b71f292707078059a9a395b00c36a`
- debug(7).log: `17b0725bc5cf9481be2fa783084acfdc7117a4f9857026eb4b2bf9869d9d9e2e`

## What the previous repair achieved

These are two historical single captures, not restored-world matched pairs.
They establish activation/operation counts, not a proven net speedup.

| Scope | #1019 | #1020 |
| --- | ---: | ---: |
| Client tick mean, ms/tick | 30.166 | 29.679 |
| Texture mean, ms/tick | 17.787 | 17.556 |
| Queued copy commands | 1,435,220 | 636,736 |
| Queued regions | 21,250,172 | 21,638,388 |
| Source-switch drains | 1,266,289 | 0 |
| Inclusive copy-flush CPU, ms/tick | 8.154 | 8.176 |
| Texture-upload GPU mean, ms/batch | 4.219 | 3.066 |
| Standard GPU interpolation coverage | 3.79% | 4.17% |

#1020 emits 247,688 ordering epochs, distinct from its command count. Source
fragmentation is removed and command count falls ~55.6%, but CPU texture cost
is essentially unchanged in this screening result. Do not call it a measured
CPU performance improvement. Per-tick upload scope drops from 8.869 to 7.150 ms,
while its residual rises from 8.918 to 10.406 ms; these nested scopes include
batching boundaries and must not be interpreted as exclusive interpolation time.
#1020 has 115,607 region-limit, 118,337 GPU-interpolation, 10,308 direct-upload
and 3,436 outer-end drains. Enlarging regions or compute batching is not selected
by count alone. Fixed-budget admission and permanent disable still leave
2,751,373 standard interpolation attempts on disabled residency; CPU interpolation
remains a substantial unresolved cost. No budget/default/shader changes here.

Main graphics GPU mean is 3.047 ms/frame; separate texture batches average
3.066 ms, with different denominators. Weighted CPU frame mean is 35.851 ms;
windows wholly after 120 s average 32.182 ms. Terrain setup averages 4.221 ms/frame
and 4.095 ms/frame after 120 s. These nested times cannot be summed with their
parents. Existing early hitches, ~5.24 MiB particle allocation/tick and integrated
server lag remain separate costs. Logs do not establish visual correctness.

Independent Java 17 RecordingFile decode finds 41,306 raw events: 23,572 execution,
17,554 native samples and 180 server-tick events. The report excludes three samples
outside capture (23,571 execution + 17,552 native). Inclusive raw render-thread
stack counts (denominator 9,773, overlapping) include TextureManager 2,354,
interpolation 2,204, setupRenderer 1,966, updateRenderChunks 1,896, ParticleEngine
render 2,071 and block-entity render scan 1,072. These are statistical samples,
not method milliseconds. Native waits/background threads are not CPU compute time.

## Concrete terrain cause and repair

In all baseline-valid windows: **4,997 traversals for 4,997 frames**, zero cached
schedules, 1,604 pending dirty notices and a stationary pose/visible count.
`cachedGraphCoversPendingDirty` rejects ANY backlog above 512 before checking
its members. A full BFS encounters unready/capacity-limited sections and retains
them as dirty; count-based rejection therefore repeats even with unchanged
connectivity. The count condition is demonstrably true in this capture. Other
invalidation causes may coexist; the capture does not prove each pending section
is neighbor-blocked or that every traversal will disappear after repair.

Repair removes the arbitrary count test, retaining exact frame/area membership
checks, camera/topology invalidation, ownership recheck at consumption and actual
build capacity. The linear preflight visits existing resident section identities;
it does not allocate another graph or increase worker queue or memory budgets.
Missing/stale membership still selects the full BFS. Known large backlogs alone
do not imply changed connectivity.

**A cap-only edit is unsafe:** executing the production scheduler with 1,604
unready known members stalled for 30 s. A bounded 3 s reproduction/thread dump
found it RUNNABLE in `ConcurrentHashMap.KeySetView.add`, called by the unready
remove/reinsert branch. A weakly consistent iterator can revisit those entries.
The repair keeps unready notices in place and limits attempts to the initial set
size, in addition to the successful-submission capacity limit. Failed admission
still retains work; removal remains before actual submission so re-dirty can
survive. Stale-notice cleanup rechecks dirtiness after removal to preserve a
racing new notification. No work is dropped to hide the backlog.

## Validation and next action

The expanded production-method Java 17 scheduler contract fails on original
HEAD with `large known backlog forced a full traversal`. The cap-only intermediate
fails by timeout. The repaired version passes: 100 frames of the 1,604-entry
unready backlog, stale graph/area rejection above the old ceiling, rejected-task
attempt bound, and exact-once drain of all 1,604 after neighbors become ready in
batches of at most seven. Existing backpressure, neighbor retry, redirty,
initial-population and ring-ownership regressions pass.

All 22 offline contracts passed locally; the final stale-notice refinement and deterministic redirty-race regression were
rechecked with the focused terrain contract. `git diff --check` passed. The local
Java 17 installation exposes the compiler module but has no javac launcher;
a temporary launcher invokes `java -m jdk.compiler/com.sun.tools.javac.Main`.
Local Forge validation cannot configure because the Gradle 8.1.1 distribution
download fails with `Network is unreachable`.

Full public/native CI **#1021** passed at implementation
`8ff928dd02deb6ad2d9098210a82fa23bfdbf5a6`: run `39070465187`, job
`117507019602`. All 22 contracts, Forge production packaging, pinned animation/
portal anchors, both startup modes, indirect, post/depth, screenshot pixel/sync,
Create Chronicles, Crash Assistant and artifact uploads passed. Private packs
were skipped. The large-backlog regression executes the production Java scheduler
methods with CPU fixture dependencies; the native suite is broader regression
coverage, not proof that the owner-world backlog is exercised on Lavapipe.
Qualified JAR: `VulkanMod_Forge_1.20.1-0.3.2-forge.2-build.1021-g8ff928dd-all.jar`,
artifact `12115062566`. Disposition: **qualified but unmeasured**.

The remaining narrow changed-executable RX question is whether
`graph_traversals_delta` falls below frames at a stationary pose, unready notices
remain retained, and `cached_graph_schedules_delta` admits eligible maintenance
without rendering loss. Compare against this #1020 capture with identical settings
and sampling; matched restored-world pairs are required for a formal net benefit.
Textures/interpolation and particle/server load remain open regardless of the
terrain result. Preserve reload/re-entry deferral and private-pack limitations.
