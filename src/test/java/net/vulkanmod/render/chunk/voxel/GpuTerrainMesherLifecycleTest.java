package net.vulkanmod.render.chunk.voxel;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;

public final class GpuTerrainMesherLifecycleTest {
    private GpuTerrainMesherLifecycleTest() {
    }

    public static void main(String[] args) throws Exception {
        MesherVisitor mesher = inspect(
                "net/vulkanmod/render/chunk/voxel/GpuTerrainSectionMesher.class",
                new MesherVisitor());
        require(mesher.constructorCatchesRuntime,
                "GpuTerrainSectionMesher constructor must catch RuntimeException");
        require(mesher.constructorCatchesError,
                "GpuTerrainSectionMesher constructor must catch Error");
        require(mesher.constructorDestroysPartialResources,
                "GpuTerrainSectionMesher constructor must destroy partial native resources on failure");
        require(mesher.shutdownCompletesPending,
                "Device-idle mesher shutdown must drain pending completions");
        require(mesher.shutdownCloses,
                "Device-idle mesher shutdown must close native descriptor/pipeline resources");

        BridgeVisitor bridge = inspect(
                "net/vulkanmod/render/chunk/GpuTerrainSectionMesherBridge.class",
                new BridgeVisitor());
        require(bridge.publicClass,
                "Gpu terrain bridge must expose its global lifecycle owner hook");
        require(!bridge.hasMesherAttempted,
                "One-shot mesherAttempted state would make transient initialization failure unrecoverable");
        require(bridge.hasBoundedRetryState,
                "GPU terrain bridge must retain bounded initialization retry state");
        require(bridge.maxInitializationAttempts == 2,
                "GPU terrain mesher initialization retry budget must remain explicitly bounded at two attempts");
        require(bridge.shutdownMesher,
                "GPU terrain bridge shutdown must retire the production mesher");
        require(bridge.shutdownModelStore,
                "GPU terrain bridge shutdown must retire the model GPU store");

        VulkanVisitor vulkan = inspect(
                "net/vulkanmod/vulkan/Vulkan.class",
                new VulkanVisitor());
        require(vulkan.waitIdleOrder >= 0,
                "Vulkan.cleanUp() must wait for the device");
        require(vulkan.bridgeShutdownOrder > vulkan.waitIdleOrder,
                "GPU terrain lifecycle shutdown must run after device idle");
        require(vulkan.deviceDestroyOrder > vulkan.bridgeShutdownOrder,
                "GPU terrain lifecycle shutdown must run before VkDevice destruction");

        System.out.println("GPU terrain mesher lifecycle contract passed");
    }

    private static <T extends ClassVisitor> T inspect(String resource, T visitor) throws IOException {
        try (InputStream input = GpuTerrainMesherLifecycleTest.class.getClassLoader()
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

    private static final class MesherVisitor extends ClassVisitor {
        boolean constructorCatchesRuntime;
        boolean constructorCatchesError;
        boolean constructorDestroysPartialResources;
        boolean shutdownCompletesPending;
        boolean shutdownCloses;

        MesherVisitor() {
            super(Opcodes.ASM9);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            if("<init>".equals(name)) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitTryCatchBlock(org.objectweb.asm.Label start,
                                                   org.objectweb.asm.Label end,
                                                   org.objectweb.asm.Label handler,
                                                   String type) {
                        if("java/lang/RuntimeException".equals(type))
                            constructorCatchesRuntime = true;
                        if("java/lang/Error".equals(type))
                            constructorCatchesError = true;
                    }

                    @Override
                    public void visitMethodInsn(int opcode, String owner, String methodName,
                                                String methodDescriptor, boolean isInterface) {
                        if(owner.equals("net/vulkanmod/render/chunk/voxel/GpuTerrainSectionMesher")
                                && methodName.equals("destroyResources"))
                            constructorDestroysPartialResources = true;
                    }
                };
            }

            if("shutdownAfterDeviceIdle".equals(name)) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String methodName,
                                                String methodDescriptor, boolean isInterface) {
                        if(owner.equals("net/vulkanmod/render/chunk/voxel/GpuTerrainSectionMesher")
                                && methodName.equals("completePending"))
                            shutdownCompletesPending = true;
                        if(owner.equals("net/vulkanmod/render/chunk/voxel/GpuTerrainSectionMesher")
                                && methodName.equals("close"))
                            shutdownCloses = true;
                    }
                };
            }

            return null;
        }
    }

    private static final class BridgeVisitor extends ClassVisitor {
        boolean publicClass;
        boolean hasMesherAttempted;
        boolean hasBoundedRetryState;
        int maxInitializationAttempts = -1;
        boolean shutdownMesher;
        boolean shutdownModelStore;

        BridgeVisitor() {
            super(Opcodes.ASM9);
        }

        @Override
        public void visit(int version, int access, String name, String signature,
                          String superName, String[] interfaces) {
            publicClass = (access & Opcodes.ACC_PUBLIC) != 0;
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor,
                                       String signature, Object value) {
            if("mesherAttempted".equals(name))
                hasMesherAttempted = true;
            if("mesherInitializationAttempts".equals(name)
                    && "I".equals(descriptor))
                hasBoundedRetryState = true;
            if("MAX_MESHER_INITIALIZATION_ATTEMPTS".equals(name)
                    && "I".equals(descriptor) && value instanceof Integer limit)
                maxInitializationAttempts = limit;
            return null;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            if(!"shutdownAfterDeviceIdle".equals(name))
                return null;

            return new MethodVisitor(Opcodes.ASM9) {
                @Override
                public void visitMethodInsn(int opcode, String owner, String methodName,
                                            String methodDescriptor, boolean isInterface) {
                    if(owner.equals("net/vulkanmod/render/chunk/voxel/GpuTerrainSectionMesher")
                            && methodName.equals("shutdownAfterDeviceIdle"))
                        shutdownMesher = true;
                    if(owner.equals("net/vulkanmod/render/chunk/voxel/GpuTerrainModelGpuStore")
                            && methodName.equals("close"))
                        shutdownModelStore = true;
                }
            };
        }
    }

    private static final class VulkanVisitor extends ClassVisitor {
        int sequence;
        int waitIdleOrder = -1;
        int bridgeShutdownOrder = -1;
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
                    if(owner.equals("org/lwjgl/vulkan/VK10")
                            && methodName.equals("vkDeviceWaitIdle"))
                        waitIdleOrder = order;
                    if(owner.equals("net/vulkanmod/render/chunk/GpuTerrainSectionMesherBridge")
                            && methodName.equals("shutdownAfterDeviceIdle"))
                        bridgeShutdownOrder = order;
                    if(owner.equals("net/vulkanmod/vulkan/Device")
                            && methodName.equals("destroy"))
                        deviceDestroyOrder = order;
                }
            };
        }
    }
}
