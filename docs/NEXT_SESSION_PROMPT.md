Continue development of https://github.com/Vorith03/VulkanMod on `forge-1.20.1`.
The owner-directed objective remains a substantive GPU-first release: GPU results
must replace eligible CPU graphics calculations in production. Do not resume the
old queue-size/BufferBuilder micro-optimization sequence or stop at investigation.

Follow `AGENTS.md` Section 3A against live Git/CI, then read `ROADMAP.md`,
`AGENT_STATUS.md`, `docs/OPTIMIZATION_EVIDENCE_LEDGER.md`,
`docs/GPU_FIRST_OWNERSHIP_2026-10-18.md`, and
`docs/GPU_PARTICLE_BILLBOARD_OWNERSHIP.md`. Treat the repository checkpoint and live
evidence as authoritative; inspect the delta instead of repeating old archaeology.

The previous qualified executable was `0e6f3657` / full CI #1033
(run 39150537659, JAR artifact 12145046866). It included fixed-budget ranked texture
admission, all-mip dispatch/shared atlas-copy ordering, and lazy GPU terrain indirect
command ownership. These remain opt-in and RX benefit remains unmeasured.

The outgoing session implemented the particle billboard pilot in `c225399f`:
`experimentalGpuParticleBillboards` admits only exact campfire smoke through the
normal unsorted Tesselator particle stream, retains Java simulation/light/callbacks,
and cancels inherited CPU four-vertex expansion. One compute dispatch per original
draw expands original quad slots, preserving mixed/custom ordering and the graphics
shader/portal clipping. Native tests cover transformed inherited render, geometry,
UV/color/light, callbacks, production uploader, sorting/failure recovery, buffer
growth and descriptor retirement. Read the durable status for the final executable,
CI result and correction commits: `0b6ba02e` counter/mapping and `761947c1` early
test shader initialization. Full #1036 (39178618231) passed at `761947c1`, JAR
12155143862; a final unsigned/bounded storage-range guard requires its own full-tip
qualification. Read status/live CI for that result instead of assuming the initial
commit or an intermediate green test qualifies the final executable.

Finish any pending/red qualification first. Then continue the integrated release:
qualify portal/mod callback and pixel semantics for the particle pilot, eliminate
per-draw terrain candidate scanning/allocation with correct producer/traversal/
readiness invalidation, complete multi-sprite texture execution, and extend the
candidate ABI before combining generated REPLACE/atomic APPEND with GPU indirect
selection. Preserve every existing generation/lifetime/exception fallback contract.
Do not mistake frustum culling for graph visibility. Keep gates until equivalent,
profitable production ownership is demonstrated.

The #1028 owner capture UUID `e05f2b31` is already read and documented (3.747% GPU
interpolation, 16.687 ms texture/tick); do not request it again. No unchanged
benchmark or JAR for each isolated experiment. Build one meaningfully integrated
candidate before narrowly requesting the changed RX observation. No new hardware
CPU savings, GPU cost, FPS or p95/p99 result has been measured for these pilots.

Work autonomously through coherent implementation and focused/native/Forge checks,
commit logical milestones, and update roadmap/status/ledger when evidence changes.
Do not claim the entire assignment finished after this particle slice.

**Before ending your next session, reach another coherent handoff point. Commit
and publish recoverable work, record exact executable/CI/artifact state and remaining
gates, update this prompt, and include a new copyable handoff prompt in your final
response. That prompt must instruct the following session to end with another
updated handoff prompt as well. Preserve this rolling-handoff requirement.**
