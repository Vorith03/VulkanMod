package net.vulkanmod.mixin.profiling;

import net.vulkanmod.render.profiling.FlightRecorderCapture;
import net.vulkanmod.render.profiling.GpuTimestampRecorder;
import net.vulkanmod.vulkan.Device;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.queue.Queue;
import org.lwjgl.PointerBuffer;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkSubmitInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static org.lwjgl.vulkan.VK10.vkDeviceWaitIdle;
import static org.lwjgl.vulkan.VK10.vkQueueSubmit;
import static org.lwjgl.vulkan.VK10.vkWaitForFences;

/** Records Vulkan's generic immediate-command and device-idle boundaries. */
@Mixin(value = Vulkan.class, priority = 900)
public abstract class VulkanFlightRecorderMixin {
    @Shadow private static VkCommandBuffer immediateCmdBuffer;

    @Inject(
            method = "beginImmediateCmd",
            at = @At(value = "INVOKE",
                    target = "Lorg/lwjgl/vulkan/VK10;vkBeginCommandBuffer(Lorg/lwjgl/vulkan/VkCommandBuffer;Lorg/lwjgl/vulkan/VkCommandBufferBeginInfo;)I",
                    shift = At.Shift.AFTER,
                    remap = false))
    private static void vulkanmod$startImmediateGpuTimestamp(CallbackInfoReturnable<VkCommandBuffer> cir) {
        GpuTimestampRecorder.begin(immediateCmdBuffer, Queue.getQueueFamilies().graphicsFamily);
    }

    @Inject(
            method = "endImmediateCmd",
            at = @At(value = "INVOKE",
                    target = "Lorg/lwjgl/vulkan/VK10;vkEndCommandBuffer(Lorg/lwjgl/vulkan/VkCommandBuffer;)I",
                    shift = At.Shift.BEFORE,
                    remap = false))
    private static void vulkanmod$endImmediateGpuTimestamp(CallbackInfo ci) {
        GpuTimestampRecorder.end(immediateCmdBuffer);
    }

    @Redirect(
            method = "endImmediateCmd",
            at = @At(value = "INVOKE",
                    target = "Lorg/lwjgl/vulkan/VK10;vkQueueSubmit(Lorg/lwjgl/vulkan/VkQueue;Lorg/lwjgl/vulkan/VkSubmitInfo;J)I"),
            remap = false)
    private static int vulkanmod$recordImmediateSubmit(VkQueue queue, VkSubmitInfo submitInfo, long fence) {
        PointerBuffer submitted = submitInfo.pCommandBuffers();
        long commandBuffer = submitted != null && submitted.hasRemaining()
                ? submitted.get(submitted.position()) : 0L;
        FlightRecorderCapture.VulkanSubmissionEvent event = FlightRecorderCapture.beginVulkanSubmission(
                commandBuffer, queue.address(), fence, submitInfo.pSignalSemaphores() != null);
        int result = vkQueueSubmit(queue, submitInfo, fence);
        FlightRecorderCapture.endVulkanSubmission(event, result);
        return result;
    }

    @Redirect(
            method = "endImmediateCmd",
            at = @At(value = "INVOKE",
                    target = "Lorg/lwjgl/vulkan/VK10;vkWaitForFences(Lorg/lwjgl/vulkan/VkDevice;JZJ)I"),
            remap = false)
    private static int vulkanmod$recordImmediateFenceWait(VkDevice device, long fence, boolean waitAll, long timeout) {
        FlightRecorderCapture.VulkanFenceWaitEvent event =
                FlightRecorderCapture.beginVulkanFenceWait(fence, 1);
        int result = vkWaitForFences(device, fence, waitAll, timeout);
        FlightRecorderCapture.endVulkanFenceWait(event, result);
        if (result == 0) GpuTimestampRecorder.complete(immediateCmdBuffer);
        return result;
    }

    @Redirect(
            method = {"waitIdle", "cleanUp"},
            at = @At(value = "INVOKE",
                    target = "Lorg/lwjgl/vulkan/VK10;vkDeviceWaitIdle(Lorg/lwjgl/vulkan/VkDevice;)I"),
            remap = false)
    private static int vulkanmod$recordDeviceIdle(VkDevice device) {
        FlightRecorderCapture.VulkanApiEvent event =
                FlightRecorderCapture.beginVulkanApi("vkDeviceWaitIdle", device.address());
        int result = vkDeviceWaitIdle(device);
        FlightRecorderCapture.endVulkanApi(event, result);
        return result;
    }

    @Inject(
            method = "cleanUp",
            at = @At(value = "INVOKE",
                    target = "Lorg/lwjgl/vulkan/VK10;vkDestroyCommandPool(Lorg/lwjgl/vulkan/VkDevice;JLorg/lwjgl/vulkan/VkAllocationCallbacks;)V",
                    shift = At.Shift.BEFORE,
                    remap = false))
    private static void vulkanmod$destroyGpuTimestampPool(CallbackInfo ci) {
        GpuTimestampRecorder.cleanUp();
    }
}
