package net.vulkanmod.vulkan.shader;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;

public final class SpirvNativeLifetimeTest {
    private SpirvNativeLifetimeTest() {
    }

    public static void main(String[] args) throws Exception {
        SpirvUtilsVisitor utils = inspect(
                "net/vulkanmod/vulkan/shader/SPIRVUtils.class",
                new SpirvUtilsVisitor());
        require(utils.compileReleasesOptions,
                "Shader compilation must release shaderc compile options");
        require(utils.compileReleasesFailedResult,
                "Shader compilation must release an untransferred shaderc result");
        require(utils.destroyReleasesCompiler,
                "SPIRVUtils teardown must release the retained shaderc compiler");

        SpirvVisitor spirv = inspect(
                "net/vulkanmod/vulkan/shader/SPIRVUtils$SPIRV.class",
                new SpirvVisitor());
        require(spirv.releasesShadercResult,
                "SPIRV.free() must release shaderc-owned results");
        require(spirv.releasesNativeBuffer,
                "SPIRV.free() must release memAlloc-owned bytecode");

        BuilderVisitor builder = inspect(
                "net/vulkanmod/vulkan/shader/Pipeline$Builder.class",
                new BuilderVisitor());
        require(builder.createReleasesCompiledShaders,
                "Graphics pipeline creation must release compiled SPIR-V wrappers");
        require(builder.failedReplacementFreesTemporarySpirv >= 2,
                "Failed shader replacement must release temporary vertex/fragment SPIR-V");
        require(builder.releaseHelperFreeCalls >= 2,
                "Builder release helper must release both compiled shader wrappers");

        VulkanVisitor vulkan = inspect(
                "net/vulkanmod/vulkan/Vulkan.class",
                new VulkanVisitor());
        require(vulkan.rendererCleanupOrder >= 0,
                "Vulkan teardown must clean renderer resources");
        require(vulkan.compilerDestroyOrder > vulkan.rendererCleanupOrder,
                "Shader compiler teardown must follow renderer resource teardown");
        require(vulkan.deviceDestroyOrder > vulkan.compilerDestroyOrder,
                "Shader compiler teardown must precede device destruction");

        System.out.println("SPIR-V native lifetime contract passed");
    }

    private static <T extends ClassVisitor> T inspect(String resource, T visitor) throws IOException {
        try(InputStream input = SpirvNativeLifetimeTest.class.getClassLoader()
                .getResourceAsStream(resource)) {
            if(input == null)
                throw new AssertionError("Could not load " + resource);
            new ClassReader(input).accept(visitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return visitor;
        }
    }

    private static void require(boolean condition, String message) {
        if(!condition)
            throw new AssertionError(message);
    }

    private static final class SpirvUtilsVisitor extends ClassVisitor {
        boolean compileReleasesOptions;
        boolean compileReleasesFailedResult;
        boolean destroyReleasesCompiler;

        SpirvUtilsVisitor() {
            super(Opcodes.ASM9);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            if("compileShader".equals(name)) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String methodName,
                                                String methodDescriptor, boolean isInterface) {
                        if(owner.equals("org/lwjgl/util/shaderc/Shaderc")
                                && methodName.equals("shaderc_compile_options_release"))
                            compileReleasesOptions = true;
                        if(owner.equals("org/lwjgl/util/shaderc/Shaderc")
                                && methodName.equals("shaderc_result_release"))
                            compileReleasesFailedResult = true;
                    }
                };
            }

            if("destroyCompiler".equals(name)) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String methodName,
                                                String methodDescriptor, boolean isInterface) {
                        if(owner.equals("org/lwjgl/util/shaderc/Shaderc")
                                && methodName.equals("shaderc_compiler_release"))
                            destroyReleasesCompiler = true;
                    }
                };
            }

            return null;
        }
    }

    private static final class SpirvVisitor extends ClassVisitor {
        boolean releasesShadercResult;
        boolean releasesNativeBuffer;

        SpirvVisitor() {
            super(Opcodes.ASM9);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            if(!"free".equals(name))
                return null;

            return new MethodVisitor(Opcodes.ASM9) {
                @Override
                public void visitMethodInsn(int opcode, String owner, String methodName,
                                            String methodDescriptor, boolean isInterface) {
                    if(owner.equals("org/lwjgl/util/shaderc/Shaderc")
                            && methodName.equals("shaderc_result_release"))
                        releasesShadercResult = true;
                    if(owner.equals("org/lwjgl/system/MemoryUtil")
                            && methodName.equals("memFree"))
                        releasesNativeBuffer = true;
                }
            };
        }
    }

    private static final class BuilderVisitor extends ClassVisitor {
        boolean createReleasesCompiledShaders;
        int failedReplacementFreesTemporarySpirv;
        int releaseHelperFreeCalls;

        BuilderVisitor() {
            super(Opcodes.ASM9);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            if("createGraphicsPipeline".equals(name)) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String methodName,
                                                String methodDescriptor, boolean isInterface) {
                        if(owner.equals("net/vulkanmod/vulkan/shader/Pipeline$Builder")
                                && methodName.equals("releaseCompiledShaders"))
                            createReleasesCompiledShaders = true;
                    }
                };
            }

            if("replaceCompiledShaders".equals(name)) {
                return countSpirvFreeCalls(true);
            }

            if("releaseCompiledShaders".equals(name)) {
                return countSpirvFreeCalls(false);
            }

            return null;
        }

        private MethodVisitor countSpirvFreeCalls(boolean replacement) {
            return new MethodVisitor(Opcodes.ASM9) {
                @Override
                public void visitMethodInsn(int opcode, String owner, String methodName,
                                            String methodDescriptor, boolean isInterface) {
                    if(owner.equals("net/vulkanmod/vulkan/shader/SPIRVUtils$SPIRV")
                            && methodName.equals("free")) {
                        if(replacement)
                            failedReplacementFreesTemporarySpirv++;
                        else
                            releaseHelperFreeCalls++;
                    }
                }
            };
        }
    }

    private static final class VulkanVisitor extends ClassVisitor {
        int sequence;
        int rendererCleanupOrder = -1;
        int compilerDestroyOrder = -1;
        int deviceDestroyOrder = -1;

        VulkanVisitor() {
            super(Opcodes.ASM9);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            if(!"cleanUp".equals(name))
                return null;

            return new MethodVisitor(Opcodes.ASM9) {
                @Override
                public void visitMethodInsn(int opcode, String owner, String methodName,
                                            String methodDescriptor, boolean isInterface) {
                    int order = sequence++;
                    if(owner.equals("net/vulkanmod/vulkan/Renderer")
                            && methodName.equals("cleanUpResources"))
                        rendererCleanupOrder = order;
                    if(owner.equals("net/vulkanmod/vulkan/shader/SPIRVUtils")
                            && methodName.equals("destroyCompiler"))
                        compilerDestroyOrder = order;
                    if(owner.equals("net/vulkanmod/vulkan/Device")
                            && methodName.equals("destroy"))
                        deviceDestroyOrder = order;
                }
            };
        }
    }
}
