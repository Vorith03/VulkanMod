# VulkanMod Forge 1.20.1 — Agent Development Protocol

## 1. Mission

The objective of this project is:

> Produce a functioning VulkanMod client mod for **Minecraft 1.20.1 / Forge 47.3.0**, suitable first for a minimal Forge installation and ultimately for **Create Chronicles: Bosses and Beyond**.

The end product is not a porting report, architectural proposal, or theoretical compatibility assessment.

The end product is a **testable Forge JAR**.

---

# 2. Immutable Project Constraints

Unless the user explicitly changes them, all agents must treat the following as fixed:

- Minecraft: **1.20.1**
- Forge: **47.3.0**
- Java: **17**
- Repository: `Vorith03/VulkanMod`
- Development branch: `forge-1.20.1`
- Known clean upstream 1.20.1 base:
  `979905a9197e69143b7ddaadf594f4d8f20c9dd1`
- Target environment includes:
  - Arch Linux
  - AMD GPU
  - Prism Launcher
  - Create Chronicles: Bosses and Beyond

Do **not** casually upgrade Minecraft, Forge, Java, mappings, or other foundational components as a way of making errors disappear.

Compatibility with the user's actual 1.20.1 modpack is the constraint.

---

# 3. Source-of-Truth Hierarchy

When information conflicts, use this priority order:

1. **Current contents of `forge-1.20.1`**
2. Successful/failed build output from the current branch
3. Runtime logs from the user's actual test environment
4. This project handoff/protocol
5. The known original Minecraft 1.20.1 VulkanMod source
6. Later VulkanMod versions
7. General assumptions or documentation

The current branch is authoritative for what has already been implemented.

Do not restore old code merely because it differs from upstream Fabric.

---

# 4. Never Redo Settled Archaeology Without Evidence

The following have already been established and should not repeatedly consume development time:

- `979905a...` is the deliberately selected last known clean Minecraft 1.20.1 source base.
- The project actually used **official Mojang mappings**, despite stale Yarn-related configuration.
- Forge **47.3.0** is the target required by the user's modpack.
- VulkanMod's renderer is primarily implemented through **Sponge Mixins** rather than Fabric APIs.
- Sponge Mixin usage alone is not a reason a Forge port cannot work.
- The Git tag named `0.5.2` is **not** the desired historical Minecraft 1.20.1 source.
- The existing renderer should not be rewritten simply because the code is unusual.
- The Forge early loading window may conflict with VulkanMod's window interception.

Reinvestigate one of these conclusions only when new repository, compiler, CI, or runtime evidence directly contradicts it.

---

# 5. Preserve the Renderer Until Proven Necessary

Default strategy:

**Port the loader around the renderer.**

Do not:

- replace the renderer wholesale;
- redesign Vulkan abstractions;
- rewrite working rendering systems;
- “modernize” large portions of code;
- transplant large pieces from modern VulkanMod versions;
- replace Mixins merely because Forge has a different mod loader.

Prefer:

- import replacement;
- lifecycle adaptation;
- metadata changes;
- dependency corrections;
- Forge-specific compatibility glue;
- narrowly targeted Mixins;
- Access Transformers only where actually necessary.

A renderer rewrite requires evidence that the existing architecture cannot function under Forge.

---

# 6. Evidence-Driven Development

Agents must not guess when build or runtime evidence is available.

The normal loop is:

1. Inspect current branch state.
2. Run or inspect the build.
3. Capture the complete failure.
4. Identify the smallest underlying cause.
5. Make a logically grouped fix.
6. Build again.
7. Repeat.

Compiler errors are the porting checklist.

Runtime crashes are the compatibility checklist.

Warnings should be investigated according to relevance but must not distract from blocking failures.

---

# 7. Failure Classification

When a build or runtime failure appears, classify it before making broad changes.

Use these categories:

