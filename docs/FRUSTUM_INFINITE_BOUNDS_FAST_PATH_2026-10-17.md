# Opt-in Forge frustum infinite-bounds equality fast rejection — 2026-10-17

## Measured basis
#1023 bulk CPU ON (run `252df7a2`, RX 6900 XT/RADV RD32) reports particle render 4.196 ms/frame and particle tick roughly 4.482 ms/client tick. Of 2,910 Render-thread JFR execution samples containing the particle-render call stack, 764 have `AABB.equals` as the sampled leaf (~26.3%), 601 are from a modded `ClientLevel.hasChunk` light query and 449 from `BufferBuilder`. The samples are statistical CPU observations, **not** an estimate of time saved.

The pinned Forge 1.20.x patch adds `AABB.equals(IForgeBlockEntity.INFINITE_EXTENT_AABB)` before the normal `Frustum.cubeInFrustum` call to preserve infinite block-entity render bounds. Ordinary particle boxes take that equality check every frame. We target only the comparison, not the culling outcome.

## Implementation and safety
With `-Dvulkanmod.experimentalParticleFrustumFastPath=true` the plugin admits `FrustumInfiniteBoundsFastPathMixin`, redirecting only Forge's specific `AABB.equals` invocation in `Frustum.isVisible(AABB)`. When the receiver is the **exact** vanilla `AABB` class and its first double coordinate differs from the sentinel's under `Double.compare`, equality is definitively false without the virtual six-coordinate comparison. Identity, matching first coordinates, subclasses, unusual boxes and all other cases continue through the original virtual `equals`. Forge's infinite sentinel still bypasses the frustum plane test; ordinary boxes still undergo the unmodified plane test. No culling radius, particle lifetime/visibility, lighting, buffers or geometry is modified.

The mixin is **not applied when the flag is absent**. CI startup enables the flag only for a native transform/loading check. The standalone Java 17 production-helper oracle covers 100,000 randomized finite boxes plus infinite, NaN, signed zeros, same/minX mismatch and subclass-specific equals behavior. This is bounded and reversible, not a general particle simulation/GPU-offload mechanism.

## Qualification and next decision
Prior #1023 A/B results remain a conditional single matched pair for the **separate** bulk CPU interpolation decision (pair 1/3). That path stays opt-in, with its two further pairs open. This new frustum experiment must not be conflated with those observations.

Local equality-only Java 17 oracle passed before publication; full Forge/native CI and RX on/off hardware benefit are pending until their individual results exist. If native CI succeeds, compare same new JAR/identically restored benchmark world **fast-path OFF vs ON**, holding CPU bulk and all GPU texture settings fixed. Inspect particle render ms/frame, full frame-time distribution, FPS, and Java JFR `AABB.equals` samples; reject if the transformed path produces errors/visual changes or if benefit is negligible. Keep default off without paired evidence. Particle light queries, buffers, and campfire tick work remain separate candidates.
