package net.vulkanmod.vulkan;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.client.Minecraft;
import net.vulkanmod.render.chunk.AreaUploadManager;
import net.vulkanmod.render.chunk.TerrainShaderManager;
import net.vulkanmod.render.profiling.Profiler2;
import net.vulkanmod.vulkan.framebuffer.Framebuffer;
import net.vulkanmod.vulkan.framebuffer.RenderPass;
import net.vulkanmod.vulkan.memory.MemoryManager;
import net.vulkanmod.vulkan.passes.DefaultMainPass;
import net.vulkanmod.vulkan.passes.MainPass;
import net.vulkanmod.vulkan.shader.*;
import net.vulkanmod.vulkan.shader.layout.PushConstants;
import net.vulkanmod.vulkan.texture.VTextureSelector;
import net.vulkanmod.vulkan.util.VUtil;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static net.vulkanmod.vulkan.Vulkan.*;
import static org.lwjgl.system.MemoryStack.stackGet;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.EXTDebugUtils.*;
import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.VK10.*;

public class Renderer {
    private static Renderer INSTANCE;

    private static VkDevice device;

    private static boolean swapCahinUpdate = false;
    public static boolean skipRendering = false;

    public static void initRenderer() { INSTANCE = new Renderer(); }

    public static Renderer getInstance() { return INSTANCE; }

    public static Drawer getDrawer() { return INSTANCE.drawer; }

    public static int getCurrentFrame() { return currentFrame; }

    public static int getCurrentImage() { return imageIndex; }

    private final Set<Pipeline> usedPipelines = new ObjectOpenHashSet<>();

    private final Drawer drawer;

    private int framesNum;
    private List<VkCommandBuffer> commandBuffers;
    private ArrayList<Long> imageAvailableSemaphores;
    private ArrayList<Long> renderFinishedSemaphores;
    private ArrayList<Long> inFlightFences;

    private Framebuffer boundFramebuffer;
    private RenderPass boundRenderPass;

    private static int currentFrame = 0;
    private static int imageIndex = 0;
    private VkCommandBuffer currentCmdBuffer;
    private boolean recordingFrame = false;

    MainPass mainPass = DefaultMainPass.PASS;

    private final List<Runnable> onResizeCallbacks = new ObjectArrayList<>();

    public Renderer() {
        device = Vulkan.getDevice();

        Uniforms.setupDefaultUniforms();
        TerrainShaderManager.init();
        AreaUploadManager.createInstance();

        framesNum = getSwapChainImages().size();

        drawer = new Drawer();
        drawer.createResources(framesNum);

        allocateCommandBuffers();
        createSyncObjects();

        AreaUploadManager.INSTANCE.createLists(framesNum);
    }

