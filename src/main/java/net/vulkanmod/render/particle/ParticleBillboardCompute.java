package net.vulkanmod.render.particle;

import net.vulkanmod.Initializer;
import net.vulkanmod.vulkan.Device;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.memory.MemoryManager;
import net.vulkanmod.vulkan.memory.VertexBuffer;
import net.vulkanmod.vulkan.queue.Queue;
import net.vulkanmod.vulkan.shader.SPIRVUtils;
import net.vulkanmod.render.profiling.PerformanceProfiler;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import static org.lwjgl.vulkan.VK10.*;

/** One compute dispatch per original particle draw, independent of its graphics shader. */
public final class ParticleBillboardCompute implements AutoCloseable {
    private static final boolean REQUESTED=Boolean.getBoolean("vulkanmod.experimentalGpuParticleBillboards");
    private static final int MAX_BINDINGS=64, PUSH_CONSTANT_BYTES=16;
    private static ParticleBillboardCompute instance;
    private static Boolean graphicsComputeSupported;
    private static boolean unavailable, ciUnavailable;
    private static long inheritedAttempts, eligibleAttempts, layoutFallbacks, unavailableFallbacks, originalCpu, packed;
    private static long gpuQuads, recoveryQuads, batches, preparationNanos, uploadedBytes, bindingsCreated;
    private static long capacityRejections, descriptorRejections, callbackStateRecoveries;
    private long descriptorSetLayout, descriptorPool, pipelineLayout, pipeline;
    private final Map<Long,Long> bindings=new HashMap<>();
    // vkFreeDescriptorSets is fence-retired. Include sets queued for retirement
    // in the actual pool reservation count; some software drivers do not reject
    // allocations after maxSets, so the host must enforce its own bound.
    private int allocatedBindings;
    private ByteBuffer offsets=MemoryUtil.memAlloc(ParticleQuadBatch.MAX_QUADS*4).order(ByteOrder.nativeOrder());
    private boolean closed;

