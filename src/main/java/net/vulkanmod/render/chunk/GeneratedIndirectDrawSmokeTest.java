package net.vulkanmod.render.chunk;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.vulkanmod.Initializer;
import net.vulkanmod.render.chunk.voxel.GpuTerrainSectionMesherSmokeTest;
import net.vulkanmod.render.vertex.CustomVertexFormat;
import net.vulkanmod.render.vertex.TerrainRenderType;
import net.vulkanmod.vulkan.Device;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.Synchronization;
import net.vulkanmod.vulkan.VRenderSystem;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import net.vulkanmod.vulkan.shader.Pipeline;
import net.vulkanmod.vulkan.shader.PipelineState;
import net.vulkanmod.vulkan.texture.ScreenshotReadback;
import org.joml.Matrix4f;
import org.joml.Vector3i;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkBufferMemoryBarrier;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.lwjgl.vulkan.VK10.*;

/** Independent seeded and production-mesher indirect pixels; no loaded ClientLevel or portal claim. */
public final class GeneratedIndirectDrawSmokeTest {
    private GeneratedIndirectDrawSmokeTest() {}

    public static void verify(Minecraft minecraft) throws Exception {
        verify(minecraft, false);
        verify(minecraft, true);
    }

    private static void verify(Minecraft minecraft, boolean meshed) throws Exception {
        // No extra production hook or configuration defaults: only the existing screenshot fixture calls this.
        if(!Boolean.getBoolean("vulkanmod.ciScreenshotSmoke")) throw new IllegalStateException("CI only");
        Renderer renderer = Renderer.getInstance();
        renderer.resetBuffers();
        // At the between-frame entry, the upload manager still names the previous
        // slot until beginFrame. Retire first, then enter the normal upload domain.
        AreaUploadManager.INSTANCE.updateFrame(Renderer.getCurrentFrame());
        ChunkArea area = new ChunkArea(93, new Vector3i(0, 0, 0));
        DrawBuffers buffers = area.getDrawBuffers();
        RegionDrawBatch batch = new RegionDrawBatch();
        RenderSection section = new RenderSection(0, 0, 0, 0);
        TerrainRenderType type = TerrainRenderType.CUTOUT_MIPPED;
        require(!meshed || RenderSection.gpuTerrainOutputLayer() == type,
                "Mesher raster fixture requires the combined unique opaque layer");
        TextureTarget target = null;
        GraphicsPipeline pipeline = null;
        boolean oldCull = VRenderSystem.cull, oldDepth = VRenderSystem.depthTest;
        boolean oldMask = VRenderSystem.depthMask, oldStencil = VRenderSystem.stencilTest;
        boolean oldBlend = PipelineState.blendInfo.enabled;
        int oldColorMask = VRenderSystem.colorMask;
        try {
            VRenderSystem.cull = VRenderSystem.depthTest = VRenderSystem.depthMask = VRenderSystem.stencilTest = false;
            PipelineState.blendInfo.enabled = false;
            VRenderSystem.colorMask = 15;
            // Reuse the production format and push-constant layout. Independent unlit shaders isolate
            // command selection, vertex fetch and draw ordering from atlas/lightmap/model qualification.
            Pipeline.Builder builder = new Pipeline.Builder(CustomVertexFormat.COMPRESSED_TERRAIN,
                    "basic/terrain_region/terrain_region");
            builder.parseBindingsJSON();
            builder.setUniforms(new ArrayList<>(), List.of());
            builder.compileShaders("""
                    #version 450
                    layout(location=0) in ivec3 Position;
                    layout(location=1) in vec4 Color;
                    layout(push_constant) uniform PC { vec3 ChunkOffset; };
                    layout(location=0) out vec4 color;
                    void main() {
                        gl_Position = vec4(POSITION_EXPRESSION + ChunkOffset.xy, 0.5, 1.0);
                        color = Color;
                    }
                    """.replace("POSITION_EXPRESSION", meshed
                            ? "vec2(Position.xy) / 1900.0 * vec2(0.8, 1.6) + vec2(0.1, -0.8)"
                            : "vec2(Position.xy) / 1000.0"), """
                    #version 450
                    layout(location=0) in vec4 color;
                    layout(location=0) out vec4 result;
                    void main() { result = color; }
                    """);
            pipeline = builder.createGraphicsPipeline();
            target = new TextureTarget(64, 32, true, Minecraft.ON_OSX);
            buffers.allocateBuffers();
            section.setChunkArea(area);
            area.registerSection(section, 0, 0, 0);
            // Exercise the legitimate wrapped traversal stamp that used to collide with an
            // "uninitialized" sentinel. addSection establishes its validity.
            section.setLastFrame((short)-1);
            area.addSection(section);
            require(area.isGraphVisible(section), "Wrapped traversal lost graph visibility");
            VFrustum frustum = new VFrustum();
            frustum.calculateFrustum(new Matrix4f(), new Matrix4f());
            frustum.offsetToFullyIncludeCameraCube(8);
            long generation = section.getVoxelGeneration();
            var parameters = section.getDrawParameters(type);
            try(MemoryStack stack = MemoryStack.stackPush()) {
                ByteBuffer cpu = meshed ? mesherCpuQuad(stack) : quad(stack, -900, 900, 0xff0000ff); // red full rectangle
                buffers.vertexBuffer.upload(cpu, parameters.vertexBufferSegment);
                AreaUploadManager.INSTANCE.submitUploads();
                new DrawBuffers.ParametersUpdate(parameters, 6, 0,
                        parameters.vertexBufferSegment.getOffset() / 20).setDrawParameters();
            }
            RenderSection.GpuTerrainPreflight mesherPlan = null;
            if(meshed) {
                section.setGpuTerrainCpuMeshComplete(generation, false);
                var plans = new RenderSection.GpuTerrainPreflight[1];
                GpuTerrainSectionMesherSmokeTest.publishIsolatedCubeInputs(area, generation, snapshot -> {
                    var plan = RenderSection.qualifyGpuTerrain(snapshot);
                    require(plan != null && plan.faceCount() == 6, "Mesher raster preflight rejected six-face cube");
                    plans[0] = plan;
                    section.stageGpuTerrainPreflight(plan, generation, true);
                });
                mesherPlan = plans[0];
                require(area.getVoxels(0, 0, 0) == null,
                        "Production mesher dispatch must return consumed CPU staging capacity");
                require(!area.getGpuVoxelResidency(0, 0, 0).valid()
                                && !area.getGpuSparseLightingResidency(0, 0, 0).valid(),
                        "Consumed GPU input slices must retire without revoking generated output");
                // Fixture-only wait: production completion remains non-blocking. No vertex readback,
                // CPU vertex construction, payload patch or reupload is permitted on this branch.
                Vulkan.waitIdle();
                GpuTerrainSectionMesherBridge.pollCompletions();
                var resident = area.getGpuTerrainOutputResidency(0, 0, 0, type);
                require(resident != null && resident.valid() && resident.generation() == generation
                                && resident.faceCount() == 6 && resident.byteOffset() > 0,
                        "Production bridge did not publish exact nonzero-offset mesher output");
                require(!section.gpuTerrainCpuMeshComplete(), "Mesher silently retained complete CPU geometry");
            } else {
                var reservation = area.reserveGpuTerrainOutput(0, 0, 0, type, generation, 1);
                require(reservation != null, "Resident output reservation rejected");
                var destination = area.getGpuTerrainOutputTarget(reservation);
                require(destination != null && destination.byteOffset() > 0, "Expected nonzero resident vertex offset");
                // Seed known resident bytes without calling the mesher's numerical oracle. Its exact packed
                // output is separately tested after model baking; this fixture closes the missing draw edge.
                try(MemoryStack stack = MemoryStack.stackPush()) {
                    var command = Device.getGraphicsQueue().beginCommands();
                    vkCmdUpdateBuffer(command.getHandle(), destination.bufferId(), destination.byteOffset(),
                            quad(stack, 100, 900, 0xff00ff00)); // green right rectangle
                    var barrier = VkBufferMemoryBarrier.calloc(1, stack).sType$Default()
                            .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT).dstAccessMask(VK_ACCESS_VERTEX_ATTRIBUTE_READ_BIT)
                            .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED).dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                            .buffer(destination.bufferId()).offset(destination.byteOffset()).size(80);
                    vkCmdPipelineBarrier(command.getHandle(), VK_PIPELINE_STAGE_TRANSFER_BIT,
                            VK_PIPELINE_STAGE_VERTEX_INPUT_BIT, 0, null, barrier, null);
                    Device.getGraphicsQueue().submitCommands(command);
                    Synchronization.waitFence(command.getFence());
                    Synchronization.INSTANCE.retireSameQueueCommandBufferAfterFence(command);
                }
                require(area.publishGpuTerrainOutput(reservation, 1, false), "Resident output publication failed");
            }
            section.setGpuTerrainCpuMeshComplete(generation, false);

