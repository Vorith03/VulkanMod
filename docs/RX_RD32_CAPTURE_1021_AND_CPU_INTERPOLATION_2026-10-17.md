# RX #1021 terrain adoption and CPU interpolation fallback

## State and provenance

Analysis base `646bb52052cd97b4a4ea42ffa9f52896ced41925` is documentation-only
above tested executable `8ff928dd02deb6ad2d9098210a82fa23bfdbf5a6` (#1021).
The live native/Forge run `39070465187`, job `117507019602`, was rechecked green;
private packs skipped. Open PR #12 remains the separate, historical profiler
branch. The delta since investigation `504617c` consists of source grouping,
the terrain backlog repair and their documentation; neither investigation was
restarted. The requested owner #1021 capture is received and must not be
requested again unchanged.

UUID `f4741fc5-56ad-4d93-a5c5-46c776ff03d4` identifies #1021 / `8ff928dd`,
Java 17.0.15, RX 6900 XT/RADV, RD32, simulation distance 12, 2552x1374,
spectator at (0,192,0), yaw -90 / pitch 30, both texture pilots and float64
active. Initial settling took 82.191 s. Capture completed 180.030 s / 6,466
frames (~35.92 FPS); normal save/exit followed. Large initial hitches and
integrated-server lag remain. Main graphics GPU mean is 3.164 ms/frame; the
separate texture batch span is 3.097 ms/batch, not additive or exclusive shader
time. The reported 260 cap consumes zero frame-limit time.

Original attachments stay outside public Git. SHA-256:

- benchmark: `a1657b1081d97179fba577a89ff57c68095a4c03ba3bed1fd6d60762ca1fd39e`
- raw JFR: `00b9066f8c64e083a5db8545e5394f406ae31a176728fe0e0b1bb7d00370c500`
- latest(20261017-214227).log: `e39d435aa7ccb26b6bb106841bb6da92613d758028f5374c626dcf660756f878`
- debug(8).log: `3d6cebc002509e995b54e6ce34ab9f9f28514213cff622d3fdc331b470100b09`

## Terrain: changed path is adopted

These are historical single captures, not matched restored-world A/B pairs.

| Scope | #1020 | #1021 |
| --- | ---: | ---: |
| Frames in valid terrain-delta windows | 4,997 | 6,457 |
| Full graph traversals in those windows | 4,997 | 439 |
| Cached rebuild admissions in those windows | 0 | 785 |
| Pending dirty notices | 1,604 | 1,604 |
| Terrain setup mean, ms/frame | 4.221 | 0.907 |
| Terrain setup after 120 s, ms/frame | 4.095 | 0.863 |
| CPU frame mean (weighted windows), ms | 35.851 | 27.794 |
| CPU frame mean after 120 s, ms | 32.182 | 24.610 |
| Texture mean, ms/client tick | 17.556 | 17.950 |

The backlog no longer forces one BFS per stationary frame. Work is retained
and eligible rebuilds are admitted. Residual topology invalidations still
produce traversals. This establishes adoption and favorable screening evidence,
not a proven matched net benefit or visual/lifecycle correctness.

## Remaining measured texture cost

Texture work remains 17.950 ms/tick; inclusive copy-flush CPU is 8.231 ms/tick
and sprite-upload scope 7.911 ms/tick. Those overlapping scopes must not be
summed or subtracted as exclusive interpolation time. There are 2,875,168
standard interpolation updates, only 83,951 GPU (2.92%); 2,791,217 use CPU,
including 2,787,367 disabled-residency fallbacks. Lifetime residency is near
128 MiB with 492 admissions, 1,233 capacity rejections, no source/staging
rejections or invalidations. First-admission/permanent-disable under a full
fixed budget is still the coverage limitation. This capture does not provide
per-sprite benefit/size data sufficient to select a new eviction/admission
policy. No budget or default GPU setting is changed.

Independent Java 17 RecordingFile decoding reconciles all 41,147 raw events:
23,450 execution, 17,517 native-boundary and 180 server-tick events. Four samples
are outside the admitted capture (report: 23,449 + 17,514). Raw render-thread
execution denominator is 9,203; overlapping inclusive stacks include vanilla
InterpolationData 2,213, ParticleEngine.render 2,451, block-entity rendering
1,419 and setupRenderer 582. Texture groupSources has only 5 and overlapsAny
21 samples. The report's top individual texture stack has 1,037 samples in
NativeImage.setPixelRGBA/Unsafe.putInt; source reads/checks also rank highly.
These are statistical samples, not method milliseconds. Native boundaries are
not CPU compute time. The record has 567 truncated samples and 31 observer
samples; no stack-cap overflow. Full caller chains, rather than the report's
single owner category, determine inclusive attribution.

## Bounded implementation: opt-in bulk CPU fallback

`-Dvulkanmod.experimentalCpuSpriteInterpolation=true` replaces only the pixel
loop after the existing GPU attempt declines. It leaves the source pool,
residency/admission, GPU-first route and original SpriteContents upload and
transfer machinery intact. Exact-class/absent-Forge-metadata qualification and
render-thread execution are required; custom/unqualified/invalid input uses
vanilla. This is a CPU fallback repair, not broader GPU offload or a solution
to the admission policy.

`CpuSpriteInterpolation` validates every positive mip, RGBA/alive storage,
source/destination extents, resolved frame coordinates and nonaliasing before
writing anything. A tight row loop reads two native integers and writes one,
with frame coordinate/row stride checks outside the pixel loop. Java 17 double
arithmetic, truncation, current-frame alpha, odd base-offset shifts and existing
mip bytes remain exact. Output mutation tracking is marked once per mip; its
internal generation remains an invalidation token, not a per-pixel write count.
No per-frame heap allocation or CPU/GPU memory-budget increase is introduced.
The benchmark records `standard_interpolation_cpu_bulk_updates`, a subset of
`standard_interpolation_cpu_updates`, to distinguish adoption from intent.

The falsifiable hypothesis is reduced CPU pixel-loop overhead without changed
pixels/uploads/clock. The path remains off by default until native correctness
and matched RX times support adoption. Validation work is not performance proof.

## Validation and next gate

The Java 17 production-kernel contract compares native-memory output with an
independent checked scalar oracle over randomized clocks/pixels, odd/rectangular
sheets, all positive mips and zero-extent guards. Padding and sources remain
unchanged. Invalid later mip, closed/non-RGBA memory, alias, invalid/repeated
frame and clock/coordinate overflow reject before any write.

The native smoke runs the actual transformed ticker twice for interpolated
cases: GPU candidate and forced CPU bulk candidate, each against an unchanged
vanilla CPU reference plus the existing independent numeric/readback oracle.
It asserts bulk adoption and custom exclusion, all-mip atlas pixels, clocks,
hidden/refresh cadence and synchronization. GPU first refusal and subsequent
resident discrete copies remain covered. Local full Forge configuration is
blocked by the Gradle 8.1.1 distribution download (`Network is unreachable`).
All 23 offline contracts pass locally on Java 17; `git diff --check` passes.
Full/native CI results will be recorded at the qualified milestone.
An early local report-format fixture failed because the new field interrupted
its old adjacent-field assertion; ordering was preserved and coverage/reset
assertions were added before qualification. No production failure was hidden.

After native qualification: same-JAR pilot off/on comparison with all existing
#1021 flags/settings and the same JFR sampler; only the CPU bulk flag changes.
Read bulk-update denominator, whole texture/tick time and frame distributions,
plus animation observations. Use identically restored worlds and three matched
alternating pairs for a formal benefit claim. Do not repeat unchanged #1021,
combine unrelated renderer changes, or request deferred reload/re-entry tests.
Particle allocation/render cost (~5.32 MiB/tick and 4.237 ms/frame), the
block-entity scan (~2.290 ms/frame) and integrated-server load remain separate.


## Remote qualification blocked by approval review

The coherent implementation is committed locally as `909497a`; a follow-up
checkpoint bounds source-array validation to 32 entries. Automatic approval
review rejected its remote push because it judged an external repository write
not explicitly authorized in this session. No remote workaround was attempted.
Native/Forge CI therefore has **not run** for this candidate. Explicit push
authorization is required to advance qualification. Keep the new pilot off and
do not promote a test JAR before that gate. The local Gradle block remains;
CPU contracts do not replace native qualification.


The owner subsequently explicitly authorized publication and CI. The shell Git
transport cannot authenticate (`could not read Username`); the connected GitHub
API is used to publish the same local implementation tree on the verified base.
The prior authorization block is superseded. Full/native qualification remains
pending until actual CI results are inspected.


Additional local adversarial qualification rejects three deliberately corrupted
production kernels: next-frame alpha, integer interpolation weight and truncated
odd-tile mip offsets. Each fails the independent pixel oracle. Remote #1022,
run `39072794735`, job `117513870765`, has passed all 23 contracts and entered
Forge build/packaging; full/native qualification is still pending.


#1022's Forge compilation failed with `Mapping conflict for @Invoker target
upload`: MSpriteContents already owns an injection mapping for the same target
with a different reference form. The corrected candidate moves the invoker to a
dedicated registered `SpriteContentsUploadInvoker` interface, matching the
existing separate-accessor pattern. Pixel logic and upload targets are unchanged.
Native gates were not reached on #1022; preserve that negative result.
