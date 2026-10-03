# Performance feature implementation

The owner authorized implementation of the complete research shortlist on 2026-10-02. Track actual functionality separately from prerequisites and hardware adoption. Follow AGENTS.md section 3A; do not restart settled audits. Research: PERFORMANCE_FEATURE_OPPORTUNITIES_2026-10-02.md. GPU work retains GPU_OFFLOAD_INVESTIGATION_PLAN.md ownership and correctness contracts.

| Feature | Implementation state | Validation/adoption |
| --- | --- | --- |
| Persistent compilation caches | Implemented SPIR-V and Vulkan driver cache persistence | Local contract and full CI #947 pass, including native persistence/reload; hardware hitch measurement pending |
| Usage-driven animated textures | Implemented opt-in vanilla ticker gating and first-use refresh | Local visibility/lifetime contract; native clock/all-mip pixel oracle and full CI pending; custom raw-UV consumer audit and hardware adoption open |
| Create/Flywheel Vulkan instancing | Pending legacy API/model/shader adapter | Keep existing working fallback until qualified |
| Entity/block-entity occlusion | Pending installed-hook audit and conservative implementation | Portal views and custom bounds required |
| Adaptive chunk scheduling | Pending publication budget and worker policy | Keep generation-atomic publication and bounded native backlog |
| World pregeneration tooling | Pending exact Forge 1.20.1 deployment tooling | User-world operation requires world/deployment details |
| Separate-server tooling | Pending mod/config parity and deployment tooling | No access to desktop/server or user world in this workspace |
| Render scaling/upscaling | Pending world-target/GUI/depth/post-chain contract | Quality profile separate from canonical benchmark |
| Far-terrain LOD | Pending DH numeric-data/Vulkan rendering adapter | DH currently does not work; suppression is not LOD support |

## Compilation caches

`persistentCompilationCache` defaults true in `config/vulkanmod_settings.json`. Set false to bypass application caches. Files live under `cache/vulkanmod`; deleting that directory is safe while the game is closed.

SPIR-V cache identity includes the SHA-256 of the actual loaded shaderc library, complete effective source, filename (debug/source identity), stage, entry point, options and a versioned compiler-options schema. If native compiler identity is unavailable, compilation proceeds uncached. Source transformation happens before the existing compile API; its output is authoritative. Changed source/stage/options naturally misses. Only successful compiler outputs are persisted; bytecode ownership remains exactly one shaderc result or native allocation.

Vulkan pipeline persistence includes vendor/device, driver/API version and pipeline UUID in its key, verifies the little-endian Vulkan v1 header before initializing a driver cache, and falls back to an empty cache if saved data is rejected. It exports bounded data before cache destruction while device properties remain alive. Existing in-memory pipeline/state caching remains intact.

Cache entries carry a version, embedded key, byte length and SHA-256 checksum; writes use temporary files and atomic replacement when supported. Per-process admission caps: SPIR-V 4 MiB/entry, 64 MiB/2048 entries; driver cache 32 MiB/entry, 64 MiB/16 entries. A full cache refuses new entries rather than evicting while rendering. Separate game processes can race admission; these are application admission limits, not a global filesystem quota. Cache I/O failure warns once and uses ordinary compilation. No quality or simulation changes occur.

This slice does not yet prewarm graphics pipeline variants: persistence alone does not reconstruct render-pass/descriptor/state identities. That requires observed-variant ownership and reload-safe replay. It does not claim measured startup/hitch/FPS gains.

## Usage-driven animated textures

`animateOnlyUsedTextures` defaults false. `animationVisibilityGraceMs` defaults 500 and is clamped to 0–5000. `animationAlwaysActiveSprites` accepts resource IDs to exclude untracked/custom consumers from gating. Exact vanilla SpriteContents/Ticker without Forge custom loader metadata qualify; custom subclasses/loaders retain original behavior.

Vanilla metadata advances on every ticker invocation. Hidden interpolation pixel work and uploads are suppressed; materializing an eligible sprite uses vanilla discrete-frame/interpolation routines at the current frame/subframe without advancing the clock. Worker builds capture animated sprite references, generation-atomic publication retains them with their compiled section, and drawing a visible section marks usage. Standard UV access and Forge bulk-quad emission mark item, GUI, entity and particle consumers; usage aggregates across portal views rather than one global camera. First use refreshes dirty atlas rectangles through the existing bounded same-queue upload path, restoring texture binding and upload flags. Closing source contents retires state.

Native CI oracle compares hidden versus ungated clock progression, zero hidden staging, no clock advance on refresh, reordered/repeated frames, interpolation, alpha and each mip across 24 ticks under Vulkan synchronization validation. This is a qualified opt-in path, not proof of coverage for mods that cache raw UVs, bypass normal vertex consumers or use existing GPU-only model tables. Those consumers require explicit marking/conservative fallback before adoption; do not enable the feature by default from synthetic coverage alone.
