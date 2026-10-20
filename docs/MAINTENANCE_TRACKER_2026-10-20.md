# Maintainability and reuse implementation tracker

One combined entry per finding from both October 20 reviews. Baseline `ecaa21f7`;
qualified maintenance executable `6c65e00f`, full #1078 / run 39509869276.
Prior maintenance baseline `c303cf26` / full #1077 passed as recorded below.
Previous baseline `682b0d6b` / full #1074 is retained as historical evidence.
Audits/inventory describe reviewed code, not migrated code. No FPS claim.

| Findings | Inspected contract and bounded action | Disposition and evidence / remaining risk |
| --- | --- | --- |
| R1 liquid fork | No source, metadata, mixin/plugin or supported API consumer of internal LiquidRenderer; ChunkTask uses Forge renderLiquid and LiquidBlockRendererM adapts real vertices. Removed fork, Fabric annotation-only dependency and now-unused Maven repository. | Implemented in `a15ce968`; full #1075 / 39506746716 cancelled/superseded; combined full #1076 / 39507963173 failed compilation (see below); correction qualified by full #1077. Unknown private external callers are not a supported API. |
| R2 stable sorting | Existing fastutil primitive stableSort preserves descending Float.compare and encounter-order ties. Frozen old implementation is test-only; independent boxed stable oracle also checked. | Implemented `a15ce968`; local actual fastutil 8.5.9 differential passed (500 random/tied/raw-float sets, empty/singleton, signed zero, infinity, NaN payloads, reverse encounter order). Pinned Forge compilation/check passed in #1075; full #1077 native suite passed. No throughput claim. |
| R2-1 Unsafe | All 11 scalar uses now call LWJGL; addresses/offsets and native representation unchanged. Reflective bootstrap removed. | Implemented `a15ce968`; local LWJGL 3.3.1 raw-NaN/signed-zero checks passed; #1075 build passed. No bounds/concurrency safety inferred. |
| R2-4 / M8 copy APIs | Removed uncalled short[]/FloatBuffer/image helpers and package-private MemoryType readback abstraction (including empty device-local implementation). Live copies retain source reset/destination-position semantics; documented absolute source limit versus byte count. ManualUBO names words/bytes. | Implemented cleanup `a15ce968`, additional docs/test in ownership milestone. Local nonzero position/slice/limits/native-order behavior passed. Full #1077 passed. No generic new copy API. |
| M1 native ownership | MappedBuffer hides fields, exposes checked borrowed address/view, rejects scalar access after free, documents confinement and owned/borrowed lifetime. Uniform bindings invalidate created borrowed wrappers before releasing fallback bytes, including partial failure. Explicit abstract Buffer.Retirement requires every direct subclass to name its lifetime instead of central concrete subtype exceptions. Staging/resident buffers require upload completion; existing other subclasses require frame fences. | Implemented; local owned/repeated/borrowed close and stale-address checks passed. Implementation `0b50f449`, policy made abstract in `27a14fca`. Actual uniform failure test ran in full #1077. Follow-up `6c65e00f` strengthens immediate cleanup and mat3 failure safety; #1078 passed. Upload/frame queues, active-batch exclusion and queue-idle waits unchanged. Escaped raw addresses/views remain caller-owned lifetime responsibility. |
| M2 / M7 cache cohesion | One CandidateCache per layer groups primitives/bitsets/table/frustum; named producer modes and shared invalidation. No per-frame/per-candidate holder allocation. Input/visible generations and APPEND transaction untouched. Transition table below. | Implemented; existing unchanged reuse, producer revocation, stale publication and atomic APPEND native suite remains required. Implementation `0b50f449`; no proof of all asynchronous worlds from fixtures. |
| M3 ABI | Independent expected selector constants, shifted Java/unshifted GLSL masks, capacity, header/record offsets, native VkDrawIndexedIndirectCommand metadata, nonzero serialization position and rejected pair no-partial-mutation checks. | Implemented; local LWJGL ABI check passed. Existing compiled/native version rejection/selection/1,024-command pair tests retained. Texture ABI also checks independent job/push words/capacity, ordered push fields, mode sentinel and selected job offsets; native pixel oracle remains separate. |
| M4 admission | Pure AnimationComputeAdmission enumerates requested/effective configuration reasons for shared scratch/multi-dispatch; production gates delegate with original short-circuit truth table. Capability/resource admission remains separate, as do terrain policies and early mixin bootstrap. | Implemented bounded texture seam; all 16 configuration combinations and missing-prerequisite reasons passed standalone without renderer loading. Retained broader policy separation after evaluation: profiler clamp versus reject policies and startup/reload timing are intentionally different. No global getter rewrite or framework. Device capability rejection remains enabled()/binding readiness, not a configuration admission claim. |
| M5 test quality | Labelled bytecode tests as shape guards. New real allocator-backed storage, independent sorting and ABI behavior; partial uniform allocation failure seam counts real releases and preserves cause. | Implemented subset; uniform execution passed in #1077; six cleanup/partial-write mutants rejected locally, with strengthened #1078 coverage passed. Existing produced-JAR/mixin/native coverage retained. No framework migration. |
| M6 optional integrations | IP bridges had identical cause unwrapping; helper only shares mechanics. Per-adapter absence/discovery/API failure policy unchanged; EntityCulling intentionally warns on incompatible interface, active IP failures propagate. Flywheel has distinct ownership/API policy. | Implemented shared IP unwrapping; standalone actual helper test passed for runtime/error identity and checked cause/context without optional classes. Public IP/Flywheel compatibility still required. Broader discovery unification retained after evaluation: different missing/installed contracts must stay explicit. |
| M9 docs/handoff | Selector comment now acknowledges default-off production ownership; texture comment distinguishes individual/shared/multi modes. | Comments implemented `a15ce968`; 511-line historical checkpoint preserved verbatim in AGENT_STATUS_ARCHIVE_2026-10-20.md (including old negatives), current status condensed to current work, qualified versus pending code, open gates and links. Historical negatives must remain linked. |
| R2-2 compute cache | Pipeline statically captures VkDevice and creates persistent cache on class initialization, destroys it via Vulkan teardown. Loading it from compute would introduce eager graphics initialization and cross-owner synchronization. Device-qualified persistence is useful, but sharing its raw handle is not a safe bounded accessor. | Deferred with concrete prerequisites: first extract explicit device-scoped create/use/save/destroy service, serialize cache host access, retain corrupt-cache fallback and size/identity limits; measure cold/warm creation. No benefit claim or cache adoption yet. |
| R2-3 compute construction | Shared ComputePipelineFactory replaces three compile/module/stage/create/finally routines. Temporary SPIR-V/module cleanup belongs to the helper; partial failed pipeline handle is destroyed. Callers retain layout/descriptors/dispatch/barriers/retirement. | Implemented `27a14fca`; three native smoke consumers passed in #1077. Follow-up `6c65e00f` exercises compile/module/pipeline/partial failure cleanup locally; full #1078 passed. Cache intentionally remains null until explicit cache lifecycle service exists; no compute framework or fixture-oracle consolidation. |
| R3 parser | GlslConverter uses Minecraft resource preprocessor and shaderc downstream. Existing parser assumes declarations by line; AST alone cannot resolve Mojang resource imports or JSON uniforms. Project mods.toml declares LGPL-3.0-or-later; proposed glsl-transformer has unresolved exact version/licensing/distribution/transitive dependencies. | Blocked by identified evaluation inputs: pin compatible license/version and representative actual pack/mod corpus before migration. Current GlslConverterParsingTest and bundled core/effect corpus retained; no parser dependency or additional grammar promised. Requires generated-binding/native-pixel comparison, packaging and reload cost before adoption. |
| R4 virtual allocation | AreaBuffer includes stable Segment identity, reserve-without-growth, non-power-of-two vertex stride, backing-buffer growth and readiness callbacks; RegionVoxelPageAllocator is fixed-capacity/power-of-two with failed allocation as normal CPU fallback. VMA fixed virtual extent cannot absorb those policies. | Retained after evaluation for this assignment: replacing two small free lists would add native handles/cleanup and offset-preserving growth reconstruction without removing renderer policy. Reconsider only with aligned fragmentation/exhaustion/growth allocation trace and measured net complexity/cost improvement. No native binding upgrade needed. |
| R5 widget reuse | OptionWidget splits row hover/control hitbox; OptionList2.Entry.render directly changes y and CyclingOptionWidget caches child-button positions; list owns staged option apply/cancel, rendering and scrolling. AbstractWidget would require replacing duplicate x/y/focus/active storage, implementing render/narration hooks and migrating slider/cycle/list event routing together. | Deferred UI migration: needs keyboard/tab/narration, drag, scroll/resize and cancel/apply/persistence matrix on a live config screen. No new config framework. LayoutElement accessors currently stubbed; evaluate separately rather than silently claiming migration complete. |
| R6 small utilities | SwapChain integer clamp now uses JDK min/max with exact original argument order. Removed unused float/saturate/lerp MathUtil API, posLongHash and findMemoryType. Positive log uses numberOfLeadingZeros with explicit invalid-input rejection. Common arbitrary-stride alignment rejects invalid/overflow inputs. Pair consumer never mutates or uses pair identity as a key: uses existing Mojang Pair. Removed inactive gui.DebugHudM and debug.DebugScreenOverlayM; current mixin JSON/plugin register neither. Active TerrainDebugOverlayMixin retained. | Implemented; local arbitrary alignment (including 3/7/20 strides and overflow) passed. Forge packaging/native compatibility passed in #1077. No new utility dependency; JOML/depth conversion and active HUD unchanged. |
| R7 query reuse | Device now owns production graphics-compute query keyed by VkPhysicalDevice identity and selected queue family; null/out-of-range fails closed, destroy clears cache. Texture/particle use it; probes retain their independent queries. | Implemented; native capability/lifecycle suite passed in #1077; hardware device-replacement behavior remains beyond fixtures. Query is render-thread confined like device setup; no cross-thread capability service or per-frame allocation. |