- **Fabric-only API/import**
- **Loader lifecycle**
- **Mappings/member access**
- **Mixin application**
- **Mixin target mismatch**
- **Forge-patched Minecraft behavior/signature**
- **Access Transformer/accessibility**
- **ForgeGradle configuration**
- **Dependency resolution**
- **Dependency shading/packaging**
- **LWJGL native loading**
- **Vulkan initialization**
- **Window/GLFW initialization**
- **Renderer compatibility**
- **Third-party mod conflict**
- **Unrelated environmental failure**

This classification should guide the scope of the fix.

---

# 8. Smallest-Fix Rule

For each failure, make the smallest change that correctly resolves the underlying problem.

Avoid changing five systems in response to one error.

A good patch should answer:

> What concrete evidence required this change?

If the answer is merely:

> This looked cleaner.

then the change probably does not belong in the Forge port yet.

---

# 9. Access Transformer Protocol

The current build configuration references:

`src/main/resources/META-INF/accesstransformer.cfg`

but that file may not exist.

Do not blindly translate the old Fabric Access Widener.

For each old widened target:

1. Search current Forge source for actual direct Java access.
2. Determine whether a Mixin `@Shadow`, accessor, invoker, or other mechanism already handles it.
3. Add an Access Transformer entry only if normal compiled Java access genuinely requires it.
4. If no entries are necessary, remove the `accessTransformer` declaration entirely.

Every Access Transformer entry should have an identifiable consumer.

---

# 10. Mixin Protocol

Mixins are a core architectural dependency of VulkanMod and should be preserved.

When dealing with Mixins:

- preserve `vulkanmod.mixins.json` unless evidence requires changing it;
- preserve `MixinPlugin` behavior unless a specific incompatibility exists;
- verify Forge discovers the mixin configuration correctly;
- verify refmap behavior rather than assuming Fabric-specific behavior;
- diagnose failed targets individually;
- account for Forge-patched Minecraft where appropriate.

Do not disable groups of Mixins simply to achieve a successful launch unless those Mixins are conclusively incompatible.

A launch where VulkanMod's renderer is silently not active is not success.

---

# 11. Initialization/Lifecycle Protocol

VulkanMod configuration initialization has ordering requirements.

In particular:

- VulkanMod settings must be available early enough for Mixins such as `WindowMixin`.
- `VideoResolution.init()` must occur before consumers expect it.
- Forge lifecycle conventions must not override these requirements.

Do not mechanically move all initialization into a later Forge client event.

Initialization changes must preserve the invariant:

> Anything required by an early Mixin exists before that Mixin uses it.

---

# 12. Window Initialization Is a First-Class Compatibility Area

VulkanMod expects Minecraft's GLFW window to be created without an OpenGL client context:

`GLFW_CLIENT_API = GLFW_NO_API`

Forge 1.20.1 may create an early OpenGL loading window before normal Minecraft window creation.

The currently identified workaround is:

`config/fml.toml`

`earlyWindowControl = false`

Agents must:

- preserve awareness of this requirement;
- verify whether it remains necessary;
- distinguish Forge's early window from Minecraft's normal game window;
- document this clearly for test builds.

Do not declare Vulkan initialization broken until early-window behavior has been accounted for.

---

# 13. Dependency Discipline

Minecraft already provides standard LWJGL libraries.

VulkanMod requires additional modules including:

- `lwjgl-vulkan`
- `lwjgl-vma`
- `lwjgl-shaderc`

plus required platform natives.

Agents should:

- avoid bundling duplicate LWJGL core classes;
- ensure VMA and shaderc natives are present where required;
- verify Linux support first because that is the user's development platform;
- avoid solving native loading issues by blindly shading the entire LWJGL ecosystem.

Packaging must eventually be tested from the **actual produced runtime JAR**, not merely through Gradle's development classpath.

---

# 14. Git Discipline

All development work goes to:

`forge-1.20.1`

Do not modify the default branch.

Before editing:

- inspect the current branch HEAD;
- inspect recent commits;
- verify another agent has not already solved the issue.

Commits should represent logical milestones.

Examples:

- `Fix ForgeGradle access transformer configuration`
- `Replace remaining Fabric loader APIs`
- `Fix Forge mixin configuration`
- `Package Vulkan LWJGL modules`
- `Adapt Window initialization for Forge`

