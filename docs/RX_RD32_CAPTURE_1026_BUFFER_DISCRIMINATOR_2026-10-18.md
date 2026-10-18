# RX RD32 #1026 particle buffer discriminator — 2026-10-18

## Hardware capture

- Owner UUID `9b7f3496-f876-4ca7-ba6a-482519c5373a` at full-CI qualified executable `8da6c4041852908bf9e41f1713ca41ce37798c3d` (#1026). Received benchmark, raw JFR, latest and debug.
- `capture_complete reason=benchmark_complete`: 7,576 frames in 180.012 s (~42.09 FPS). Same nominal RX 6900 XT/RADV, Java 17, Forge 1.20.1, RD32 / SD12, 2552×1374 stationary spectator camera, owner-authorized restored-world snapshot. Settle time 79.124s (vs 72.940s for #1025). GPU/texture and CPU bulk/frustum configuration retained. Nominal clock/world similarity is not byte-identical JIT/server/thermal state.
- ~42.09 FPS vs #1025 ~47.68 FPS is **not clean net-regression proof** because #1026 instrumented the sampled hot method and runtime state differs.
- `world_render_attribution` reports 7,576 particle renders averaging 4.178 ms each. Campfire smoke ~29.31M particle tick calls. Terrain remains 6,559 visible sections / 1,604 retained dirty notices.

## Discriminator

Exact logger line reports `finalizations=7576 finalization_capacity_checks=0 resize_calls=0 finalization_resize_calls=0 vertex_resize_calls=0 resize_wall_ms=0.000`. The **zero actual native resize calls** is narrowly scoped to the measured particle-render thread and the redirected `MemoryTracker.resize` call; it does not describe startup, vertex work outside particle rendering, or all of Minecraft.

**The zero finalization capacity-check counter is invalid.** Raw JFR has 2,751 Render-thread Java execution samples with `ParticleEngine.render` on stack; 850 show `BufferBuilder.m_85722_` (mapped `ensureCapacity`), ~792 directly under `BufferBuilder.m_231177_` (mapped `storeRenderedBuffer`); ~811 samples end at the capacity method. These are sampled stacks, *not* the number of calls or an exclusive millisecond estimate. The injected per-builder finalization flag and `@Inject(ensureCapacity HEAD)` didn't reliably record those checks. Whether they ran before/after some flag transition remains unproven; interpreting their counter's zero value as absence of checks is false.

## Repair and next work

Remove both transient finalization marker hooks and the invalid per-check injection, including its sampled-hot-method overhead; preserve only real scoped `MemoryTracker.resize` accounting and the existing native-pointer update. The forced-growth production Forge vertex smoke from #1025 continues to verify growth, vertex bytes, release, and reuse. This **is not an FPS optimization** and no benefit is claimed.

#1026 supplies no evidence of steady-state particle native reallocations. Do not rewrite growth policies or request duplicate baselines. Separate supported hotspot: the same JFR has ~474 sampled leaves in mod-injected `ClientLevel.handler$bpg000$onHasChunk`, along `ClientLevel.hasChunk → LevelReader → Particle.getLightColor → SingleQuadParticle.render`. Identify mod ownership and preserve chunk-loading/portal/dynamic-light semantics before changing it. Texture tick non-upload CPU is another substantial independent candidate. One owner-machine test should be requested only after an actual eligible optimization needs comparison.