            // First draw must publish through the real queue and consume immediately;
            // changing views cannot depend on a later frame to make candidates ready.
            int unchangedFrames = Renderer.getFramesNum() - 1;
            int lastGenerationDispatchSlot = -1;
            for(int iteration = 0; iteration < 7 + unchangedFrames; iteration++) {
                int pass = iteration <= 1 ? iteration
                        : iteration <= 1 + unchangedFrames ? 1 : iteration - unchangedFrames;
                if(pass == 2) {
                    section.stageGpuTerrainPreflight(new RenderSection.GpuTerrainPreflight(
                            meshed ? mesherPlan.modelGeneration() : 1L, meshed ? mesherPlan.faceCount() : 1,
                            GpuTerrainDrawHandoff.Ownership.APPEND), generation, true);
                    if(meshed) require(section.publishGpuTerrainDrawHandoff(generation,
                            GpuTerrainDrawHandoff.Ownership.APPEND), "Meshed APPEND handoff rejected");
                }
                if(pass == 4) new DrawBuffers.ParametersUpdate(parameters, 0, 0, 0).setDrawParameters();
                if(pass == 5) {
                    area.invalidateGpuTerrainOutput(0, 0, 0, generation + 1);
                    section.setGpuTerrainCpuMeshComplete(generation, true);
                    new DrawBuffers.ParametersUpdate(parameters, 6, 0,
                            parameters.vertexBufferSegment.getOffset() / 20).setDrawParameters();
                    area.resetQueue();
                    area.sectionQueue.add(section); // deliberately untracked writer: force complete CPU fallback
                }
                if(pass == 6) {
                    area.resetQueue();
                    area.addSection(section); // fresh producer proof permits ordinary GPU command ownership again
                }
                renderer.resetBuffers();
                renderer.beginFrame();
                if(iteration == 1) lastGenerationDispatchSlot = Renderer.getCurrentFrame();
                if(pass == 2) require(Renderer.getCurrentFrame() == lastGenerationDispatchSlot,
                        "Generation turnover must deliberately revisit the retired selector slot");
                // Publish/submit through the actual area queue before the draw resolves residency.
                if(pass > 0) {
                    batch.publishLiveCandidates(buffers, area, type, true, true, true);
                    AreaUploadManager.INSTANCE.submitUploads();
                }
                target.setClearColor(0, 0, 0, 1);
                target.clear(Minecraft.ON_OSX);
                target.bindWrite(true);
                Renderer.resetScissor();
                Renderer.setDepthBias(0, 0);
                renderer.bindGraphicsPipeline(pipeline);
                renderer.uploadAndBindUBOs(pipeline);
                Renderer.getDrawer().getQuadsIndexBuffer().checkCapacity(meshed ? 24 : 4);
                Renderer.getDrawer().bindAutoIndexBuffer(Renderer.getCommandBuffer(), 7);
                int uploads = RegionBatchStats.commandUploads;
                int accepted = RegionBatchStats.gpuCommandOwnerReasons[0];
                batch.draw(buffers, area, pipeline, RenderType.cutoutMipped(), 0, 0, 0);
                if(pass != 5) {
                    require(RegionBatchStats.gpuCommandOwnerReasons[0] == accepted + 1,
                            "Combined configuration did not admit production GPU command ownership");
                    require(RegionBatchStats.commandUploads == uploads,
                            "Accepted GPU draw built/uploaded duplicate CPU commands");
                }
                if(pass == 5) {
                    require(RegionBatchStats.gpuCommandOwnerReasons[0] == accepted,
                            "Untracked queue mutation did not reject GPU ownership");
                    require(RegionBatchStats.commandUploads == uploads + 1,
                            "Rejected GPU ownership did not rebuild complete CPU commands");
                }
                AreaUploadManager.INSTANCE.submitUploads();
                var capture = ScreenshotReadback.request(target);
                target.unbindWrite();
                minecraft.getMainRenderTarget().bindWrite(true);
                renderer.endFrame();
                try(NativeImage image = capture.get(10, TimeUnit.SECONDS)) {
                    if(pass > 0) {
                        expect(image, 16, 16, pass == 1 || pass == 4 ? 0xff000000 : 0xff0000ff);
                        expect(image, 48, 16, pass >= 5 ? 0xff0000ff : meshed ? 0xffffffff : 0xff00ff00);
                        expect(image, 1, 1, 0xff000000);
                    }
                }
            }
            Initializer.LOGGER.info("VULKANMOD_GPU_SELECTOR_RETIRED_SLOT_REUSE_OK: unchanged cached frames span a complete slot ring; changed generation re-adopted on the same numeric slot without CPU command upload; meshed={}", meshed);
            if(meshed) Initializer.LOGGER.info("VULKANMOD_GPU_MESHED_INDIRECT_PIXELS_OK: baked model qualification, sparse input upload, production bridge async compute/completion/handoff, six resident faces without vertex readback/reupload, native selector and production indirect pixels, REPLACE/APPEND/recovery CPU command bypass; synthetic world inputs and unlit projection, not loaded-world/portal adoption");
            else Initializer.LOGGER.info("VULKANMOD_GPU_GENERATED_INDIRECT_PIXELS_OK: production RegionDrawBatch.draw, real queued candidates/selector/indirect fetch, nonzero resident offset, REPLACE, atomic CPU/GPU APPEND order, zero-exception APPEND, frame-slot reuse, rejected-proof complete CPU recovery and re-adoption, no duplicate accepted CPU command upload; seeded vertices, no loaded world/mesher/portal claim");
        } finally {
            Vulkan.waitIdle();
            batch.free();
            area.releaseBuffers();
            if(target != null) target.destroyBuffers();
            if(pipeline != null) pipeline.cleanUp();
            VRenderSystem.cull = oldCull; VRenderSystem.depthTest = oldDepth;
            VRenderSystem.depthMask = oldMask; VRenderSystem.stencilTest = oldStencil;
            PipelineState.blendInfo.enabled = oldBlend; VRenderSystem.colorMask = oldColorMask;
        }
    }

    private static ByteBuffer mesherCpuQuad(MemoryStack stack) {
        ByteBuffer bytes = stack.calloc(80);
        int i = 0;
        for(int[] xy : new int[][]{{-2375, 0}, {1900, 0}, {1900, 1900}, {-2375, 1900}}) {
            bytes.putShort(i, (short)xy[0]).putShort(i + 2, (short)xy[1]);
            bytes.putInt(i + 8, 0xff0000ff);
            i += 20;
        }
        return bytes;
    }

    private static ByteBuffer quad(MemoryStack stack, int left, int right, int color) {
        ByteBuffer bytes = stack.calloc(80);
        int i = 0;
        for(int[] xy : new int[][]{{left, -800}, {right, -800}, {right, 800}, {left, 800}}) {
            bytes.putShort(i, (short)xy[0]).putShort(i + 2, (short)xy[1]);
            bytes.putInt(i + 8, color);
            i += 20;
        }
        return bytes;
    }

    private static void expect(NativeImage image, int x, int y, int expected) {
        int actual = image.getPixelRGBA(x, y);
        require(actual == expected, String.format("Generated indirect pixel (%d,%d): expected %08x, got %08x",
                x, y, expected, actual));
    }

    private static void require(boolean condition, String message) {
        if(!condition) throw new AssertionError(message);
    }
}
