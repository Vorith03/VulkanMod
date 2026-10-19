# GPU particle billboard ownership pilot

Opt-in `-Dvulkanmod.experimentalGpuParticleBillboards=true`, default off.
Implemented during the October 18 GPU-first continuation; the initial native/Forge
milestone passed #1036. The final storage-range safety correction `6a78cfb8`
passed full #1037 attempt 2 (39179150695), JAR 12156240273. Its first
attempt failed Forge FMLConfig startup with a null Boolean in an indirect smoke
fixture, not a particle assertion. Broad portal/mod/pixel/RX evidence remains open.

## Production boundary

The injected inherited `SingleQuadParticle.render` cancels its original numeric
expansion only for the **exact** `CampfireSmokeParticle` class, the normal
Tesselator builder, an unsorted aligned PARTICLE/QUADS stream, and a ready graphics-
queue compute pipeline in a recording frame. Subclasses, custom render overrides,
nonstandard consumers, default-color/index-only/sorted streams and unavailable
compute retain Java rendering. A separate exact probe class is admitted only by
the native screenshot test property. The Mixin itself is absent when disabled.
No ParticleEngine invocation redirection competes with the IP replacement loop.

CPU still advances simulation, lifetime, randomness, collisions and alpha; resolves
camera-relative interpolation and roll; executes getQuadSize, UV and getLightColor
callbacks exactly once in original order. Arbitrary Forge simulation is not
converted. The successful path omits the four Vector3f objects plus array, four
quaternion transforms/scales/adds, and all four chained vertex/UV/color/light
emissions. One 56-byte numeric record occupies the original 112-byte quad slot;
a per-rendered-buffer monotonic offset table identifies only qualified slots.
Other CPU quads stay byte-for-byte at their original indices. No regrouping or
extra translucent draw splitting occurs. This is compute expansion, not a new
particle renderer or an instanced shader replacement.

Immediately before the original draw, the existing shared frame-slot vertex buffer
receives its original stream plus offset table. One compute dispatch per original
particle draw expands qualified slots in place. Existing quad indices, shader,
textures, model-view, clipping, render target and draw order remain authoritative,
including the converted-legacy manual BufferUploader route. The normal graphics
shader consumes GPU-produced vertices. There is no CPU shadow calculation or
production readback/device-idle. CPU sorting first materializes pending records;
post-pack pre-record rejection similarly restores CPU vertices before upload.

## Resources and synchronization

Only pilot shared frame vertex buffers add STORAGE usage. Existing host-coherent,
fence-owned append uploads remain; total uploaded vertex bytes are **not reduced**.
The offset list costs 4 bytes per eligible quad, bounded at 65,536 quads/batch.
CPU tables grow geometrically and are owned by each RenderedBuffer; the reusable
native list scratch is 256 KiB. Descriptors are cached by VkBuffer generation in
a bounded 64-set pool; resize/free schedules retirement on existing frame fences.
No per-particle descriptor, scratch VkBuffer or dispatch is allocated.

Capacity and descriptor limits fail before commands, using CPU materialization.
Storage descriptors expose at most maxStorageBufferRange even when geometric
vertex-buffer growth allocates a larger unused tail. Every accessed byte is bounded
before reserve/bind; ordinary vertex draws can still use the larger allocation.
The Vulkan uint32 storage limit is widened unsigned, including 0xffffffff devices.
Host-write/compute-read-write and compute-write/vertex-fetch barriers are explicit.
The helper submits on the same graphics VkQueue before the later main frame, using
existing same-queue command-buffer retirement without CPU waits. An already active
texture upload batch rejects admission. New per-draw helper submission overhead
and GPU writes into host-coherent memory are material, **unmeasured** costs.
Retain the faster correct path until representative profitability is established.
Device loss/submission errors remain fatal renderer errors, never hidden as success.

Kernel follows JOML 1.10.5 Quaternionf.transform normalization for nonunit rotations,
preserves multiplication then addition and disables fusion through GLSL precise.
The optional joml.useMathFma mode falls back. Geometry oracle uses a 2e-6 position
and UV tolerance; packed color/light must match exactly. No general bit-exact
floating-point or rasterization equivalence is claimed yet.

Pinned source reviewed: Minecraft 1.20.1 `mahtomedi/minecraft` commit
`315a03ea8861509b550cc06a7ca6b28853d2459c`, SingleQuadParticle, BufferBuilder and
DefaultVertexFormat; Forge branch 1.20.1 ParticleEngine patch; JOML tag 1.10.5
Quaternionf.transform. These prevent accidentally importing 1.20.4 facing modes.

## Evidence and remaining gates

Native screenshot oracle calls the transformed inherited method and actual builder,
compares all 524 vertices in a mixed 87 GPU / 44 CPU quad stream against the original
CPU method, includes roll/nonunit quaternion, callback counts, sorted recovery,
post-pack unavailability, production BufferUploader activation, buffer growth and
generation retirement across three frames. It runs with synchronization validation.
A passing oracle does not establish broad modpack or portal adoption.

Existing benchmark output gains explicitly lifetime counters: inherited method
attempts, exact-class eligibility, original CPU calls, packed inputs, GPU-expanded
quads, CPU recovery quads, layout fallback calls and unavailable checks, capacity/
pool rejected batches, compute batches, host preparation
and submission time, upload bytes, descriptor allocation/live count. Custom methods
overriding inherited render are excluded from this denominator. There are no
per-particle clocks or new profiler subsystem. Separate GPU compute cost is not yet
measured. No owner RX time/FPS/p95/p99 improvement has been measured.

Before promotion: full native/Forge CI; production portal/Forge callback and mixed
translucency pixel observations; resource-reload/world re-entry and frame-slot
pressure; representative adoption and net CPU/dispatch/submission/GPU cost. Audit
other mod injections into inherited render before claiming their callbacks preserved.
The adapter intentionally does not cancel arbitrary custom subclasses. No request
for an unchanged baseline or a per-experiment owner benchmark is authorized by this
milestone. Integrate with the existing texture/terrain release gates first.

## Qualification recovery

Initial implementation `c225399f` triggered #1034 (39165258706), superseded during
build by counter/oracle-mapping correction `0b6ba02e`. Full #1035 (39165513611)
passed offline, build/package, startup/no-splash, native indirect and post/depth.
Its screenshot stage failed before geometry comparison: the constructor-time test
asked GameRenderer for the particle shader before asynchronous core reload had
populated it (`RenderType supplied no ShaderInstance`, ParticleBillboardSmokeTest).
This is a fixture initialization failure, not a qualified compute result.
`761947c1` creates/owns the real particle ShaderInstance explicitly for the oracle,
restores previous shader state and closes only after test idle. Full #1036
(39178618231) **passed**, executable `761947c1`, JAR artifact 12155143862, logs
12155024238. All public build/native/sync/Forge/compatibility gates passed; private
packs skipped. The mandatory particle marker confirms all 524 vertices per frame
across three frames, original uploader, exact callbacks and fallback/retirement.
Its six validation dispatches include direct oracle and production draws, so lifetime
GPU/eligible totals are not workload adoption. CI host preparation/submission was
18.330 ms for six batches, including cold descriptor/command work under software
Vulkan validation; do not treat this as RX steady-state cost or a speedup. Final
unsigned/bounded storage-range guard later passed full #1037 attempt 2 (same
executable as the failed environmental first attempt).
All 24 offline contracts also passed locally via the installed
jdk.compiler module, despite the missing javac launcher.
