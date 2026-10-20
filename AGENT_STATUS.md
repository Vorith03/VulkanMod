# VulkanMod Forge 1.20.1 — Current continuation checkpoint

## Active work

The owner selected maintainability/reuse implementation as a bounded detour from
Phase 7 actual-world GPU qualification. Follow AGENTS.md §3A and the live
[rolling assignment](docs/NEXT_SESSION_PROMPT.md).

Both reviews are tracked once in
[the maintenance tracker](docs/MAINTENANCE_TRACKER_2026-10-20.md), including
implementation/evaluation dispositions, limits and the section transition table.
Read its open/pending entries before starting more cleanup.

## Executable and validation

- Last fully qualified baseline: `682b0d6bb59b249c5be66517209f720e54ac6d1d`,
  full #1074 / [39308824108](https://github.com/Vorith03/VulkanMod/actions/runs/39308824108).
  JAR **12214688900**, logs **12214474322**. Private packs skipped.
- Maintenance cleanup `a15ce968e5b7078790a9a097602480564a6f25da`:
  full #1075 / [39506746716](https://github.com/Vorith03/VulkanMod/actions/runs/39506746716)
  was **cancelled/superseded** by the combined milestone. It had passed Forge
  packaging, both startups, native indirect, post/depth and screenshot steps;
  public modpack was still running. It is NOT full qualification.
- Further milestones: `0b50f449e8f937b932feb3d30a5b72b4a541dbc8`
  (native ownership, cache state and tests) and
  `27a14fca0dfea9f1b824239138c04fcbd9f623aa`
  (compute construction, texture configuration admission, device query and utilities).
  These are pushed. Combined full #1076 /
  [39507963173](https://github.com/Vorith03/VulkanMod/actions/runs/39507963173)
  **FAILED compileJava**: seven remaining direct accesses to the newly private
  MappedBuffer.buffer in BufferUploaderM/RenderSystemMixin. Failed smoke-log
  artifact **12304163339**, no qualified JAR/native result. Those two mixins now
  use byteBuffer(); the corrected executable requires a fresh full suite.
- Locally: 24 offline contracts passed twice. Java 17's compiler module works
  despite no javac launcher; a temporary launcher allowed actual contracts.
  Direct Gradle download is network-blocked. Focused actual LWJGL 3.3.1/fastutil
  8.5.9 tests passed for sorting, mapped ownership/copy state, ABI and alignment;
  pure admission/optional invocation tests passed. Uniform failure injection
  and full Forge/native execution remain pending remote validation.

## Implemented scope

Removed unused liquid fork/Fabric bridge, dead copy/readback/math/hash/query
APIs and two unregistered debug mixins. Primitive stable sorting and LWJGL
replace private algorithms/Unsafe. Buffer subclasses explicitly declare frame
versus upload retirement. MappedBuffer access checks closed state; uniform
binding closes borrowed views before freeing fallback bytes. Candidate cache
state is grouped per layer with named producer modes. Production compute creation
and capability query share bounded helpers. Texture admission exposes rejection
reasons without changing default-off prerequisites or early mixin initialization.
Existing GPU/CPU, barrier, readiness, provenance, exact-frustum and APPEND
transaction policy remains. Native/compatibility qualification is still required.

## Unresolved gates

Maintenance does not establish actual-world generated REPLACE/APPEND consumption,
nonzero GPU post-cull output, matched CPU/GPU main-world or genuine portal pixels,
private resource-pack qualification, real particle callbacks/lighting/translucency/
camera/reload/frame pressure, or RX 6900 XT adoption/displaced CPU/GPU cost/FPS/
p95/p99 benefit. Follow the
[world witness contract](docs/GPU_GENERATED_WORLD_DRAW_WITNESS_2026-10-19.md)
when those inputs become available; do not repeat settled user-machine tests or
request an isolated benchmark. All experiments remain default-off; 512 sections,
1,024 commands, atomic APPEND CPU exceptions, generation/provenance/readiness,
queue barriers, fence retirement and complete CPU fallback remain mandatory.

Larger parser, virtual allocation, widget and cache decisions are recorded in the
tracker, with explicit acceptance prerequisites. Profiler PR #12 remains open at
`3db6d0d5`; no competing recorder or merge was made.

## Historical evidence

The complete previous 511-line status is preserved in
[the dated archive](docs/AGENT_STATUS_ARCHIVE_2026-10-20.md) and Git. In particular,
keep failed #1067/#1060/#1064, cancelled #1071, #1070 attempt-1 startup failure
versus attempt-2 success, older #1054/#1050–1052/#1047–1048/#1041–1042 and analyzed
#1028 UUID `e05f2b31` distinct from passing qualification. Contracts and the
optimization ledger retain the detailed prior evidence; historical “latest”
headings do not override this checkpoint or newer live Git/CI.

Every successor must update this repository handoff and the rolling prompt, and
finish with a NEW complete copyable continuation prompt explicitly carrying this
same requirement forward.
