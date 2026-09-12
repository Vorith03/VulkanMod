# Terrain priority override — 2026-09-12

The user explicitly chose to park the remaining Create Chronicles F3+T memory-reload issue and continue to the terrain/performance work so the project can reach the GPU-driven renderer work sooner.

This is an intentional roadmap sequencing override, not evidence that the remaining Phase 4 gates are complete.

## Parked Phase 4 limitation

Build #310 verified that the pre-decode native allocator purge is effective: after old static atlas CPU data was retired, process RSS dropped by about 1.2 GiB before replacement decoding. The full-pack reload still hit the unchanged system-memory safety floor later while the Minecraft DRM client's GTT residency increased substantially. The reload issue remains open and should be resumed from the existing allocator/DRM diagnostics rather than restarted from scratch.

Do not weaken the memory safety guard to mark this path complete.

## Active priority

Proceed with the terrain-performance track:

1. keep a lightweight repeatable benchmark contract in `docs/TERRAIN_PERFORMANCE_BASELINE.md`;
2. improve the existing persistent region-buffer/suballocation architecture;
3. reduce terrain allocation and CPU submission churn;
4. preserve translucent/tripwire and direct/legacy fallbacks while the region backend matures;
5. collect comparable RX 6900 XT/OpenGL measurements before claiming a performance win;
6. use the resulting persistent-residency model as the prerequisite for the later GPU-driven/mesh-shader backend.

The unfinished Phase 4 compatibility gates remain release/stability work and must be revisited before final release closure.