    private void allocateCommandBuffers() {
        if(commandBuffers != null) {
            commandBuffers.forEach(commandBuffer -> vkFreeCommandBuffers(device, Vulkan.getCommandPool(), commandBuffer));
        }

        commandBuffers = new ArrayList<>(framesNum);

        try(MemoryStack stack = stackPush()) {

            VkCommandBufferAllocateInfo allocInfo = VkCommandBufferAllocateInfo.callocStack(stack);
            allocInfo.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO);
            allocInfo.commandPool(getCommandPool());
            allocInfo.level(VK_COMMAND_BUFFER_LEVEL_PRIMARY);
            allocInfo.commandBufferCount(framesNum);

            PointerBuffer pCommandBuffers = stack.mallocPointer(framesNum);

            if (vkAllocateCommandBuffers(device, allocInfo, pCommandBuffers) != VK_SUCCESS) {
                throw new RuntimeException("Failed to allocate command buffers");
            }

            for (int i = 0; i < framesNum; i++) {
                commandBuffers.add(new VkCommandBuffer(pCommandBuffers.get(i), device));
            }
        }
    }

    private void createSyncObjects() {
        imageAvailableSemaphores = new ArrayList<>(framesNum);
        renderFinishedSemaphores = new ArrayList<>(framesNum);
        inFlightFences = new ArrayList<>(framesNum);

        try(MemoryStack stack = stackPush()) {

            VkSemaphoreCreateInfo semaphoreInfo = VkSemaphoreCreateInfo.callocStack(stack);
            semaphoreInfo.sType(VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO);

            VkFenceCreateInfo fenceInfo = VkFenceCreateInfo.callocStack(stack);
            fenceInfo.sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO);
            fenceInfo.flags(VK_FENCE_CREATE_SIGNALED_BIT);

            LongBuffer pImageAvailableSemaphore = stack.mallocLong(1);
            LongBuffer pRenderFinishedSemaphore = stack.mallocLong(1);
            LongBuffer pFence = stack.mallocLong(1);

            for(int i = 0;i < framesNum; i++) {

                if(vkCreateSemaphore(device, semaphoreInfo, null, pImageAvailableSemaphore) != VK_SUCCESS
                        || vkCreateSemaphore(device, semaphoreInfo, null, pRenderFinishedSemaphore) != VK_SUCCESS
                        || vkCreateFence(device, fenceInfo, null, pFence) != VK_SUCCESS) {

                    throw new RuntimeException("Failed to create synchronization objects for the frame " + i);
                }

                imageAvailableSemaphores.add(pImageAvailableSemaphore.get(0));
                renderFinishedSemaphores.add(pRenderFinishedSemaphore.get(0));
                inFlightFences.add(pFence.get(0));

            }

        }
    }

    public void beginFrame() {
        this.recordingFrame = false;

        Profiler2 p = Profiler2.getMainProfiler();
        p.push("Frame_fence");

        if(swapCahinUpdate) {
            recreateSwapChain();
            swapCahinUpdate = false;

            if(getSwapChain().getWidth() == 0 && getSwapChain().getHeight() == 0) {
                skipRendering = true;
                Minecraft.getInstance().noRender = true;
            }
            else {
                skipRendering = false;
                Minecraft.getInstance().noRender = false;
            }
        }


        if(skipRendering)
            return;

        vkWaitForFences(device, inFlightFences.get(currentFrame), true, VUtil.UINT64_MAX);

        p.pop();

        try(MemoryStack stack = stackPush()) {
            IntBuffer pImageIndex = stack.mallocInt(1);
            int vkResult = vkAcquireNextImageKHR(device, Vulkan.getSwapChain().getId(), VUtil.UINT64_MAX,
                    imageAvailableSemaphores.get(currentFrame), VK_NULL_HANDLE, pImageIndex);

            if(vkResult == VK_ERROR_OUT_OF_DATE_KHR) {
                swapCahinUpdate = true;
                return;
            } else if(vkResult == VK_SUBOPTIMAL_KHR) {
                // The image was still acquired and the semaphore will be signaled.
                // Render this frame so the graphics submit consumes that semaphore,
                // then rebuild the swapchain on the next frame.
                swapCahinUpdate = true;
            } else if(vkResult != VK_SUCCESS) {
                throw new RuntimeException("Cannot get image: " + vkResult);
            }

            imageIndex = pImageIndex.get(0);
        }

        p.start();
        p.push("Frame_ops");

        AreaUploadManager.INSTANCE.updateFrame(currentFrame);

        MemoryManager.getInstance().initFrame(currentFrame);
        drawer.setCurrentFrame(currentFrame);

        //Moved before texture updates
//        this.vertexBuffers[currentFrame].reset();
//        this.uniformBuffers.reset();
//        Vulkan.getStagingBuffer(currentFrame).reset();

        resetDescriptors();

        currentCmdBuffer = commandBuffers.get(currentFrame);
        vkResetCommandBuffer(currentCmdBuffer, 0);

        p.pop();

        try(MemoryStack stack = stackPush()) {

            VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.callocStack(stack);
            beginInfo.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);
            beginInfo.flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);

            VkCommandBuffer commandBuffer = currentCmdBuffer;

            int err = vkBeginCommandBuffer(commandBuffer, beginInfo);
            if (err != VK_SUCCESS) {
                throw new RuntimeException("Failed to begin recording command buffer:" + err);
            }

            mainPass.begin(commandBuffer, stack);
            this.recordingFrame = true;

            vkCmdSetDepthBias(commandBuffer, 0.0F, 0.0F, 0.0F);
        }
    }

    public void endFrame() {
        if(skipRendering || !this.recordingFrame)
            return;

        mainPass.end(currentCmdBuffer);

        try {
            submitFrame();
        } finally {
            this.recordingFrame = false;
        }
    }

    public void endRenderPass() {
        this.boundRenderPass.endRenderPass(currentCmdBuffer);
        this.boundRenderPass = null;
    }

    //TODO
    public void beginRendering(Framebuffer framebuffer) {
        if(skipRendering) 
            return;

        if(this.boundFramebuffer != framebuffer) {
            this.endRendering();

            try (MemoryStack stack = stackPush()) {
                //TODO
//                framebuffer.beginRenderPass(currentCmdBuffer, stack);
            }

            this.boundFramebuffer = framebuffer;
        }
    }

    public void endRendering() {
        if(skipRendering) 
            return;
        
        this.boundRenderPass.endRenderPass(currentCmdBuffer);

        this.boundFramebuffer = null;
        this.boundRenderPass = null;
    }

    public void setBoundFramebuffer(Framebuffer framebuffer) {
        this.boundFramebuffer = framebuffer;
    }

    public void resetBuffers() {
        // runTick resets these resources before texture/resource work, which is
        // earlier than beginFrame's normal frame-slot fence wait. Retire the slot
        // here first so staging/drawer memory cannot be reused while the previous
        // graphics frame (and any semaphore-ordered helper transfer) still uses it.
        if(!skipRendering) {
            vkWaitForFences(device, inFlightFences.get(currentFrame), true, VUtil.UINT64_MAX);
        }

        drawer.resetBuffers(currentFrame);
        Vulkan.getStagingBuffer(currentFrame).reset();
    }

    /**
     * Establish a safe reuse point for the current frame's shared staging buffer.
     * Texture uploads and terrain transfers both allocate ranges from it, so an
     * oversized texture reload must retire both queues before resetting the bump
     * pointer. This is intentionally a slow-path used only when a large texture
     * batch reaches its memory budget.
     */
    public void retireStagingUploadsForReuse() {
        AreaUploadManager.INSTANCE.waitAllUploads();

        // waitAllUploads handles any transfer command buffer still owned by the
        // area manager. Also wait the queues themselves for already-submitted work
        // whose bookkeeping has moved on to frame synchronization.
        Device.getTransferQueue().waitIdle();
        Device.getGraphicsQueue().waitIdle();

        // Same-graphics-queue texture helper buffers are now definitely complete;
        // recycle them immediately instead of retaining thousands of completed
        // command buffers until a later main-frame fence.
        Synchronization.INSTANCE.retireSameQueueCommandBuffersAfterQueueIdle();
    }

    public void addUsedPipeline(Pipeline pipeline) {
        usedPipelines.add(pipeline);
    }

    public void removeUsedPipeline(Pipeline pipeline) { usedPipelines.remove(pipeline); }

    private void resetDescriptors() {
        for(Pipeline pipeline : usedPipelines) {
            pipeline.resetDescriptorPool(currentFrame);
        }

        usedPipelines.clear();
    }

    private void submitFrame() {
        try(MemoryStack stack = stackPush()) {
            int vkResult;

            VkSubmitInfo submitInfo = VkSubmitInfo.callocStack(stack);
            submitInfo.sType(VK_STRUCTURE_TYPE_SUBMIT_INFO);

            int helperWaitSemaphoreCount = Synchronization.INSTANCE.getWaitSemaphoreCount();
            int totalWaitSemaphoreCount = helperWaitSemaphoreCount + 1;

            LongBuffer waitSemaphores = stack.mallocLong(totalWaitSemaphoreCount);
            IntBuffer waitDstStageMask = stack.mallocInt(totalWaitSemaphoreCount);

            Synchronization.INSTANCE.getWaitSemaphores(waitSemaphores);

            for(int i = 0; i < helperWaitSemaphoreCount; ++i) {
                waitDstStageMask.put(i, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT);
            }

            waitSemaphores.put(totalWaitSemaphoreCount - 1, imageAvailableSemaphores.get(currentFrame));
            waitDstStageMask.put(totalWaitSemaphoreCount - 1, VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT);
            waitSemaphores.position(0);

            submitInfo.waitSemaphoreCount(totalWaitSemaphoreCount);
            submitInfo.pWaitSemaphores(waitSemaphores);
            submitInfo.pWaitDstStageMask(waitDstStageMask);

            submitInfo.pSignalSemaphores(stackGet().longs(renderFinishedSemaphores.get(imageIndex)));

            submitInfo.pCommandBuffers(stack.pointers(currentCmdBuffer));

            vkResetFences(device, stackGet().longs(inFlightFences.get(currentFrame)));

            // Preserve correctness for any legacy producer that still registers a fence.
            // Converted helper uploads take the semaphore path above and return immediately here.
            Synchronization.INSTANCE.waitFences();

            if((vkResult = vkQueueSubmit(Device.getGraphicsQueue().queue(), submitInfo, inFlightFences.get(currentFrame))) != VK_SUCCESS) {
                vkResetFences(device, stackGet().longs(inFlightFences.get(currentFrame)));
                throw new RuntimeException("Failed to submit draw command buffer: " + vkResult);
            }

            Synchronization.INSTANCE.scheduleCbReset();

            VkPresentInfoKHR presentInfo = VkPresentInfoKHR.calloc(stack);
            presentInfo.sType(VK_STRUCTURE_TYPE_PRESENT_INFO_KHR);

            presentInfo.pWaitSemaphores(stackGet().longs(renderFinishedSemaphores.get(imageIndex)));

            presentInfo.swapchainCount(1);
            presentInfo.pSwapchains(stack.longs(Vulkan.getSwapChain().getId()));

            presentInfo.pImageIndices(stack.ints(imageIndex));

            vkResult = vkQueuePresentKHR(Device.getPresentQueue().queue(), presentInfo);

            if(vkResult == VK_ERROR_OUT_OF_DATE_KHR || vkResult == VK_SUBOPTIMAL_KHR || swapCahinUpdate) {
                swapCahinUpdate = true;
                return;
            } else if(vkResult != VK_SUCCESS) {
                throw new RuntimeException("Failed to present swap chain image");
            }

            currentFrame = (currentFrame + 1) % framesNum;
        }
    }

    void waitForSwapChain()
    {
        vkResetFences(device, inFlightFences.get(currentFrame));

//        constexpr VkPipelineStageFlags t=VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
        try(MemoryStack stack = MemoryStack.stackPush()) {
            //Empty Submit
            VkSubmitInfo info = VkSubmitInfo.calloc(stack)
                    .sType$Default()
                    .pWaitSemaphores(stack.longs(imageAvailableSemaphores.get(currentFrame)))
                    .pWaitDstStageMask(stack.ints(VK_PIPELINE_STAGE_ALL_COMMANDS_BIT));

            vkQueueSubmit(Device.getGraphicsQueue().queue(), info, inFlightFences.get(currentFrame));
            vkWaitForFences(device, inFlightFences.get(currentFrame),  true, -1);
        }
    }

    private void recreateSwapChain() {
//        for(Long fence : inFlightFences) {
//            vkWaitForFences(device, fence, true, VUtil.UINT64_MAX);
//        }

//        waitForSwapChain();
        Vulkan.waitIdle();
        AreaUploadManager.INSTANCE.waitAllUploads();

//        for(int i = 0; i < getSwapChainImages().size(); ++i) {
//            vkDestroyFence(device, inFlightFences.get(i), null);
//            vkDestroySemaphore(device, imageAvailableSemaphores.get(i), null);
//            vkDestroySemaphore(device, renderFinishedSemaphores.get(i), null);
//        }

        commandBuffers.forEach(commandBuffer -> vkResetCommandBuffer(commandBuffer, 0));

        Vulkan.recreateSwapChain();

        int newFramesNum = getSwapChain().getFramesNum();

        // Acquire/present semaphores belong to the old swapchain lifecycle. Recreate
        // them even when the image count is unchanged so every binary semaphore
        // starts in a known unsignaled state after replacement.
        destroySyncObjects();

        if(framesNum != newFramesNum) {
            framesNum = newFramesNum;
            allocateCommandBuffers();

            Pipeline.recreateDescriptorSets(framesNum);

            drawer.createResources(framesNum);
            AreaUploadManager.INSTANCE.createLists(framesNum);
        }

        createSyncObjects();

        this.onResizeCallbacks.forEach(Runnable::run);

        currentFrame = 0;
        imageIndex = 0;
        this.recordingFrame = false;
    }

    public void cleanUpResources() {
        destroySyncObjects();

        drawer.cleanUpResources();

        TerrainShaderManager.destroyPipelines();
        VTextureSelector.getWhiteTexture().free();
    }

    private void destroySyncObjects() {
        for (int i = 0; i < framesNum; ++i) {
            vkDestroyFence(device, inFlightFences.get(i), null);
            vkDestroySemaphore(device, imageAvailableSemaphores.get(i), null);
            vkDestroySemaphore(device, renderFinishedSemaphores.get(i), null);
        }
    }

    public void setBoundFramebuffer(Framebuffer framebuffer) {
        this.boundFramebuffer = framebuffer;
    }

    public RenderPass getBoundRenderPass() {
        return boundRenderPass;
    }

    public void setMainPass(MainPass mainPass) { this.mainPass = mainPass; }

    public void addOnResizeCallback(Runnable runnable) {
        this.onResizeCallbacks.add(runnable);
    }

    public void bindGraphicsPipeline(GraphicsPipeline pipeline) {
        VkCommandBuffer commandBuffer = currentCmdBuffer;

        PipelineState currentState = PipelineState.getCurrentPipelineState(boundRenderPass);
        vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.getHandle(currentState));

        addUsedPipeline(pipeline);
    }

    public void uploadAndBindUBOs(Pipeline pipeline) {
        VkCommandBuffer commandBuffer = currentCmdBuffer;
        pipeline.bindDescriptorSets(commandBuffer, currentFrame);
    }

    public void pushConstants(Pipeline pipeline) {
        VkCommandBuffer commandBuffer = currentCmdBuffer;

        PushConstants pushConstants = pipeline.getPushConstants();

        try (MemoryStack stack = stackPush()) {
            ByteBuffer buffer = stack.malloc(pushConstants.getSize());
            long ptr = MemoryUtil.memAddress0(buffer);
            pushConstants.update(ptr);

            nvkCmdPushConstants(commandBuffer, pipeline.getLayout(), VK_SHADER_STAGE_VERTEX_BIT, 0, pushConstants.getSize(), ptr);
        }

    }

    public static void setDepthBias(float units, float factor) {
        VkCommandBuffer commandBuffer = INSTANCE.currentCmdBuffer;

        vkCmdSetDepthBias(commandBuffer, units, 0.0f, factor);
    }

    public static void clearAttachments(int v) {
        Framebuffer framebuffer = Renderer.getInstance().boundFramebuffer;
        if(framebuffer == null)
            return;

        clearAttachments(v, framebuffer.getWidth(), framebuffer.getHeight());
    }

    public static void clearAttachments(int v, int width, int height) {
        if(skipRendering)
            return;

        try(MemoryStack stack = stackPush()) {
            VkClearValue clearValue = VkClearValue.calloc(stack);
            clearValue.color().float32(0, VRenderSystem.clearColor[0]);
            clearValue.color().float32(1, VRenderSystem.clearColor[1]);
            clearValue.color().float32(2, VRenderSystem.clearColor[2]);
            clearValue.color().float32(3, VRenderSystem.clearColor[3]);

            VkClearAttachment.Buffer clearAttachments = VkClearAttachment.calloc(1, stack);
            clearAttachments.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT);
            clearAttachments.colorAttachment(0);
            clearAttachments.clearValue(clearValue);

            VkClearRect.Buffer clearRects = VkClearRect.calloc(1, stack);
            clearRects.rect().offset().set(0, 0);
            clearRects.rect().extent().set(width, height);
            clearRects.baseArrayLayer(0);
            clearRects.layerCount(1);

            vkCmdClearAttachments(INSTANCE.currentCmdBuffer, clearAttachments, clearRects);
        }
    }

    public static void scheduleSwapChainUpdate() {
        swapCahinUpdate = true;
    }

    public int getFramesNum() {
        return framesNum;
    }

    public static int getFramesNum() {
        return INSTANCE.framesNum;
    }
}