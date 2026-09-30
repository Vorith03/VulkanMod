package net.vulkanmod.mixin.profiling;

import net.vulkanmod.render.profiling.FlightRecorderCapture;
import net.vulkanmod.vulkan.Renderer;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkPresentInfoKHR;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkSubmitInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.nio.IntBuffer;

import static org.lwjgl.vulkan.KHRSwapchain.vkAcquireNextImageKHR;
import static org.lwjgl.vulkan.KHRSwapchain.vkQueuePresentKHR;
import static org.lwjgl.vulkan.VK10.vkQueueSubmit;
import static org.lwjgl.vulkan.VK10.vkWaitForFences;

/**
 * Records universal Vulkan boundaries used directly by the main renderer.
 *
 * <p>This intentionally describes API operations, not renderer subsystems. The JFR
 * stack trace answers which code caused a submission/wait/acquire/present after the
 * capture, so no terrain/entity/texture hypothesis has to be chosen in advance.</p>
 */
@Mixin(value = Renderer.class, priority = 900)
public abstract class RendererFlightRecorderMixin {
    @Redirect(
            method = {"beginFrame", "resetBuffers", "waitForSwapChain"},
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/vulkan/VK10;vkWaitForFences(Lorg/lwjgl/vulkan/VkDevice;JZJ)I"),
            remap = false)
    private int vulkanmod$recordFenceWait(VkDevice device, long fence, boolean waitAll, long timeout) {
        FlightRecorderCapture.VulkanFenceWaitEvent event =
                FlightRecorderCapture.beginVulkanFenceWait(fence, 1);
        int result = vkWaitForFences(device, fence, waitAll, timeout);
        FlightRecorderCapture.endVulkanFenceWait(event, result);
        return result;
    }

    @Redirect(
            method = "beginFrame",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/vulkan/KHRSwapchain;vkAcquireNextImageKHR(Lorg/lwjgl/vulkan/VkDevice;JJJJLjava/nio/IntBuffer;)I"),
            remap = false)
    private int vulkanmod$recordAcquire(VkDevice device, long swapchain, long timeout,
                                        long semaphore, long fence, IntBuffer imageIndex) {
        FlightRecorderCapture.VulkanApiEvent event =
                FlightRecorderCapture.beginVulkanApi("vkAcquireNextImageKHR", swapchain);
        int result = vkAcquireNextImageKHR(device, swapchain, timeout, semaphore, fence, imageIndex);
        FlightRecorderCapture.endVulkanApi(event, result);
        return result;
    }

    @Redirect(
            method = {"submitFrame", "waitForSwapChain"},
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/vulkan/VK10;vkQueueSubmit(Lorg/lwjgl/vulkan/VkQueue;Lorg/lwjgl/vulkan/VkSubmitInfo;J)I"),
            remap = false)
    private int vulkanmod$recordSubmit(VkQueue queue, VkSubmitInfo submitInfo, long fence) {
        FlightRecorderCapture.VulkanSubmissionEvent event = FlightRecorderCapture.beginVulkanSubmission(
                0L, queue.address(), fence, submitInfo.pSignalSemaphores() != null);
        int result = vkQueueSubmit(queue, submitInfo, fence);
        FlightRecorderCapture.endVulkanSubmission(event, result);
        return result;
    }

    @Redirect(
            method = "submitFrame",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/vulkan/KHRSwapchain;vkQueuePresentKHR(Lorg/lwjgl/vulkan/VkQueue;Lorg/lwjgl/vulkan/VkPresentInfoKHR;)I"),
            remap = false)
    private int vulkanmod$recordPresent(VkQueue queue, VkPresentInfoKHR presentInfo) {
        FlightRecorderCapture.VulkanApiEvent event =
                FlightRecorderCapture.beginVulkanApi("vkQueuePresentKHR", queue.address());
        int result = vkQueuePresentKHR(queue, presentInfo);
        FlightRecorderCapture.endVulkanApi(event, result);
        return result;
    }
}