## Section/cache transition table (existing semantics retained)

| Producer transition | Revoked/advanced proof | Visibility / fallback rule |
| --- | --- | --- |
| setDirty / voxel invalidation | input voxel generation (also CPU builds with capture disabled), preflight/staged work | Previously visible handoff remains distinct until current replacement commits; stale queued generation cannot publish. |
| setChunkArea / owned-slot replacement | departing/destination owner and input revisions; queue membership audit | Foreign identities cannot reuse current table. |
| setMainDirection change on queued section | queue provenance validity | Both seed-to-graph and graph-to-seed require fresh traversal. |
| stage/clear preflight; CPU-complete change | generated producer revision | Changed REPLACE/APPEND state cannot reuse unchanged numeric geometry proof. |
| APPEND staged CPU exceptions | exact generation/section stage; transaction holds prior visible state | Neither half publishes alone; failure rolls back/retains CPU recovery. |
| visible handoff / complete CPU recovery | visible generation/ownership and generated revision | GPU residency generation must match visible owner, not merely new input generation. |
| geometry upload/readiness/retirement | mesh and immutable geometry revision | Missing/unready/retired output rejects GPU ownership. |
| traversal graph/direct bits or exact portal volume | input/provenance or volume comparison | Off-frustum graph entries and direct seeds remain distinct; exact volume required. |
| queue proof failure or producer change during build | invalidate layer cache | Complete CPU fallback; no mixed-generation publication. |

