package net.vulkanmod.render.chunk.voxel;

import net.vulkanmod.vulkan.Device;
import net.vulkanmod.vulkan.queue.Queue;
import net.vulkanmod.vulkan.shader.SPIRVUtils;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkComputePipelineCreateInfo;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo;
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo;
import org.lwjgl.vulkan.VkQueueFamilyProperties;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;

import java.nio.IntBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK10.*;

/** Shared compute pipeline for live section-selection shadow dispatches. */
public final class GpuSectionSelectionShadowPipeline {
    private static State state;

    private GpuSectionSelectionShadowPipeline() {}

    static synchronized State get() {
        if(state == null)
            state = new State();
        return state;
    }

    public static synchronized void destroy() {
        if(state != null) {
            state.close();
            state = null;
        }
    }

    static final class State implements AutoCloseable {
        final long descriptorSetLayout;
        final long pipelineLayout;
        final long pipeline;
        private boolean closed;

        State() {
            if(!graphicsQueueSupportsCompute())
                throw new UnsupportedOperationException(
                        "Graphics queue family does not support compute dispatch");

            long setLayout = VK_NULL_HANDLE;
            long layout = VK_NULL_HANDLE;
            long computePipeline = VK_NULL_HANDLE;
            long module = VK_NULL_HANDLE;
            SPIRVUtils.SPIRV spirv = null;
            try(MemoryStack stack = MemoryStack.stackPush()) {
                VkDescriptorSetLayoutBinding.Buffer bindings =
                        VkDescriptorSetLayoutBinding.calloc(3, stack);
                for(int binding = 0; binding < 3; ++binding) {
                    bindings.get(binding).binding(binding)
                            .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                            .descriptorCount(1)
                            .stageFlags(VK_SHADER_STAGE_COMPUTE_BIT)
                            .pImmutableSamplers(null);
                }

                LongBuffer pSetLayout = stack.mallocLong(1);
                check(vkCreateDescriptorSetLayout(Device.device,
                        VkDescriptorSetLayoutCreateInfo.calloc(stack).sType$Default()
                                .pBindings(bindings), null, pSetLayout),
                        "create section-selection shadow descriptor layout");
                setLayout = pSetLayout.get(0);

                LongBuffer pPipelineLayout = stack.mallocLong(1);
                check(vkCreatePipelineLayout(Device.device,
                        VkPipelineLayoutCreateInfo.calloc(stack).sType$Default()
                                .pSetLayouts(stack.longs(setLayout)), null, pPipelineLayout),
                        "create section-selection shadow pipeline layout");
                layout = pPipelineLayout.get(0);

                spirv = SPIRVUtils.compileShaderResource(
                        "/assets/vulkanmod/shaders/terrain/section_select_probe.comp",
                        SPIRVUtils.ShaderKind.COMPUTE_SHADER);
                LongBuffer pModule = stack.mallocLong(1);
                check(vkCreateShaderModule(Device.device,
                        VkShaderModuleCreateInfo.calloc(stack).sType$Default()
                                .pCode(spirv.bytecode()), null, pModule),
                        "create section-selection shadow shader module");
                module = pModule.get(0);

                VkPipelineShaderStageCreateInfo stage = VkPipelineShaderStageCreateInfo.calloc(stack)
                        .sType$Default().stage(VK_SHADER_STAGE_COMPUTE_BIT)
                        .module(module).pName(stack.UTF8("main"));
                VkComputePipelineCreateInfo.Buffer info = VkComputePipelineCreateInfo.calloc(1, stack);
                info.get(0).sType$Default().stage(stage).layout(layout)
                        .basePipelineHandle(VK_NULL_HANDLE).basePipelineIndex(-1);
                LongBuffer pPipeline = stack.mallocLong(1);
                check(vkCreateComputePipelines(Device.device, VK_NULL_HANDLE,
                        info, null, pPipeline),
                        "create section-selection shadow compute pipeline");
                computePipeline = pPipeline.get(0);
            } catch(RuntimeException | Error failure) {
                if(computePipeline != VK_NULL_HANDLE)
                    vkDestroyPipeline(Device.device, computePipeline, null);
                if(layout != VK_NULL_HANDLE)
                    vkDestroyPipelineLayout(Device.device, layout, null);
                if(setLayout != VK_NULL_HANDLE)
                    vkDestroyDescriptorSetLayout(Device.device, setLayout, null);
                throw failure;
            } finally {
                if(module != VK_NULL_HANDLE)
                    vkDestroyShaderModule(Device.device, module, null);
                if(spirv != null)
                    spirv.free();
            }

            this.descriptorSetLayout = setLayout;
            this.pipelineLayout = layout;
            this.pipeline = computePipeline;
        }

        @Override
        public void close() {
            if(closed)
                return;
            closed = true;
            vkDestroyPipeline(Device.device, pipeline, null);
            vkDestroyPipelineLayout(Device.device, pipelineLayout, null);
            vkDestroyDescriptorSetLayout(Device.device, descriptorSetLayout, null);
        }
    }

    private static boolean graphicsQueueSupportsCompute() {
        try(MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer count = stack.ints(0);
            vkGetPhysicalDeviceQueueFamilyProperties(Device.physicalDevice, count, null);
            VkQueueFamilyProperties.Buffer properties =
                    VkQueueFamilyProperties.malloc(count.get(0), stack);
            vkGetPhysicalDeviceQueueFamilyProperties(Device.physicalDevice, count, properties);
            return (properties.get(Queue.getQueueFamilies().graphicsFamily).queueFlags()
                    & VK_QUEUE_COMPUTE_BIT) != 0;
        }
    }

    private static void check(int result, String action) {
        if(result != VK_SUCCESS)
            throw new RuntimeException("Failed to " + action + ": " + result);
    }
}
