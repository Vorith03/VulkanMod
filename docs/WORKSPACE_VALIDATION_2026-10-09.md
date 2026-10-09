# Local validation recovery — 2026-10-09

Continuation of the prebenchmark audit, with live baseline `c5f9d66` and last
qualified public CI #1005. The audit remains subject to the full Forge build and
native renderer/compatibility gates.

## Executable follow-up

`8f4b2fd262aaa7465f339b2c6829feb2e56e31e1` fixes deletion of a bound incomplete
legacy framebuffer. Assembly deliberately ends the unrelated previous pass, but
such an FBO has no native backing. The old deletion path cleared the synthetic
binding and returned from `retireBacking()` without restoring framebuffer zero.
A subsequent draw therefore encountered no active render pass. Deletion now
restores the main pass while a frame is recording, including this no-backing
case. Unbound deletion and deletion outside a frame remain inert.

The production-class CPU regression fails against the previous implementation
and passes after the fix. Cases cover a newly empty FBO, a previously complete
FBO whose attachment was detached, unbound deletion, and deletion outside a frame.
The existing transformed/native pixel oracle now checks framebuffer-zero/pass
restoration after incomplete deletion. `gradlew` now has its executable bit,
matching the documented local `agent-check.sh` invocation; CI already supplied
this permission explicitly.

`90d9c004c671f7c7f6ebb6de1d47bd532b3abef6` requires the legacy framebuffer
oracle's completion marker in the existing screenshot gate, so a future missing
oracle invocation cannot silently retain a green screenshot gate.

`cbb198364d1ddb31f4bd3f65b80fca1073851b74` fixes a real source compilation
failure in the original audit: `RenderSection` is in `render.chunk`, while
`CompiledSection.transparencyState` is package-private in `render.chunk.build`.
The isolated audit harness placed both types in one enclosing class and did not
expose that access violation. `CompiledSection.hasTransparencyState()` now
provides the narrow admission predicate without exposing mutable sort state.
The existing audit harness extracts the same production accessor. Its admission,
quota and cancellation checks pass; real Forge compilation remains the authority
for package/type/Mixin correctness.

## Workspace build setup

The wrapper's earlier `Network is unreachable` was not proof that all dependency
retrieval was unavailable. Command-line HTTPS used the environment's live proxy;
the inherited `GRADLE_OPTS` instead selected an unavailable `browser-proxy:8889`.
A complete official Eclipse Temurin **Java 17** toolchain was downloaded and
verified against its published SHA-256. A scratch Gradle **8.1.1** distribution,
the same version pinned by the repository, was used. The environment's trusted
CA certificates were imported into this temporary JDK's truststore; TLS
verification remained enabled.

For recovery in a similar workspace:

1. Use a full Java 17 JDK (the base runtime may lack `javac`/`javap` executables).
2. Derive Java HTTP/HTTPS proxy host and port from the current `HTTPS_PROXY`.
   Replace the stale inherited `GRADLE_OPTS`; merely setting `JAVA_OPTS` before it
   leaves the later conflicting Gradle properties effective.
3. Pass the same proxy properties to the Gradle invocation and use a persistent
   scratch `GRADLE_USER_HOME` for dependency reuse. Do not hard-code a proxy port:
   the environment can assign a different port per tool process.
4. Run `classes testRegionBatchLayout testSectionVoxelSnapshot --stacktrace`,
   then the production `build` gate. Keep network setup outside repository build
   configuration and preserve Minecraft 1.20.1 / Forge 47.3.0 / Java 17.

Focused audit/FBO, terrain population, HUD, upload-GPU ownership, animation numeric
and visibility, attribution, particles, chunk budget, GPU-HUD hooks, compilation
cache, pipeline variants, deployment, render scale, instance input, Flywheel
ownership and smoke-fixture isolation checks passed locally. These are not native
renderer evidence.

## Generated Minecraft artifact recovery

The first cold Gradle attempt failed in MCP `rename`, before VulkanMod source
compilation: `ZipException: zip END header not found` on MCP `merge/output.jar`.
The downloaded client and extracted/stripped server archives were valid. The
merger was run once independently with the identical pinned tool/arguments and
inputs, producing a valid 7,436-entry archive whose CRCs all checked. Only the
corrupt generated merge artifact was atomically replaced. A subsequent Gradle
attempt passed MCP rename and continued into Forge binary patch preparation.
All 96 then-downloaded Forge/Minecraft dependency JARs had valid ZIP directories.
The cause of the initially truncated generated output is not established; do not
classify this as a VulkanMod compiler regression or weaken production checks.
MCP `mergeMappings/output.jar` is intentionally TSRG text despite its filename;
it must not be treated as another damaged ZIP.

## Confirmed local build gates

For executable `cbb1983`, real Java/Forge main and test compilation, resource and
Mixin/refmap packaging, production JarJar reobfuscation and `verifyDistribution`
passed. The verifier confirmed the distributable and nested
`org.lwjgl.glfw -> org.lwjgl.vulkan` module linkage. All 21 JavaExec regressions
attached to Gradle `check` completed across the initial build and an explicit
remaining-gates run. That latter run ended **BUILD SUCCESSFUL in 25s** (23 tasks,
18 executed / 5 up-to-date), including distribution verification, matrix/zero
allocation, memory pressure, pipeline keys, process ownership, region layout,
resource generations, voxel snapshots, SPIR-V lifetime, samplers, packed uploads,
VMA mapping and transactional image creation. The earlier full `build` process
returned without its final summary after the legacy-texture-unit test; only its
observed completed gates are counted, and the remaining gates were run explicitly.
Do not equate this cumulative local build evidence with a full native renderer
suite or a new public CI run.

The generated artifact is
`build/libs/VulkanMod_Forge_1.20.1-0.3.2-forge.2-all.jar`. This is an unstamped local
build, not a numbered CI artifact or an RX-qualified release. No user benchmark
is requested until the external native gates pass.

## Native runtime boundary

Software Vulkan/Xvfb dependencies were downloaded as Ubuntu packages and
extracted into scratch without installing/upgrading system packages. Lavapipe's
shared-library dependencies resolved, but Xvfb could not establish display
listening sockets. Both its ordinary local-socket route and a bounded alternative
TCP route failed before Minecraft launch. Do not repeat this bootstrap as if a
native renderer result had been obtained. Run the existing full suite in a
runtime-capable environment.

Exact-SHA Actions queries after both follow-up publications returned zero runs.
The separate Contents API update did not start a run either. Authenticated shell
push was unavailable (`could not read Username`, terminal prompts disabled).
The connector has older-run retries but no new-run dispatch operation; retrying
#1005 still validates #1005's old SHA. Repository default branch is `dev`, so a
branch-only manual-dispatch addition is not an established solution. Default
branch, repository settings, secrets and billing were not changed. No new
benchmark or accelerated default is qualified by these local checks.