Coverage map (methods in RegionBatchSmokeTest unless named otherwise):

| Transition / invariant | Behavioral coverage retained or extended |
| --- | --- |
| Dirty input versus previous visible ownership | verifyGpuFirstTransitionContract |
| Owned identity / area transfer / direction provenance | verifyCandidateProducerProvenance; verifyCachedCandidateProducerRevocation |
| Stage/clear preflight and CPU completeness | verifyGeneratedReplaceCandidates (clear case added in `6c65e00f`) |
| APPEND staged halves, commit, stale discard | verifyGpuAppendRebuildTransactionContract |
| Visible handoff and complete CPU recovery | verifyGpuFirstTransitionContract; verifyGeneratedReplaceCandidates |
| Geometry upload/readiness/retirement | verifyCandidateGeometryProducer; verifyGeneratedCandidateResidencyFingerprint |
| Traversal bits and exact frustum volume | verifyCandidateProducerProvenance; native section selector oracle; frustum-equality-contract.py |
| Queue proof revocation / stale completed publication | verifyCachedCandidateProducerRevocation; build/PublicationDrainSmokeTest |
| Ordinary/generated mode round trip with stable revisions | verifyGeneratedReplaceCandidates (added in `6c65e00f`) |

This maps discriminating cases, not an exhaustive asynchronous world-state proof.

