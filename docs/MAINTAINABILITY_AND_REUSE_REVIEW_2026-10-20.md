# Maintainability review and second reuse pass — 2026-10-20

## Result and evidence boundary

The largest maintenance cost is the number of contracts a change must preserve across files: native ownership, GPU/CPU publication, feature admission, binary layouts, optional-mod APIs and validation. Removing generic helpers helps, but a large framework or renderer rewrite would not solve these contracts and would add migration risk.

This review extends, rather than repeats, [the first reuse audit](REUSE_AUDIT_2026-10-20.md). New material reuse findings are our private `Unsafe` access layer, compute pipelines bypassing the existing Vulkan pipeline cache, repeated compute-pipeline bootstrap, and unused copy helpers. Most are reuse of dependencies or infrastructure already present; no new library is needed for them.

**Baseline:** executable `682b0d6bb59b249c5be66517209f720e54ac6d1d`; local first-audit commit `10e111f5c005c7c062191d7dddb1f317feeba598`. The live remote still pointed to the executable when checked. CI run [39308824108 / full #1074](https://github.com/Vorith03/VulkanMod/actions/runs/39308824108) remained successful. Open [PR #12](https://github.com/Vorith03/VulkanMod/pull/12) still had head `3db6d0d55ae46fae594696bd271c09c44877d921`; its flight recorder work was not merged or duplicated. At review completion, the earlier documentation push had been blocked by automatic review and the audits remained local. The owner subsequently authorized publication on October 20; use live Git for publication state.

**Coverage:** all 406 main Java files were rescanned for size, imports/references, method duplication and maintenance signals; resources, shader contracts, tests, scripts, packaging, mixin registration and handoff documents were revisited by subsystem. Focused semantic review covered the findings below. The [first inventory](REUSE_AUDIT_INVENTORY_2026-10-20.tsv) still describes the unchanged executable files. This is not exhaustive line-by-line correctness certification, a security audit or qualification of a refactor. No newly reproduced renderer failure or performance improvement is claimed.

## Prioritized maintainability findings

### M1 — Native lifetime rules are easier to violate than the API suggests (high)

**Evidence:** `vulkan/util/MappedBuffer` exposes its `ByteBuffer` and raw pointer publicly. `free()` tracks ownership and idempotence, but accessors do not consult `freed`; its native address survives logically after release. `EffectUniformBindings.bind` supplies wrappers around borrowed Minecraft storage and separately owns fallback allocations. `MemoryManager.addToFreeable` selects a stronger upload-retirement rule specifically for `StagingBuffer` and `TextureResidentBuffer`, rather than from an explicit lifetime requirement on every buffer.

**Why this matters:** a new wrapper consumer can mistake a borrowed view for owned storage, retain a released address, or introduce an upload buffer subtype without learning the central retirement-type exception. This is API misuse exposure, not evidence of a currently executed use-after-free. Switching `Unsafe` to LWJGL alone does not fix lifetime misuse.

**Bounded improvement:** document owned versus borrowed wrappers at constructors/factories; narrow raw-pointer exposure where consumers allow it; make closed-state checks available without silently adding locks/checks to every hot-loop access. Give buffer retirement an explicit, small domain such as frame-fence versus upload-completion and preserve the actual same-queue/fence implementation. Audit current subclass consumers before changing classification. Do not introduce a Cleaner/finalizer as GPU synchronization.

**Verification:** owned/borrowed and repeated-release behavior, failure halfway through uniform binding, queued upload after a frame fence, resize/reload and safe retirement. Native counters and failure-path behavior matter more than the presence of a `free` call in bytecode.

### M2 — Candidate cache and section state require coordinated mutations (high)

**Evidence:** `RegionDrawBatch` maintains parallel per-layer arrays for table, initialized state, mode, generations, several revisions, frustum, provenance bitsets and completeness. Its producer mode is encoded as `0/1/2`. `RenderSection` separately tracks preflight, visible-generation/ownership, CPU completeness, forced recovery and staged APPEND CPU state; producer notifications connect these to `ChunkArea` and `DrawBuffers`. Recent generation/invalidation fixes demonstrate why that coordination is important. The existing `GpuTerrainAppendRebuildTransaction` already provides a good bounded transaction boundary.

**Why this matters:** adding a cache dimension or transition means finding every reset/reuse/publication site. An omitted field can preserve a stale proof even when each individual check looks plausible.

**Bounded improvement:** first group one layer's cache metadata behind a small state holder with named invalidate/publish operations; use a named mode instead of magic integers. Preserve primitive storage where it matters and do not introduce one object per candidate or per frame. Create an explicit transition/invalidation table for existing section methods before changing their semantics. Keep the APPEND transaction and separate input/visible generations; a single global enum is not enough to represent them.

**Verification:** retain unchanged-state reuse, one test per invalidating producer transition, stale queued publication, generation churn and atomic APPEND fallback. No removal of locks or readiness checks solely for tidiness.

### M3 — Binary layout maintenance spans Java and GLSL (high)

**Evidence:** `GpuRegionCandidateTable` defines magic/version, header/record sizes and flag bits; `section_select_probe.comp` independently repeats the corresponding values and a `1024u` capacity bound. `RegionBatchLayout` defines the 20-byte standard indirect command and region/command limits. `GpuTextureInterpolationCompute` defines job/push word counts that the animation shader must interpret. The existing native and layout tests provide important coverage; this is not a claim that an ABI mismatch currently exists.

**Why this matters:** a seemingly local Java or shader change can compile while changing a wire-format contract. A derived mask may intentionally differ in its shifted/unshifted representation, so naïve text equality is also insufficient.

**Bounded improvement:** add a small machine-checked contract covering the authoritative constants, sizes, offsets and representations, extending current tests before adding code generation. Use LWJGL's standard struct metadata to validate standard Vulkan layouts when available; keep project-specific serialization explicit. Generate a shared constants include only if it reduces the number of sources of truth and works in the actual Forge/resource pipeline. SPIR-V reflection is a separate candidate, not a necessary new native dependency for a handful of constants.

**Verification:** compiled/native round trips, maximum capacity and atomic pair edge cases, version rejection, field offsets and independently expected values. Do not replace GPU execution tests with a textual constants test.

### M4 — Flag admission is distributed and difficult to explain from one place (medium/high)

**Evidence:** lexical scan found 137 direct system-property getter calls across 56 main Java files, including diagnostics/tests. `RegionDrawBatch` combines indirect-command, draw, bypass, handoff and generated REPLACE/APPEND gates; texture multi-dispatch depends on shared scratch, batching and source pooling. `MixinPlugin` makes some decisions earlier than renderer initialization. Parsing helpers in `PerformanceProfiler` and `AutomatedBenchmark` overlap but deliberately have different clamp/reject/range policies.

**Why this matters:** the requested flag set can differ from the effective feature set for legitimate reasons. Maintenance and support require reconstructing that dependency graph, and a future centralization could accidentally load renderer classes during early mixin initialization.

**Bounded improvement:** maintain a typed, allocation-free effective-feature snapshot or dependency table with explicit reasons for non-admission, keeping the early bootstrap layer independent of Vulkan classes. Share only the basic property parsing primitive; keep each feature's validation/admission policy named. Preserve startup-only versus reloadable behavior and all default-off gates. Avoid adding a configuration framework for this.

**Verification:** default-off matrix, each missing prerequisite, malformed/nonfinite values, unsupported capability and early initialization order. A list of requested properties alone is not proof of activation.

### M5 — Some tests verify implementation shape more strongly than behavior (medium/high)

**Evidence:** `SpirvNativeLifetimeTest` visits bytecode and sets booleans when release-method invocations occur. That can confirm a release call exists without proving every relevant failing path reaches it exactly once. Some Python contracts extract or rewrite source snippets and compile stubs. In contrast, `cpu-interpolation-contract.py` copies the production kernel and checks independent scalar pixels including rejected input without partial writes; `MatrixProductTest` checks values, buffer positions, aliasing and warmed allocations. These are useful behavioral examples.

**Why this matters:** implementation rearrangement can break a textual test without changing behavior, while a misplaced cleanup call can pass a presence-based test. Neither risk invalidates all existing contracts; native smoke tests remain distinct evidence.

**Bounded improvement:** label shape/packaging guards explicitly; supplement the riskiest cleanup paths with injected failures and allocation/release accounting. Extract a genuinely dependency-free policy helper when that removes source surgery, then test the actual helper. Keep independent oracles independent. JUnit may improve test discovery/reporting later, but changing test frameworks does not make an assertion behavioral.

**Verification:** show that the test fails for an early-return cleanup leak, double release, partial mutation and intentionally incorrect result. Retain actual produced-JAR/mixin/native coverage.

### M6 — Optional integrations have repeated reflection and divergent failure policies (medium)

**Evidence:** `ImmersivePortalsLevelRendererCompat` and `ImmersivePortalsShaderCompat` cache reflective methods and manually unwrap invocation failures; Flywheel adapters repeat reflective discovery and invocation. `EntityCullingCompat` instead warns on a changed optional interface and can remain unavailable. Different contracts justify some differences. Much of the repository's reflection occurs in smoke fixtures, so raw reflection counts are not runtime overhead evidence.

**Why this matters:** optional absence, an unsupported installed version and an exception thrown by a supported mod are materially different states. Duplication makes it easier to handle them inconsistently or hide the original cause.

**Bounded improvement:** keep per-mod adapters and their pinned API expectations; share only cause-unwrapping/discovery primitives with explicit absence policy. Record integration status clearly. A compiled optional adapter or MethodHandle is an evaluation choice, not an automatic improvement over cached reflection. Do not suppress exceptions from active integrations or add a general reflection dependency.

**Verification:** mod absent, supported pinned API, incompatible API and underlying runtime/error causes, including class-loading behavior when the optional mod is absent.

### M7 — Large classes mix policy, native resources and diagnostic bookkeeping (medium)

**Evidence:** `GpuTextureInterpolationCompute` is 1,069 lines and handles admission gates, descriptor/pipeline resources, scratch-page budgeting, pending jobs, dispatch/copy recording, statistics and retirement. `RegionDrawBatch` is 944 lines; `PerformanceProfiler` is 1,154. Line count alone is not a defect, and class splitting alone is not a fix.

**Bounded improvement:** choose one independently testable responsibility at a time, such as scratch-page budget/lifetime bookkeeping or candidate-cache state. Keep command ordering, barriers and retirement in a clearly visible coordinator. Keep profiler work coordinated with existing PR #12. Do not split one native transaction across opaque collaborators merely to shorten files; avoid a new general compute/render graph.

**Verification:** unchanged command order, flush conditions, budget accounting including pending retirement, rollback and shutdown behavior, plus existing native gates for executable edits.

### M8 — Copy helpers hide byte units and mutate buffer state (medium)

**Evidence:** `VUtil.memcpy(ByteBuffer,ByteBuffer,int,long)` sets source limit to `size`, offsets the destination, copies, then resets the source to its capacity and rewinds. Other overloads also reset source state. Live consumers include `MemoryTypes` and `UniformBuffers`. `ManualUBO` accepts a constructor `size` multiplied by four, while `setSrc` stores a byte count used directly by `memCopy`.

**Why this matters:** `size` can mean bytes, words or an absolute buffer limit, and a method named memcpy suggests fewer side effects than these wrappers have. A caller changing slice positions can silently depend on historical reset behavior.

**Bounded improvement:** name units (`byteCount`, `wordCount`, `dstByteOffset`) and document or isolate copy-and-reset behavior. A position-preserving API can use duplicates/explicit offsets where appropriate, but do not change current callers' buffer semantics in a mechanical replacement. Remove unused overloads before designing more wrappers.

**Verification:** nonzero positions, slices, exact limits, capacity bounds, byte order and caller-visible source/destination positions. No memory-copy speedup is assumed.

### M9 — Stale comments and growing checkpoints can misdirect the next change (medium; low-cost cleanup)

**Confirmed contradiction:** the class comment in `GpuSectionSelectionShadowStore` says its output is shadow-only and production consumes CPU commands. `RegionDrawBatch.draw` now conditionally consumes that store's output for production indirect commands. `GpuTerrainSectionMesher` also uses a shader still named `section_mesher_probe.comp`. `GpuTextureInterpolationCompute`'s top comment describes per-animation resources without explaining shared/batched modes.

`AGENT_STATUS.md` was 509 lines before this review and contains many superseded checkpoint sections; the standing protocol calls for a concise living continuation checkpoint. `ROADMAP.md` and the ledger also retain substantial chronological checkpoints. History is valuable, but it competes with the current decision at session recovery.

**Bounded improvement:** correct role comments first, identifying default-off production consumption separately from diagnostic modes. Do not rename files/properties en masse. Condense the living status to the current qualified executable, limits, active gate and next action; retain exact older negatives/evidence in a linked archive and Git history. Preserve the distinction between current qualified code and actual-world performance evidence.

**Verification:** source comments versus current call sites; all old evidence identifiers remain recoverable; the successor can identify the active gate without following several conflicting “latest” sections. No archive/removal performed in this audit.

## New reuse opportunities from the second pass

| ID | Confirmed duplication or missed reuse | Recommended direction | Boundary / acceptance |
| --- | --- | --- | --- |
| R2-1 | `VUtil` reflectively obtains `sun.misc.Unsafe`; its `UNSAFE` consumers are four scalar accessors in `MappedBuffer`, three float writes in `VRenderSystem` and four long writes in `Drawer`. Other code already uses `MemoryUtil.memGetInt/memPutInt/memGetFloat` and related methods. | Use pinned LWJGL `MemoryUtil` address accessors (`memGet/PutInt`, `memGet/PutFloat`, `memPutLong`) at all three consumers; delete our reflective `Unsafe` bootstrap once no consumer remains. | No new dependency. Pinned 3.3.1 API presence checked. Preserve native byte order, exact bits, address arithmetic and lifetime ownership; don't claim this adds bounds/use-after-free protection. |
| R2-2 | Production texture, particle and terrain mesher `vkCreateComputePipelines` calls pass `VK_NULL_HANDLE` for the cache, while `Pipeline` already owns a persistent device-qualified Vulkan pipeline cache. | Evaluate a device-scoped cache service shared by graphics and production compute construction. | Vulkan supports the same cache kind for both. Preserve initialization/teardown order, cache identity, size limits, error fallback and host synchronization. Avoid forcing eager `Pipeline` initialization by exposing its static field. Benchmark cold/warm creation or reload; no FPS claim. |
| R2-3 | Texture and particle compute `createPipeline()` routines repeat compile/module/stage/create/finally-release bootstrap; terrain and probe paths have similar code. | A small internal compute-pipeline construction helper can own temporary shader/module cleanup. | Internal reuse, not a new engine/library. Keep descriptor layouts, dispatch, barriers and lifetime policy at the subsystem. Preserve independent test oracles and cleanup on every creation failure. |
| R2-4 | `VUtil`'s short-array/FloatBuffer scalar-copy overloads and `memcpyImage` have no call sites found by whole-source search; live ByteBuffer overloads already delegate copying to LWJGL. | Delete unsupported unused helpers after checking reflective/external API obligations. If later needed, evaluate JDK typed-buffer bulk operations. | Do not replace the live upload path wholesale or reintroduce unused API merely to use a library. Preserve byte order/positions in any future bulk equivalent. |

A smaller related cleanup: `MemoryType.copyFromBuffer` has no callers found, and the device-local implementation is empty. Remove a dead abstraction or explicitly reject unsupported use before it acquires callers; do not make a generic readback call silently succeed. This is API clarity, not a currently observed missing screenshot path (screenshots use their own implemented readback path).

## First-pass findings rechecked

- The unused `LiquidRenderer` and annotation-only Fabric compile dependency remain the clearest whole-implementation cleanup.
- fastutil stable sorting remains a plausible replacement for `SortUtil`; stable floating-point comparison and translucent ordering still require differential validation.
- GLSL parser replacement still needs exact-version, licensing, packaging, preprocessing and shader-corpus evaluation. More ad hoc parsing is not the preferred long-term direction.
- VMA virtual allocation remains a candidate for **generic free-range bookkeeping only**. It does not replace ready/pending state, generation ownership, APPEND transactions, growth or fence retirement.
- Existing Minecraft widgets remain preferable to duplicating generic interaction behavior when a bounded UI cleanup is selected.
- `ResettableQueue`, GPU/CPU ownership, Forge/portal adapters and native fixture infrastructure have special semantics. No drop-in replacement was established by this second pass.

## Suggested sequence and validation

1. Correct misleading comments and streamline the living handoff without losing historical evidence.
2. Remove unused liquid code/dependency and unused copy/readback APIs in a bounded cleanup; qualify executable changes normally.
3. Replace our private Unsafe bootstrap using existing LWJGL, and separately replace the custom stable sorter with differential checks.
4. Add behavioral failure-path/ABI tests around a selected high-risk seam before changing native/cache/section ownership representation.
5. Only then consider one structural change: per-layer cache state, explicit retirement policy, shared compute construction/cache, or a shader-parser evaluation. Do not undertake them all together or turn this review into new mandatory release prerequisites.

The active real-world GPU/portal qualification and RX profitability gates remain unclosed. No audit cleanup satisfies them. No renderer source, dependency, default or ownership behavior changed during this review; no new full build/native run was launched for documentation.

**Checks performed:** live branch/CI/PR delta; complete lexical scans; candidate call-site and lifecycle review; comparison with existing tests/native gates; pinned MemoryUtil API verification; primary Vulkan API documentation verification; documentation diff/links and unchanged-executable checks. The scans found 185 `@Overwrite` annotations in 41 main Java files and 49 main-source files with `Smoke` in their filenames (12,037 lines). Counts include inactive mixins/fixtures and are context, not a claim that all overrides execute or that smoke code should be removed from production packaging without an equivalent produced-JAR validation route.

Primary references: [LWJGL 3.3.1 MemoryUtil source](https://github.com/LWJGL/lwjgl3/blob/3.3.1/modules/lwjgl/core/src/main/java/org/lwjgl/system/MemoryUtil.java), [Vulkan pipeline-cache API](https://docs.vulkan.org/refpages/latest/refpages/source/vkCreatePipelineCache.html), [compute pipeline creation](https://docs.vulkan.org/refpages/latest/refpages/source/vkCreateComputePipelines.html). API capability is verified; migration benefit remains unmeasured.
