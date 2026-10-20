package net.vulkanmod.vulkan.shader;

import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkComputePipelineCreateInfo;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.List;
import static org.lwjgl.vulkan.VK10.*;

/** Runs the real construction coordinator/structs with bounded failing native calls. */
public final class ComputePipelineFactoryFailureTest {
    public static void main(String[] args) {
        verify("compile", List.of("compile"));
        verify("module", List.of("compile", "module", "release"));
        verify("pipeline", List.of("compile", "module", "pipeline", "destroyModule", "release"));
        verify("partial", List.of("compile", "module", "pipeline", "destroyPipeline", "destroyModule", "release"));
        verify("throw", List.of("compile", "module", "pipeline", "destroyModule", "release"));
        verify("success", List.of("compile", "module", "pipeline", "destroyModule", "release"));
        System.out.println("Compute construction failure cleanup passed: compile/module/pipeline/partial/throw/success");
    }

    private static void verify(String mode, List<String> expected) {
        List<String> events = new ArrayList<>();
        RuntimeException injected = new RuntimeException("injected " + mode);
        SPIRVUtils.SPIRV[] compiled = new SPIRVUtils.SPIRV[1];
        var calls = new ComputePipelineFactory.Calls() {
            public SPIRVUtils.SPIRV compile(String resource) {
                events.add("compile");
                require(resource.equals("fixture"), "Resource identity");
                if (mode.equals("compile")) throw injected;
                return compiled[0] = SPIRVUtils.SPIRV.nativeBuffer(MemoryUtil.memCalloc(4));
            }
            public int module(VkShaderModuleCreateInfo info, LongBuffer result) {
                events.add("module");
                require(info.sType() == VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO
                        && info.pCode().remaining() == 4 && result.get(0) == 0, "Module ABI and zero handle");
                if (mode.equals("module")) return VK_ERROR_OUT_OF_HOST_MEMORY;
                result.put(0, 17L);
                return VK_SUCCESS;
            }
            public int pipeline(VkComputePipelineCreateInfo.Buffer infos, LongBuffer result) {
                events.add("pipeline");
                var info = infos.get(0);
                require(infos.remaining() == 1 && info.sType() == VK_STRUCTURE_TYPE_COMPUTE_PIPELINE_CREATE_INFO
                        && info.layout() == 23L && info.basePipelineHandle() == 0 && info.basePipelineIndex() == -1
                        && info.stage().module() == 17L && info.stage().stage() == VK_SHADER_STAGE_COMPUTE_BIT
                        && info.stage().pNameString().equals("main") && result.get(0) == 0, "Pipeline ABI");
                if (mode.equals("throw")) throw injected;
                if (!mode.equals("pipeline")) result.put(0, 31L);
                return mode.equals("success") ? VK_SUCCESS : VK_ERROR_OUT_OF_DEVICE_MEMORY;
            }
            public void destroyModule(long module) {
                require(module == 17L, "Exact module identity"); events.add("destroyModule");
            }
            public void destroyPipeline(long pipeline) {
                require(pipeline == 31L, "Exact partial pipeline identity"); events.add("destroyPipeline");
            }
            public void release(SPIRVUtils.SPIRV spirv) {
                require(spirv == compiled[0], "Exact SPIR-V identity"); events.add("release"); spirv.free();
            }
        };
        try {
            long pipeline = ComputePipelineFactory.create("fixture", 23L, calls);
            require(mode.equals("success") && pipeline == 31L, "Only success transfers pipeline ownership");
        } catch (RuntimeException failure) {
            if (mode.equals("compile") || mode.equals("throw")) require(failure == injected, "Original failure identity");
            else require(!mode.equals("success") && failure instanceof IllegalStateException
                    && failure.getMessage().contains(mode.equals("module") ? "shader module" : "compute pipeline"),
                    "Native failure context");
        }
        require(events.equals(expected), mode + " release sequence/count: " + events);
        if (compiled[0] != null) {
            try { compiled[0].bytecode(); throw new AssertionError("SPIR-V retained after " + mode); }
            catch (IllegalStateException expectedClosed) { }
        }
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