## Earlier validation checkpoint (superseded by follow-up below)

Local Implementation milestones: `a15ce968` cleanup; `0b50f449` ownership/cache/ABI;
`27a14fca` compute/admission/query/utilities.

24 offline contracts passed using Java 17's compiler module (the javac
launcher is absent). Initial Gradle wrapper bootstrap cannot download directly
(`Network is unreachable`); focused tests used actual Maven LWJGL 3.3.1 and
fastutil 8.5.9 jars via the environment proxy, not substitute implementations.
Full #1075 passed build/distribution and native startup/indirect/post/depth/
screenshot, then was cancelled/superseded during public modpack validation. It
is NOT qualified. Combined full #1076 / 39507963173 on `27a14fca` FAILED compileJava:
seven direct accesses to the now-private MappedBuffer.buffer remained in
BufferUploaderM and RenderSystemMixin. They now call byteBuffer(), preserving
storage representation and adding closed-state checking. All seven compiler
errors were inspected; remaining .buffer uses were rescanned by type/call site.
Failed log artifact 12304163339; no #1076 native result or qualified JAR. A new
full run must qualify the corrected tree. Correction `c303cf26` is pushed;
full #1077 / run 39508596394 is queued/running at handoff, with no passed result
or qualified JAR. Last qualified baseline remains `682b0d6b` / #1074.
A green partial step is not full qualification.

Local mutation checks rejected a closed-address escape regression before native
dereference and an intentionally incorrect sort order. At that earlier checkpoint, uniform early-return leak/
double-release/partial-write mutation sensitivity had not run; the completed
follow-up evidence below supersedes that gap.

Local second 24-contract pass also succeeded after compute/admission/query/helper changes.

No maintenance work closes actual generated-world nonzero selection/pixels,
genuine portal equivalence, private resource packs, sustained particle
callbacks/reload/frame pressure or RX adoption/net CPU/GPU/FPS/p95/p99 gates.
PR #12 remains open at `3db6d0d5`; profiler work is untouched.

## Qualification follow-up — October 20

Full **#1077 / run 39508596394 completed SUCCESS**, attempt 1, on
`c303cf26f53887964e9e45a2cc156c21a55143fb`. JAR **12305230721**;
logs **12305300783**. Offline contracts, actual uniform failure and ABI tests,
Forge package/both startups, native indirect/terrain/texture/particle pixels and
synchronization, screenshot/post/depth, public Create Chronicles/Immersive
Portals and Crash Assistant passed. All three private-fixture steps skipped.
This qualifies the earlier implementation rows; their original pending/negative
entries above retain the historical sequence, superseded by this result.
#1076 remains a compile failure and #1075 remains cancelled.

New executable **`6c65e00f6212a7e2d52571bb7a279ec943efd19a`** contains:

- **M1/M5:** actual uniform failure test now asserts immediate release before
  caller close (previously a later close could mask missing exception cleanup).
  Both vec4 and mat3 variants require no destination mutation on stale update.
  The mat3 variant reproduced a real partial write: Field.Mat3f zeroed its padded
  destination before checking the closed source. Source address validation now
  precedes every destination write. This is per-field failure safety, not a new
  transactional guarantee across an arbitrary multi-field UBO update.
