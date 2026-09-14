# GPU terrain lighting-demand telemetry

## Purpose and scope

Source commits `9d64eb0c5c5ec7f41cc5e429df7656a5e6040b15` and
`2de36511134e48feb0ab9076d6a84bef64b1787e` add a measurement-only estimator for
the lighting points demanded by surviving canonical GPU face candidates.

It does not call lighting hooks, retain lighting values, change snapshot version 4,
clear `CPU_REQUIRED`, allocate GPU output, or alter rendered geometry. The estimator
runs on chunk workers only when explicitly enabled and operates on the immutable
numeric snapshot already captured for the GPU terrain experiment.

## Enabling a real-section measurement

Both restart-only JVM properties are required:

```text
-Dvulkanmod.experimentalSectionVoxels=true
-Dvulkanmod.debugGpuLightingDemand=true
```

The logger emits the first observation and an aggregate every 128 completed section
builds under this marker:

```text
VULKANMOD_GPU_LIGHTING_DEMAND
```

For a useful sample, enter the Create Chronicles world, wait for initial chunks to
settle, then travel through previously unseen terrain far enough to build at least
several hundred sections. A Create-heavy built area should be sampled separately if
possible. Return every marker line from `latest.log`; no profiler or manual arithmetic
is required.

This property adds estimator work and is not intended for ordinary play or FPS
comparison.

## Logged fields

- `sections`: total measured completed section builds;
- `latest=(x,y,z)`: latest section origin;
- `qualified`: aggregate zero-offset canonical voxels;
- `faces`: aggregate candidates remaining after conservative full-occluder rejection;
- `unique(avg/latest)`: exact demanded points in the proven 20-cube coordinate domain;
- `bricks(avg/latest)`: active 4x4x4 bricks out of 125;
- `projected(point/brick/cpuMesh)`: aggregate candidate encoding bytes and the actual
  copied terrain vertex/explicit-index bytes for the same sections;
- `ratios`: projected point and brick bytes as percentages of those CPU mesh bytes;
- `densityBuckets`: section counts for zero, <=25%, <=50%, <=75%, and >75% of the
  8,000-point rectangular domain.

The point projection includes a 20-cube occupancy map, a per-word rank prefix,
bit-exact packed-light/shade records, AO predicate bits, and six directional shade
words. The brick projection includes a 125-brick mask, complete exact records for
each active 4-cube, predicate words, and directional shade words. These are sizing
models, not committed ABI layouts.

## Automated bounds

The startup smoke proves three deterministic cases:

- 4,096 qualified non-occluding voxels produce 24,576 candidate faces, all 125
  bricks, and 7,752 unique reachable samples;
- 4,096 qualified fully occluded voxels produce no faces and no projected payload;
- one isolated qualified voxel produces six faces and bounded sparse demand.

The 7,752 maximum sharpens the earlier 7,776 slab-layout bound: a face's outer AO
predicate moves along only one tangent, so the four tangential corners of each of six
second-shell slabs are unreachable. Even so, the projected indexed-point worst case
is 65,012 bytes, only 12 bytes below the rejected 65,024-byte dense reference; the
4-cube projection is 65,040 bytes. Production encoding must therefore fall back well
before worst-case density.

CI #413 correctly failed the initial 7,776 expectation. The corrected bound and all
existing build, Vulkan, rendering and compatibility gates passed in CI #414 (run
`34791671857`, job `103816942293`).

## Decision gate

Do not select an encoding or extend the snapshot ABI until representative RX 6900 XT
modpack logs exist. Continue only if common sections show a material retained-byte
reduction against their corresponding CPU meshes, with an explicit per-section
dense-demand fallback. Otherwise reject this CPU-resolved lattice approach rather
than moving CPU meshing cost into a different capture loop.
