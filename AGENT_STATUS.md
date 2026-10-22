# VulkanMod Forge1.20.1 — GPU adoption repair checkpoint

## Current authoritative state — October 21, 2026

Executable candidate **272b7f81f09a05e4796e008d64a33c73a5270505** on
forge-1.20.1. Full **#1092 /39716738464 IN PROGRESS**. #1091
/39716188014 on c9b6ef70 was superseded/cancelled. The final successor is not internally qualified yet.
#1090 /39715651273 failed fixture compilation before native testing; corrected
with pinned LWJGL writable memory views in c9b6ef70. Do not request an owner
benchmark until the final candidate's required full gates pass.

Last verified FULL qualified executable is **550a510c6f2ed833eb80e8e03a881ab708faf270**,
#1089 /39713898920 SUCCESS. JAR artifact12398816155, logs12398058418.
The artifact ZIP digest is fc457485eef17f1f4c296c253e1d8c5c44ed5b72decc66ec98a18c6e77774adc;
this is NOT the extracted JAR SHA256. Do not promote that older JAR as the final
texture+particle repair. Downloaded #1089 logs: particle647 GPU quads/131 batches,
one nonzero-window batch, deliberate descriptor rejection2, original CPU recovery;
texture shared/multi/native tick oracles passed. No top-level Vulkan validation
error/sync-hazard matches. Private fixtures skipped.

## Surviving implementation and corrected cause

Interrupted particle descriptor windows survived in550a510c; reviewed/preserved.
Existing shared buffer, absolute draw/barriers, relative shader addressing, signed
Java end bounds, 64 allocated+pending descriptor quota and frame retirement remain.

**Shared defect:** Vulkan uint32 maxComputeWorkGroupCount(X) was compared as signed
Java int in texture and particle guards. RADV source advertises0xffffffff, exposed
as -1, rejecting even one group. Texture maxStorageBufferRange signed comparisons
also disabled shared scratch. The earlier inference that <=1024 groups excludes
the workgroup guard was wrong. Installed owner limits still require confirmation;
new unsigned-limit/rejection summary discriminates them. Exact source and safety
review: docs/GPU_ADOPTION_REPAIRS_2026-10-21.md.

b767223c widens affected limits with Integer.toUnsignedLong, preflights standalone
texture descriptor ranges, adds bounded lifetime rejection reasons. c9b6ef70 fixes
native test API;272b7f81 proves a forced nonzero-window GPU result in the actual
BufferUploader frame buffer and reads the exact drawn slice against CPU geometry.
Existing native texture oracle now exercises high-bit storage/group limits and
actual transformed-ticker CPU cancellation with exact atlas mip pixels. No shader,
allocator, queue, default flag, terrain implementation or profiler PR#12 change.

Local all24 Java17/offline contracts PASS; pinned-LWJGL native-struct probe PASS
including pre-fix signed rejection at0x80000000 and0xffffffff. Focused Java17
compilation of all5 changed renderer/profiler classes PASS on reduced mapped
Forge/pinned-LWJGL classpath (annotation warnings). Full local Gradle dependency
bootstrap timed out at240s; local native runtime unavailable, not passed.

## Owner evidence and remaining gates

#1088 owner integrated19 UUID221fd7a2:71.34fps at2552x1374/D32/S12; partial
20c49c53:58.60fps on same JAR. Single trials, no true CPU allfalse control, no
causal/performance/visual-equivalence claim. Integrated textures0/2879091,
particles0/126249312; terrain727 GPU-published/CPU-bypassed sections, live
REPLACE/APPEND,19 CPU recoveries. Preserve working terrain and recovery boundaries.
Private raw logs remain outside Git. Owner report/ledger contain full comparison.

Next: inspect #1091 and FINAL #1092 results/logs, repair actionable failures, and
continue until one coherent full qualification is complete. Then download final
JAR/log artifacts, verify JAR SHA256, native output/cancellation/production-window
marker and zero synchronization hazards, plus integrated terrain/portal/public
modpack gates. Record private skipped gates separately. Refresh these documents
and rolling prompt before finishing. Do not treat pending/quick/older CI as final.

After internal success, deliver ONE targeted owner package from
GPU_ADOPTION_REPAIRS_2026-10-21.md and OWNER_GPU_ADOPTION_{ON,OFF}.jvmargs:
functional texture/smoke/reload/reentry observations, then matched all19 true vs
false on SAME new JAR, framebuffer2552x1374 and existing benchmark scene. Owner
visual/adoption, measured profitability and release/default-enable gates remain
external. No repeated unchanged owner logs or broad terrain archaeology.
