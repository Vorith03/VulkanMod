# FTB Library / FTB Chunks map compatibility

## Problem

FTB Library 2001.x implements its GUI scissor stack with direct LWJGL OpenGL calls. Under VulkanMod there is no current OpenGL context, so opening/drawing the FTB Chunks region map can fail in `GuiHelper.pushScissor()` at `GL11.glEnable(GL_SCISSOR_TEST)`.

The same scissor path also calls `GL11.glScissor(...)` when applying/restoring a rectangle and `GL11.glDisable(GL_SCISSOR_TEST)` when the stack becomes empty. Handling only the first call would therefore expose the next raw OpenGL call immediately.

## Compatibility fix

VulkanMod applies two optional `@Pseudo` mixins when FTB Library is present:

- `FTBLibraryGuiHelperMixin` suppresses the raw `glEnable` in `pushScissor()` and routes the final `glDisable` in `popScissor()` to `RenderSystem.disableScissor()`.
- `FTBLibraryScissorMixin` routes FTB Library's already-computed framebuffer rectangle from `GuiHelper.Scissor.scissor()` to `RenderSystem.enableScissor(...)`.

FTB Library keeps ownership of its existing nested scissor stack, crop/intersection behavior, and coordinate conversion. VulkanMod only replaces the three OpenGL state calls at the boundary.

Because these mixins ship with VulkanMod and are `@Pseudo`, FTB Library remains optional. Normal FTB behavior is unchanged when VulkanMod is not the active renderer/mod.

## Adjacent-path audit

`RegionMapPanel` in FTB Chunks does not issue raw LWJGL/OpenGL calls itself, and a repository search found no `org.lwjgl.opengl` / `GL11` use in FTB Chunks. The immediate crash surface is FTB Library's scissor helper described above.

## Scope

This compatibility patch intentionally does not change terrain rendering, chunk/voxel GPU work, indirect commands, `RegionDrawBatch`, or any `render/chunk/voxel/Gpu*` implementation.
