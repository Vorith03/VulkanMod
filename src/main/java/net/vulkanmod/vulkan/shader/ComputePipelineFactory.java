package net.vulkanmod.vulkan.shader;

import net.vulkanmod.vulkan.Device;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import java.nio.LongBuffer;
import static org.lwjgl.vulkan.VK10.*;

/** Temporary shader/module ownership only. Callers own layouts, pipeline retirement and dispatch. */
public final class ComputePipelineFactory {
    private ComputePipelineFactory() {}

    public static long create(String resource, long pipelineLayout) {
        VkDevice device = Device.device;
        SPIRVUtils.SPIRV spirv = SPIRVUtils.compileShaderResource(resource, SPIRVUtils.ShaderKind.COMPUTE_SHADER);
        long module = VK_NULL_HANDLE;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var info = VkShaderModuleCreateInfo.calloc(stack).sType$Default().pCode(spirv.bytecode());
            LongBuffer result = stack.callocLong(1);
            check(vkCreateShaderModule(device, info, null, result), resource + " shader module");
            module = result.get(0);
            var stage = VkPipelineShaderStageCreateInfo.calloc(stack).sType$Default()
                    .stage(VK_SHADER_STAGE_COMPUTE_BIT).module(module).pName(stack.UTF8("main"));
            var pipelineInfo = VkComputePipelineCreateInfo.calloc(1, stack);
            pipelineInfo.get(0).sType$Default().stage(stage).layout(pipelineLayout)
                    .basePipelineHandle(VK_NULL_HANDLE).basePipelineIndex(-1);
            result.put(0, VK_NULL_HANDLE);
            // Deliberately uncached until graphics' static cache has an explicit device lifecycle.
            int status = vkCreateComputePipelines(device, VK_NULL_HANDLE, pipelineInfo, null, result);
            if (status != VK_SUCCESS) {
                // Vulkan may return successfully created handles on a failed multi-create call.
                if (result.get(0) != VK_NULL_HANDLE) vkDestroyPipeline(device, result.get(0), null);
                check(status, resource + " compute pipeline");
            }
            return result.get(0);
        } finally {
            if (module != VK_NULL_HANDLE) vkDestroyShaderModule(device, module, null);
            spirv.free();
        }
    }

    private static void check(int status, String action) {
        if (status != VK_SUCCESS) throw new IllegalStateException("Failed to create " + action + ": " + status);
    }
}
