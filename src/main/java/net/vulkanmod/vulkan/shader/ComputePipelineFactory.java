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
        return create(resource, pipelineLayout, new Calls() {
            public SPIRVUtils.SPIRV compile(String path) {
                return SPIRVUtils.compileShaderResource(path, SPIRVUtils.ShaderKind.COMPUTE_SHADER);
            }
            public int module(VkShaderModuleCreateInfo info, LongBuffer result) {
                return vkCreateShaderModule(device, info, null, result);
            }
            public int pipeline(VkComputePipelineCreateInfo.Buffer info, LongBuffer result) {
                return vkCreateComputePipelines(device, VK_NULL_HANDLE, info, null, result);
            }
            public void destroyModule(long module) { vkDestroyShaderModule(device, module, null); }
            public void destroyPipeline(long pipeline) { vkDestroyPipeline(device, pipeline, null); }
            public void release(SPIRVUtils.SPIRV spirv) { spirv.free(); }
        });
    }

    // Construction-only seam: no global override and no dispatch/retirement ownership.
    interface Calls {
        SPIRVUtils.SPIRV compile(String resource);
        int module(VkShaderModuleCreateInfo info, LongBuffer result);
        int pipeline(VkComputePipelineCreateInfo.Buffer info, LongBuffer result);
        void destroyModule(long module);
        void destroyPipeline(long pipeline);
        void release(SPIRVUtils.SPIRV spirv);
    }

    static long create(String resource, long pipelineLayout, Calls calls) {
        SPIRVUtils.SPIRV spirv = calls.compile(resource);
        long module = VK_NULL_HANDLE;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var info = VkShaderModuleCreateInfo.calloc(stack).sType$Default().pCode(spirv.bytecode());
            LongBuffer result = stack.callocLong(1);
            check(calls.module(info, result), resource + " shader module");
            module = result.get(0);
            var stage = VkPipelineShaderStageCreateInfo.calloc(stack).sType$Default()
                    .stage(VK_SHADER_STAGE_COMPUTE_BIT).module(module).pName(stack.UTF8("main"));
            var pipelineInfo = VkComputePipelineCreateInfo.calloc(1, stack);
            pipelineInfo.get(0).sType$Default().stage(stage).layout(pipelineLayout)
                    .basePipelineHandle(VK_NULL_HANDLE).basePipelineIndex(-1);
            result.put(0, VK_NULL_HANDLE);
            // Deliberately uncached until graphics' static cache has an explicit device lifecycle.
            int status = calls.pipeline(pipelineInfo, result);
            if (status != VK_SUCCESS) {
                // Vulkan may return successfully created handles on a failed multi-create call.
                if (result.get(0) != VK_NULL_HANDLE) calls.destroyPipeline(result.get(0));
                check(status, resource + " compute pipeline");
            }
            return result.get(0);
        } finally {
            if (module != VK_NULL_HANDLE) calls.destroyModule(module);
            calls.release(spirv);
        }
    }

    private static void check(int status, String action) {
        if (status != VK_SUCCESS) throw new IllegalStateException("Failed to create " + action + ": " + status);
    }
}