    private ParticleBillboardCompute() {
        try { createDescriptorResources(); createPipelineLayout(); createPipeline(); }
        catch(RuntimeException | Error e) { close(); throw e; }
    }
    public static boolean requested() { return REQUESTED; }
    public static void attempt(boolean eligible) {
        inheritedAttempts++; if(eligible) eligibleAttempts++;
    }
    public static void originalCpu() { originalCpu++; }
    public static void packed() { packed++; }
    public static void layoutFallback() { layoutFallbacks++; }
    public static void callbackStateRecovery() { callbackStateRecoveries++; }
    public static void cpuRecovery(int count) { recoveryQuads+=count; }
    public static boolean ready() {
        if(!REQUESTED || unavailable || ciUnavailable || Device.device==null
                || !Renderer.getInstance().isRecordingFrame() || Device.getGraphicsQueue().hasActiveUploadBatch()
                || Boolean.getBoolean("joml.useMathFma")) { unavailableFallbacks++; return false; }
        try {
            if(!graphicsQueueSupportsCompute()) { unavailable=true; unavailableFallbacks++; return false; }
            if(instance==null) instance=new ParticleBillboardCompute();
            return true;
        } catch(RuntimeException | OutOfMemoryError e) {
            unavailable=true; unavailableFallbacks++;
            Initializer.LOGGER.warn("GPU particle billboards unavailable; retaining CPU expansion",e);
            return false;
        }
    }
    public static void ciUnavailable(boolean value) {
        if(!Boolean.getBoolean("vulkanmod.ciScreenshotSmoke")) throw new IllegalStateException("CI only");
        ciUnavailable=value;
    }
    /** Called only after idle at renderer destruction. Pending frame operations tolerate a closed pool. */
    public static void shutdownAfterDeviceIdle() {
        if(instance!=null) instance.close();
        instance=null; unavailable=false; graphicsComputeSupported=null;
    }
    public static void retireBuffer(long buffer) {
        if(instance==null) return;
        ParticleBillboardCompute owner=instance;
        Long set=owner.bindings.remove(buffer);
        if(set!=null) MemoryManager.getInstance().addFrameOp(()->owner.freeSet(set));
    }
    private void freeSet(long set) {
        if(closed) return;
        try(MemoryStack stack=MemoryStack.stackPush()) {
            check(vkFreeDescriptorSets(Device.device,descriptorPool,stack.longs(set)),"free billboard descriptor");
            allocatedBindings--;
        }
    }
    private long binding(VertexBuffer buffer) {
        Long cached=bindings.get(buffer.getId());
        if(cached!=null) return cached;
        // A removed binding is still resident until its frame-op free runs.
        // Never rely on a driver's optional over-allocation behavior.
        if(allocatedBindings >= MAX_BINDINGS) return 0;
        try(MemoryStack stack=MemoryStack.stackPush()) {
            LongBuffer out=stack.mallocLong(1);
            int result=vkAllocateDescriptorSets(Device.device,VkDescriptorSetAllocateInfo.calloc(stack)
                    .sType$Default().descriptorPool(descriptorPool).pSetLayouts(stack.longs(descriptorSetLayout)),out);
            if(result!=VK_SUCCESS) return 0;
            long set=out.get(0);
            VkDescriptorBufferInfo.Buffer info=VkDescriptorBufferInfo.calloc(1,stack);
            info.get(0).buffer(buffer.getId()).offset(0)
                    // Vertex-buffer growth can exceed the storage range even while this draw fits.
                    // Admission bounds every accessed byte; never expose the unused allocation tail.
                    .range(Math.min((long)buffer.getBufferSize(),
                            Integer.toUnsignedLong(Device.deviceProperties.limits().maxStorageBufferRange())));
            VkWriteDescriptorSet.Buffer write=VkWriteDescriptorSet.calloc(1,stack);
            write.get(0).sType$Default().dstSet(set).dstBinding(0).descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                    .descriptorCount(1).pBufferInfo(info);
            vkUpdateDescriptorSets(Device.device,write,null);
            bindings.put(buffer.getId(),set); bindingsCreated++; allocatedBindings++;
            return set;
        }
    }
    /** No CPU expansion on success. On pre-record rejection the caller expands and performs its ordinary upload. */
    public static boolean expand(VertexBuffer buffer, ByteBuffer vertices, ParticleQuadBatch batch) {
        long start=System.nanoTime();
        batch.validate(vertices.remaining());
        if(!ready()) return false;
        int groups=(batch.count()+63)/64;
        if(groups>Device.deviceProperties.limits().maxComputeWorkGroupCount(0)) { capacityRejections++; return false; }
        int padding=(int)((4-(buffer.getUsedBytes()&3))&3);
        int metadataBytes=batch.count()*4;
        long needed=buffer.getUsedBytes()+padding+metadataBytes+vertices.remaining();
        if(needed>Integer.toUnsignedLong(Device.deviceProperties.limits().maxStorageBufferRange()) || needed>Integer.MAX_VALUE) { capacityRejections++; return false; }
        buffer.reserve((int)(padding+metadataBytes+vertices.remaining()));
        long set=instance.binding(buffer);
        if(set==0) { descriptorRejections++; return false; }
        ByteBuffer table=instance.offsets;
        table.clear();
        for(int i=0;i<batch.count();i++) table.putInt(batch.offset(i));
        table.flip();
        try(MemoryStack stack=MemoryStack.stackPush()) {
            if(padding!=0) buffer.copyToVertexBuffer(1,padding,stack.calloc(padding));
            buffer.copyToVertexBuffer(4,batch.count(),table);
            int list=(int)buffer.getOffset()/4;
            buffer.copyToVertexBuffer(1,vertices.remaining(),vertices.slice());
            int base=(int)buffer.getOffset()/4;
            var queue=Device.getGraphicsQueue();
            var command=queue.getCommandBuffer();
            var cmd=command.getHandle();
            VkBufferMemoryBarrier.Buffer barrier=VkBufferMemoryBarrier.calloc(1,stack);
            barrier.get(0).sType$Default().srcAccessMask(VK_ACCESS_HOST_WRITE_BIT)
                    .dstAccessMask(VK_ACCESS_SHADER_READ_BIT | VK_ACCESS_SHADER_WRITE_BIT)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED).dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .buffer(buffer.getId()).offset(list*4L).size(metadataBytes+vertices.remaining());
            vkCmdPipelineBarrier(cmd,VK_PIPELINE_STAGE_HOST_BIT,VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,0,null,barrier,null);
            vkCmdBindPipeline(cmd,VK_PIPELINE_BIND_POINT_COMPUTE,instance.pipeline);
            vkCmdBindDescriptorSets(cmd,VK_PIPELINE_BIND_POINT_COMPUTE,instance.pipelineLayout,0,stack.longs(set),null);
            ByteBuffer push=stack.malloc(16).order(ByteOrder.nativeOrder());
            push.putInt(0,base).putInt(4,list).putInt(8,batch.count()).putInt(12,vertices.remaining()/4);
            vkCmdPushConstants(cmd,instance.pipelineLayout,VK_SHADER_STAGE_COMPUTE_BIT,0,push);
            vkCmdDispatch(cmd,groups,1,1);
            barrier.get(0).srcAccessMask(VK_ACCESS_SHADER_WRITE_BIT).dstAccessMask(VK_ACCESS_VERTEX_ATTRIBUTE_READ_BIT);
            vkCmdPipelineBarrier(cmd,VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,VK_PIPELINE_STAGE_VERTEX_INPUT_BIT,0,null,barrier,null);
            queue.endIfNeeded(command);
        }
        gpuQuads+=batch.count(); batches++; uploadedBytes+=metadataBytes+vertices.remaining();
        preparationNanos+=System.nanoTime()-start;
        return true;
    }
    public static long gpuQuads() { return gpuQuads; }
    /** Native CI probe: pool exhaustion must reject before recording a dispatch. */
    static long descriptorRejectionsForSmoke() { return descriptorRejections; }
    static long callbackStateRecoveriesForSmoke() { return callbackStateRecoveries; }
    public static String statistics() {
        return String.format(Locale.ROOT,"particle_billboard_lifetime inherited_render_attempts=%d exact_class_eligible=%d original_cpu_eligible_calls=%d packed_quads=%d gpu_quads=%d cpu_recovery_quads=%d layout_fallback_calls=%d callback_state_cpu_recoveries=%d unavailable_checks=%d compute_batches=%d capacity_rejected_batches=%d descriptor_rejected_batches=%d preparation_submit_ms=%.3f upload_kib=%.3f descriptor_allocations=%d live_descriptors=%d offset_scratch_kib=%d simulation=cpu custom_override_denominator=excluded",
                inheritedAttempts,eligibleAttempts,originalCpu,packed,gpuQuads,recoveryQuads,layoutFallbacks,callbackStateRecoveries,unavailableFallbacks,batches,capacityRejections,descriptorRejections,
                preparationNanos/1e6,uploadedBytes/1024.0,bindingsCreated,instance==null?0:instance.bindings.size(),instance==null?0:256);
    }
    public static void emitSummary() {
        if(REQUESTED) PerformanceProfiler.benchmarkEvent(statistics());
    }
    private void createDescriptorResources() {
        try(MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorSetLayoutBinding.Buffer bindings =
                    VkDescriptorSetLayoutBinding.calloc(1, stack);
            for(int i = 0; i < 1; ++i) {
                bindings.get(i)
                        .binding(i)
                        .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                        .descriptorCount(1)
                        .stageFlags(VK_SHADER_STAGE_COMPUTE_BIT)
                        .pImmutableSamplers(null);
            }

            VkDescriptorSetLayoutCreateInfo layoutInfo =
                    VkDescriptorSetLayoutCreateInfo.calloc(stack)
                            .sType$Default()
                            .pBindings(bindings);
            LongBuffer pLayout = stack.mallocLong(1);
            check(vkCreateDescriptorSetLayout(
                    Device.device, layoutInfo, null, pLayout),
                    "create particle billboard descriptor set layout");
            this.descriptorSetLayout = pLayout.get(0);

            VkDescriptorPoolSize.Buffer poolSizes =
                    VkDescriptorPoolSize.calloc(1, stack);
            poolSizes.get(0)
                    .type(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                    .descriptorCount(MAX_BINDINGS);
            VkDescriptorPoolCreateInfo poolInfo =
                    VkDescriptorPoolCreateInfo.calloc(stack)
                            .sType$Default()
                            .flags(VK_DESCRIPTOR_POOL_CREATE_FREE_DESCRIPTOR_SET_BIT)
                            .pPoolSizes(poolSizes)
                            .maxSets(MAX_BINDINGS);
            LongBuffer pPool = stack.mallocLong(1);
            check(vkCreateDescriptorPool(
                    Device.device, poolInfo, null, pPool),
                    "create particle billboard descriptor pool");
            this.descriptorPool = pPool.get(0);
        }
    }

    private void createPipelineLayout() {
        try(MemoryStack stack = MemoryStack.stackPush()) {
            VkPushConstantRange.Buffer pushRange =
                    VkPushConstantRange.calloc(1, stack);
            pushRange.get(0)
                    .stageFlags(VK_SHADER_STAGE_COMPUTE_BIT)
                    .offset(0)
                    .size(PUSH_CONSTANT_BYTES);

            VkPipelineLayoutCreateInfo layoutInfo =
                    VkPipelineLayoutCreateInfo.calloc(stack)
                            .sType$Default()
                            .pSetLayouts(stack.longs(this.descriptorSetLayout))
                            .pPushConstantRanges(pushRange);
            LongBuffer pLayout = stack.mallocLong(1);
            check(vkCreatePipelineLayout(
                    Device.device, layoutInfo, null, pLayout),
                    "create particle billboard pipeline layout");
            this.pipelineLayout = pLayout.get(0);
        }
    }

    private void createPipeline() {
        SPIRVUtils.SPIRV spirv = SPIRVUtils.compileShaderResource(
                "/assets/vulkanmod/shaders/particle/billboard_expand.comp",
                SPIRVUtils.ShaderKind.COMPUTE_SHADER);
        long shaderModule = VK_NULL_HANDLE;
        try(MemoryStack stack = MemoryStack.stackPush()) {
            VkShaderModuleCreateInfo moduleInfo =
                    VkShaderModuleCreateInfo.calloc(stack)
                            .sType$Default()
                            .pCode(spirv.bytecode());
            LongBuffer pModule = stack.mallocLong(1);
            check(vkCreateShaderModule(
                    Device.device, moduleInfo, null, pModule),
                    "create particle billboard shader module");
            shaderModule = pModule.get(0);

            VkPipelineShaderStageCreateInfo stageInfo =
                    VkPipelineShaderStageCreateInfo.calloc(stack)
                            .sType$Default()
                            .stage(VK_SHADER_STAGE_COMPUTE_BIT)
                            .module(shaderModule)
                            .pName(stack.UTF8("main"));

            VkComputePipelineCreateInfo.Buffer pipelineInfo =
                    VkComputePipelineCreateInfo.calloc(1, stack);
            pipelineInfo.get(0)
                    .sType$Default()
                    .stage(stageInfo)
                    .layout(this.pipelineLayout)
                    .basePipelineHandle(VK_NULL_HANDLE)
                    .basePipelineIndex(-1);
            LongBuffer pPipeline = stack.mallocLong(1);
            check(vkCreateComputePipelines(
                    Device.device, VK_NULL_HANDLE,
                    pipelineInfo, null, pPipeline),
                    "create particle billboard compute pipeline");
            this.pipeline = pPipeline.get(0);
        } finally {
            if(shaderModule != VK_NULL_HANDLE) {
                vkDestroyShaderModule(Device.device, shaderModule, null);
            }
            spirv.free();
        }
    }

    private static boolean graphicsQueueSupportsCompute() {
        Boolean cached = graphicsComputeSupported;
        if(cached != null) {
            return cached;
        }
        if(Device.physicalDevice == null) {
            return false;
        }

        try(MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer count = stack.ints(0);
            vkGetPhysicalDeviceQueueFamilyProperties(
                    Device.physicalDevice, count, null);
            VkQueueFamilyProperties.Buffer properties =
                    VkQueueFamilyProperties.malloc(count.get(0), stack);
            vkGetPhysicalDeviceQueueFamilyProperties(
                    Device.physicalDevice, count, properties);
            int graphicsFamily = Queue.getQueueFamilies().graphicsFamily;
            boolean supported =
                    (properties.get(graphicsFamily).queueFlags()
                            & VK_QUEUE_COMPUTE_BIT) != 0;
            graphicsComputeSupported = supported;
            return supported;
        }
    }

    private static void check(int result, String action) {
        if(result != VK_SUCCESS) {
            throw new RuntimeException("Failed to " + action + ": " + result);
        }
    }

    @Override public void close() {
        if(closed) return; closed=true;
        if(pipeline!=0) vkDestroyPipeline(Device.device,pipeline,null);
        if(pipelineLayout!=0) vkDestroyPipelineLayout(Device.device,pipelineLayout,null);
        if(descriptorPool!=0) vkDestroyDescriptorPool(Device.device,descriptorPool,null);
        if(descriptorSetLayout!=0) vkDestroyDescriptorSetLayout(Device.device,descriptorSetLayout,null);
        if(offsets!=null) { MemoryUtil.memFree(offsets); offsets=null; }
        bindings.clear();
    }
}