Avoid giant commits containing unrelated experimentation.

---

# 15. Do Not Erase Working Progress

Before replacing or reverting code, determine why the existing Forge branch differs from upstream.

Assume differences may be intentional.

Particular care should be taken around:

- `Initializer.java`
- configuration initialization;
- Forge metadata;
- Gradle configuration;
- Mixin configuration;
- window initialization;
- dependency packaging.

Do not restore the Fabric implementation wholesale.

---

# 16. GitHub Actions Protocol

If CI is available:

1. Inspect the latest run for `forge-1.20.1`.
2. Read the actual failed step and relevant build log.
3. Use those failures as the immediate task list.
4. Fix locally/in-repository.
5. Commit and push.
6. Inspect the next CI result.

Do not predict hypothetical compiler failures while an actual CI failure is available.

A green CI build is a milestone, not final success.

---

# 17. Build Success Criteria

“Build works” means at minimum:

- ForgeGradle configuration succeeds;
- Java compilation succeeds;
- resources process correctly;
- Mixins are packaged;
- dependencies are packaged as intended;
- Forge reobfuscation completes;
- the expected runtime JAR exists.

Do not confuse:

- IDE compilation,
- `compileJava`,
- Gradle development runs,
- or production JAR generation.

The user ultimately needs the Forge runtime artifact.

---

# 18. Runtime Testing Stages

Testing should move through increasingly complicated environments.

## Stage A — Minimal Forge

Test:

- Minecraft 1.20.1
- Forge 47.3.0
- VulkanMod
- required Forge configuration

Goals:

- Forge recognizes the mod;
- Mixins load;
- window creation succeeds;
- Vulkan initializes;
- title screen renders;
- world loads;
- basic rendering works.

## Stage B — Basic gameplay

Validate:

- world rendering;
- entities;
- GUI;
- textures;
- chunk loading;
- resize/fullscreen behavior where relevant;
- resource reload;
- closing/reopening worlds.

## Stage C — Create Chronicles

Only after the minimal environment works should the full modpack become the primary target.

---

# 19. Modpack Compatibility Protocol

Create Chronicles is large and contains rendering-related mods.

Do not assume VulkanMod must coexist with every renderer replacement.

Potential conflicts may include:

- Embeddium
- Rubidium
- Oculus
- Sodium-derived systems
- shader/render pipeline replacements
- renderer optimization mods

For conflicts:

1. Confirm the mod actually conflicts.
2. Identify the mechanism.
3. Determine whether interoperability is realistic.
4. If not, mark the smallest incompatible set.
5. Preserve as much of the modpack as possible.

Removing one redundant renderer replacement is preferable to deforming VulkanMod around an incompatible rendering architecture.

---

# 20. Do Not Hide Failures

Never “fix” a failure by broadly swallowing exceptions, disabling initialization, or skipping major rendering functionality solely to reach the main menu.

Temporary diagnostic guards are acceptable only when clearly identified and removed or justified afterward.

Success means VulkanMod is actually operating.

---

# 21. Temporary Debugging Changes

Debugging modifications should be:

- narrow;
- obvious;
- reversible;
- documented in the commit or working notes.

Examples:

- additional initialization logging;
- Mixin application logging;
- Vulkan device-selection logging;
- dependency/native-loading diagnostics.

Remove excessive temporary diagnostics once the issue is understood unless they are useful permanently.

---

# 22. Later VulkanMod Code

Later VulkanMod versions are reference material, not the baseline.

Newer code may be mined selectively for:

- bug fixes;
- Vulkan compatibility fixes;
- AMD fixes;
- performance improvements;
- synchronization corrections;
- renderer robustness;
- mod compatibility ideas.

Do not transplant newer architecture until the native 1.20.1 Forge baseline works.

When backporting something, identify exactly what problem it solves.

---

# 23. Optimization Comes After Correctness

The project may eventually explore performance improvements, but the order is:

1. Forge structural port
2. successful build
3. minimal runtime
4. correct rendering
5. Create Chronicles compatibility
6. stability
7. profiling
8. optimization

Do not optimize code paths that have not yet been demonstrated to function correctly.

---

# 24. User-Machine Escalation Rule

Repository work should be performed by the agent whenever tooling permits.

Ask the user to perform an action only when it genuinely requires their machine, such as:

- launching Minecraft;
- testing GPU-specific Vulkan behavior;
- reproducing a runtime problem unavailable in CI;
- running a local build when no usable execution environment exists;
- collecting logs from the actual modpack.

When user action is needed, provide:

1. the exact command or action;
2. what output/log is needed;
3. where that output can be found.

Do not dump an entire development workflow onto the user when the agent can perform it itself.

---

# 25. Logs Are Evidence

When the user supplies a log:

- read the entire relevant failure region;
- identify the earliest meaningful failure;
- distinguish primary errors from cascading errors;
- reference exact exception classes, Mixin names, or modules when discussing the cause.

Do not diagnose based only on the final line of a stack trace.

---

# 26. Preserve Reproducibility

Every important project state should be reproducible from Git.

Do not rely on unexplained local modifications.

Where runtime configuration outside the repository is necessary—such as:

`earlyWindowControl = false`

document it with the test artifact.

If practical, include project documentation explaining required Forge configuration.

---

# 27. Definition of Milestones

## Milestone 1 — Structurally valid Forge project

Gradle configuration and project layout are valid.

## Milestone 2 — Compilation

`./gradlew build` progresses through Java compilation without source errors.

## Milestone 3 — Production JAR

ForgeGradle successfully produces the proper reobfuscated runtime JAR.

## Milestone 4 — Minimal launch

Forge 47.3.0 launches Minecraft 1.20.1 with VulkanMod installed.

## Milestone 5 — Vulkan rendering

The game actually renders through VulkanMod rather than falling back or silently disabling functionality.

## Milestone 6 — Playable world

A normal world can be loaded and played without major renderer failures.

## Milestone 7 — Create Chronicles launch

The actual modpack launches with VulkanMod.

## Milestone 8 — Compatibility pass

Renderer-mod conflicts and significant gameplay rendering failures are resolved or explicitly characterized.

## Milestone 9 — Optimization

Performance improvements can be investigated against a known-working baseline.

---

# 28. Required Agent Handoff

An agent ending its work must leave enough information that the next agent does not repeat the investigation.

Every handoff should contain:

### Repository state

- branch;
- HEAD commit;
- commits created during the session.

### Current milestone

State the highest completed milestone.

### Changes made

Explain meaningful code/configuration changes and why they were necessary.

### Evidence

Include:

- relevant build result;
- CI result;
- runtime result;
- important error excerpts or identifiers.

### Current blocker

State the exact next unresolved problem.

### Next recommended action

Give the next agent a concrete starting task.

### User action required

If applicable, give exact commands/test instructions and specify what output needs to come back.

---

# 29. Things an Agent Must Not Claim Without Verification

Do not claim that:

- the project builds;
- VulkanMod is loading;
- Vulkan rendering is active;
- the correct JAR was produced;
- Mixins applied successfully;
- Forge compatibility is complete;
- a mod conflicts;
- a dependency is correctly packaged;
- performance improved;

unless corresponding evidence has actually been observed.

Clearly distinguish:

- confirmed;
- strongly suspected;
- hypothesis;
- untested.

---

# 30. Decision Rule for Uncertainty

When uncertain, prefer the action that:

1. preserves existing working code;
2. produces new diagnostic evidence;
3. is easy to reverse;
4. changes the smallest possible surface area;
5. moves the project toward a testable JAR.

---

# 31. Core Development Principle

The guiding principle for this project is:

> **Evidence first, smallest fix second, test again immediately.**

The renderer is presumed viable until evidence proves otherwise.

Forge adaptation should remain as mechanical as possible.

The project's success criterion is always an actual functioning VulkanMod Forge 1.20.1 build—not merely a cleaner codebase or a convincing explanation.
