# VulkanMod Forge 1.20.1 — Agent Status

This file is the **living checkpoint**, not the source of truth for live repository state. Always inspect the current `forge-1.20.1` HEAD and latest CI first. If this file disagrees with Git or CI, Git/CI wins.

## Last verified green checkpoint

- Branch: `forge-1.20.1`
- Verified commit: `25527bb08c2a8b31880cbbbd4e5c879afe763a7c`
- Verified GitHub Actions run: **#269**
- Highest demonstrated milestone: **Milestone 6 — playable world**
- User RX 6900 XT result: Vulkan gameplay is playable; water rendering fix visually confirmed.

### CI coverage at this checkpoint

Run #269 passed:

- distributable Forge build / packaging verification;
- Vulkan startup under Lavapipe;
- Vulkan startup with Forge early splash disabled;
- liquid alpha/UV regression smoke;
- terrain region-cache/batching smoke;
- real vanilla `shaders/post/creeper.json` `PostChain` construction;
- Crash Assistant 1.9.7 compatibility;
- Flywheel 0.6 compatibility.

## Recent renderer/audit work

Important fixes already landed before this checkpoint include:

- deferred offscreen framebuffer destruction;
- deferred offscreen render-pass destruction;
- deferred post-effect pipeline destruction;
- graphics-pipeline reuse across compatible render passes;
- fullscreen blit MVP/state restoration;
- Vulkan depth target copies for `RenderTarget.copyDepthFrom`;
- render-target clear/filter semantics;
- MainTarget/swapchain sampled-color mapping for post effects;
- depth/stencil layout and synchronization corrections.

Do not redo these investigations without new evidence.

## Current investigation

The next post-processing gate is to upgrade the successful Creeper post-chain **construction** smoke into one synthetic **rendered** post-processing frame under Lavapipe.

Target flow:

1. recycle/reset the current renderer frame slot;
2. `Renderer.beginFrame()`;
3. execute `PostChain.process(...)`;
4. `Renderer.endFrame()`;
5. wait for completion and require a clean exit.

This should exercise actual descriptor updates, UBO uploads, sampler binding, main/offscreen layout transitions, fullscreen Vulkan draws, and presentation.

### User-machine testing

No new RX 6900 XT test is required yet. Exhaust CI/Lavapipe validation first. Ask for local testing only when the remaining question is genuinely GPU/gameplay/visual specific.

## Agent iteration rules

Use these together with `AGENTS.md`:

- Inspect live branch HEAD and latest CI before editing.
- If an executable checkout is available, run `bash scripts/ci/agent-check.sh` before pushing logically complete source/test-harness edits.
- **Batch mutually dependent edits before pushing.** Do not use GitHub Actions as a substitute for catching obvious compile/mapping mistakes when local/pre-CI validation is available.
- When working through a remote GitHub connector where each Contents API write creates a commit, prefer Git-data blobs/trees plus one ref update so a logical multi-file change creates **one commit and one CI run**.
- Do not push known-broken intermediate states merely to obtain compiler feedback.
- Branch CI uses `cancel-in-progress`; a newer push should supersede an obsolete in-progress run rather than consuming the whole smoke suite twice.
- Keep commits logically scoped, but distinguish logical scope from file count: one feature/test-harness change may correctly touch several files in one commit.
- Update this file at meaningful verified milestones, not after every tiny commit. Record the **last verified green checkpoint** so the file never pretends an unverified HEAD is green.

## Useful commands for an executable checkout

Fast source/resource regression pass:

```bash
bash scripts/ci/agent-check.sh
```

Individual Lavapipe gates after installing `xvfb`, `xauth`, and Mesa Vulkan drivers:

```bash
bash scripts/ci/vulkan-smoke.sh startup
bash scripts/ci/vulkan-smoke.sh no-splash
bash scripts/ci/vulkan-smoke.sh post-chain
bash scripts/ci/vulkan-smoke.sh crash-assistant
bash scripts/ci/vulkan-smoke.sh flywheel
```

## Handoff rule

At the end of a substantial work batch, report:

- current live HEAD;
- commits created;
- latest completed CI result;
- exact unresolved issue / next action;
- whether RX 6900 XT testing is now useful.