- **R2-3/M5:** package-scoped construction-only Calls seam, with no mutable global
  override. Production captures the device and still owns only temporary SPIR-V,
  module and failed partial pipeline. Actual factory control flow and LWJGL
  native structs run for compiler exception, module status failure, pipeline
  status failure with zero/nonzero output, thrown pipeline call and success.
  Tests check original exception identity/status context, exact handle identity,
  cleanup order/counts, zero output initialization, main/stage/layout/base fields,
  closed real SPIR-V storage and ownership transfer only on success. The added
  testComputePipelineFactoryFailure task is part of Gradle check/build. Native
  success/driver behavior is still independently required, not simulated by Calls.
- **M2/M7:** reviewed the table against RegionBatchSmokeTest producer revocation,
  generated candidates, geometry readiness and APPEND transaction cases, plus the
  publication-drain and native selector tests. Added specific gaps: generated ->
  ordinary -> generated with all producer revisions unchanged checks a rescan and
  CPU/GPU serialized geometry; clearing staged APPEND preflight checks rescan and
  recovery from a cached rejection. Existing dirty/input-vs-visible, identity/
  direction, readiness/retirement, stale publication and APPEND cases retained.
- **M3:** independent candidate/texture constants and serialization tests remain;
  #1077 actual native marker confirms 509 ordered atomic CPU/GPU pairs plus mixed
  rows, malformed/unready rejection, maximum capacity and zero tail. Shared and
  multi-dispatch texture pixel markers both observed. These supplement, rather
  than derive correctness from, the text/constant guards.

### Local validation and mutation sensitivity

24 offline contracts passed via scripts/ci/run-contracts.py --workers 4 using
Java 17 compiler-module launcher. Gradle bootstrap remains network-blocked.
Focused tests compiled the actual factory, SPIRVUtils/native-buffer wrapper,
uniform binder, mapped storage, layout fields and UBO against real LWJGL 3.3.1
(core/native/Vulkan/shaderc) and commons-lang3 3.12.0. Only unrelated renderer
globals, Minecraft Uniform and disk-cache dependencies were isolated with stubs;
no tested cleanup/storage/struct implementation was substituted. This is focused
local evidence, not Forge classpath/native-driver qualification.

Each temporary mutant was compiled and executed independently, then restored;
all failed the corresponding actual test, and the restored code passed again:

| Mutant | Observed failure |
| --- | --- |
| Remove failed-bind catch cleanup | Failed bind must release immediately, before caller close |
| Release fallback allocation twice | Double/foreign release |
| Validate closed mat3 source after memset | Partial stale update |
| Remove factory SPIR-V release | module release sequence/count: [compile, module] |
| Remove module destruction | pipeline release sequence/count missing destroyModule |
| Remove failed partial-pipeline destruction | partial release sequence/count missing destroyPipeline |

Reproduction on a full Forge checkout: run
`./gradlew testEffectUniformFailure testComputePipelineFactoryFailure`;
apply the single change in each row in a disposable checkout and rerun that task.
Never run the deliberately unsafe mutants in gameplay. No mutant is committed.

Full **#1078 / run 39509869276 PASSED**, attempt 1, on exact executable
`6c65e00f6212a7e2d52571bb7a279ec943efd19a`. Forge JAR **12304984467**;
logs **12306121182**. Actual new compute failure, vec4/mat3 uniform and ABI tests
passed in the Forge build. Both startups, new native cache transition cases,
APPEND/REPLACE selector output, texture shared/multi-dispatch pixel oracles,
particle/synchronization/post/depth/screenshot and public modpack/IP/Crash
Assistant gates passed. All three private-fixture steps skipped. This is the
current qualified maintenance executable; requested bounded implementation and
qualification is complete. There are no validation-pending code changes from
this assignment. Retained/deferred/blocked migrations are not implementations. No retained/deferred/blocked decision changed: compute
cache service prerequisites, parser license/corpus inputs, retained allocators
and deferred live-widget migration stand. No FPS, actual-world/portal, private
pack, particle or RX profitability gate closes through this work.
