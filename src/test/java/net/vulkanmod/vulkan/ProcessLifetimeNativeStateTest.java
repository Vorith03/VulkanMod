package net.vulkanmod.vulkan;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;

public final class ProcessLifetimeNativeStateTest {
    private ProcessLifetimeNativeStateTest() {
    }

    public static void main(String[] args) throws Exception {
        MappedBufferVisitor mapped = inspect(
                "net/vulkanmod/vulkan/util/MappedBuffer.class",
                new MappedBufferVisitor());
        require(mapped.freeUsesMemFree,
                "Owned MappedBuffer allocations must expose explicit native release");
        require(mapped.hasFreedField && mapped.freeReadsFreed && mapped.freeWritesFreed,
                "MappedBuffer.free() must be idempotent");
        require(mapped.freeReadsOwned,
                "MappedBuffer.free() must not release borrowed ByteBuffer storage");

        SyncVisitor sync = inspect(
                "net/vulkanmod/vulkan/Synchronization.class",
                new SyncVisitor());
        require(sync.cleanupFreesFenceBuffer,
                "Synchronization teardown must free its native fence buffer");
        require(sync.hasFreedGuard && sync.cleanupReadsGuard && sync.cleanupWritesGuard,
                "Synchronization native teardown must be idempotent");

        DrawerVisitor drawer = inspect(
                "net/vulkanmod/vulkan/Drawer.class",
                new DrawerVisitor());
        require(drawer.cleanupMemFreeCalls >= 2,
                "Drawer teardown must free both static native pointer buffers");
        require(drawer.hasFreedGuard && drawer.cleanupReadsGuard && drawer.cleanupWritesGuard,
                "Drawer native teardown must be idempotent");

        VRenderVisitor render = inspect(
                "net/vulkanmod/vulkan/VRenderSystem.class",
                new VRenderVisitor());
        require(render.cleanupMappedBuffers >= 10,
                "VRenderSystem teardown must release all owned mapped uniform buffers");
        require(render.cleanupClearColor,
                "VRenderSystem teardown must release its native clear-color buffer");
        require(render.hasFreedGuard && render.cleanupReadsGuard && render.cleanupWritesGuard,
                "VRenderSystem native teardown must be idempotent");

        DeviceInfoVisitor info = inspect(
                "net/vulkanmod/vulkan/DeviceInfo.class",
                new DeviceInfoVisitor());
        require(info.closeFreeCalls >= 2,
                "DeviceInfo.close() must release its retained feature structs");
        require(info.hasClosedField && info.closeReadsGuard && info.closeWritesGuard,
                "DeviceInfo.close() must be idempotent");

        DeviceVisitor device = inspect(
                "net/vulkanmod/vulkan/Device.class",
                new DeviceVisitor());
        require(!device.hasStaticSurfaceProperties,
                "Device must not retain stack-backed surface properties after device selection");
        require(device.destroyCleansPresentQueue,
                "Device.destroy() must include the present queue in queue teardown");
        require(device.destroyClosesDeviceInfo,
                "Device.destroy() must close DeviceInfo");
        require(device.destroyStructFreeCalls >= 2,
                "Device.destroy() must release physical-device property structs");

        VulkanVisitor vulkan = inspect(
                "net/vulkanmod/vulkan/Vulkan.class",
                new VulkanVisitor());
        require(vulkan.rendererCleanupOrder >= 0,
                "Vulkan teardown must first retire renderer resources");
        require(vulkan.syncCleanupOrder > vulkan.rendererCleanupOrder,
                "Synchronization native state must retire after renderer resources");
        require(vulkan.drawerCleanupOrder > vulkan.rendererCleanupOrder,
                "Drawer native state must retire after renderer resources");
        require(vulkan.renderStateCleanupOrder > vulkan.rendererCleanupOrder,
                "VRenderSystem native state must retire after renderer resources");
        require(vulkan.deviceDestroyOrder > vulkan.renderStateCleanupOrder,
                "Process-lifetime native state must retire before Device.destroy()");

        System.out.println("Process-lifetime native ownership contract passed");
    }

    private static <T extends ClassVisitor> T inspect(String resource, T visitor) throws IOException {
        try(InputStream input = ProcessLifetimeNativeStateTest.class.getClassLoader()
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

    private static final class MappedBufferVisitor extends ClassVisitor {
        boolean hasFreedField;
        boolean freeUsesMemFree;
        boolean freeReadsFreed;
        boolean freeWritesFreed;
        boolean freeReadsOwned;

        MappedBufferVisitor() { super(Opcodes.ASM9); }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor,
                                       String signature, Object value) {
            if("freed".equals(name) && "Z".equals(descriptor))
                hasFreedField = true;
            return null;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            if(!"free".equals(name))
                return null;
            return new MethodVisitor(Opcodes.ASM9) {
                @Override
                public void visitFieldInsn(int opcode, String owner, String fieldName,
                                           String fieldDescriptor) {
                    if(!owner.equals("net/vulkanmod/vulkan/util/MappedBuffer"))
                        return;
                    if("freed".equals(fieldName)) {
                        if(opcode == Opcodes.GETFIELD)
                            freeReadsFreed = true;
                        if(opcode == Opcodes.PUTFIELD)
                            freeWritesFreed = true;
                    }
                    if("owned".equals(fieldName) && opcode == Opcodes.GETFIELD)
                        freeReadsOwned = true;
                }

                @Override
                public void visitMethodInsn(int opcode, String owner, String methodName,
                                            String methodDescriptor, boolean isInterface) {
                    if(owner.equals("org/lwjgl/system/MemoryUtil")
                            && methodName.equals("memFree"))
                        freeUsesMemFree = true;
                }
            };
        }
    }

    private static final class SyncVisitor extends ClassVisitor {
        boolean hasFreedGuard;
        boolean cleanupFreesFenceBuffer;
        boolean cleanupReadsGuard;
        boolean cleanupWritesGuard;

        SyncVisitor() { super(Opcodes.ASM9); }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor,
                                       String signature, Object value) {
            if("nativeStateFreed".equals(name) && "Z".equals(descriptor))
                hasFreedGuard = true;
            return null;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            if(!"cleanUpNativeState".equals(name))
                return null;
            return new MethodVisitor(Opcodes.ASM9) {
                @Override
                public void visitFieldInsn(int opcode, String owner, String fieldName,
                                           String fieldDescriptor) {
                    if(!owner.equals("net/vulkanmod/vulkan/Synchronization")
                            || !"nativeStateFreed".equals(fieldName))
                        return;
                    if(opcode == Opcodes.GETFIELD)
                        cleanupReadsGuard = true;
                    if(opcode == Opcodes.PUTFIELD)
                        cleanupWritesGuard = true;
                }

                @Override
                public void visitMethodInsn(int opcode, String owner, String methodName,
                                            String methodDescriptor, boolean isInterface) {
                    if(owner.equals("org/lwjgl/system/MemoryUtil")
                            && methodName.equals("memFree"))
                        cleanupFreesFenceBuffer = true;
                }
            };
        }
    }

    private static final class DrawerVisitor extends ClassVisitor {
        boolean hasFreedGuard;
        int cleanupMemFreeCalls;
        boolean cleanupReadsGuard;
        boolean cleanupWritesGuard;

        DrawerVisitor() { super(Opcodes.ASM9); }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor,
                                       String signature, Object value) {
            if("nativeStateFreed".equals(name) && "Z".equals(descriptor))
                hasFreedGuard = true;
            return null;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            if(!"destroyNativeState".equals(name))
                return null;
            return new MethodVisitor(Opcodes.ASM9) {
                @Override
                public void visitFieldInsn(int opcode, String owner, String fieldName,
                                           String fieldDescriptor) {
                    if(!owner.equals("net/vulkanmod/vulkan/Drawer")
                            || !"nativeStateFreed".equals(fieldName))
                        return;
                    if(opcode == Opcodes.GETSTATIC)
                        cleanupReadsGuard = true;
                    if(opcode == Opcodes.PUTSTATIC)
                        cleanupWritesGuard = true;
                }

                @Override
                public void visitMethodInsn(int opcode, String owner, String methodName,
                                            String methodDescriptor, boolean isInterface) {
                    if(owner.equals("org/lwjgl/system/MemoryUtil")
                            && methodName.equals("memFree"))
                        cleanupMemFreeCalls++;
                }
            };
        }
    }

    private static final class VRenderVisitor extends ClassVisitor {
        boolean hasFreedGuard;
        int cleanupMappedBuffers;
        boolean cleanupClearColor;
        boolean cleanupReadsGuard;
        boolean cleanupWritesGuard;

        VRenderVisitor() { super(Opcodes.ASM9); }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor,
                                       String signature, Object value) {
            if("nativeStateFreed".equals(name) && "Z".equals(descriptor))
                hasFreedGuard = true;
            return null;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            if(!"cleanUpNativeState".equals(name))
                return null;
            return new MethodVisitor(Opcodes.ASM9) {
                @Override
                public void visitFieldInsn(int opcode, String owner, String fieldName,
                                           String fieldDescriptor) {
                    if(!owner.equals("net/vulkanmod/vulkan/VRenderSystem")
                            || !"nativeStateFreed".equals(fieldName))
                        return;
                    if(opcode == Opcodes.GETSTATIC)
                        cleanupReadsGuard = true;
                    if(opcode == Opcodes.PUTSTATIC)
                        cleanupWritesGuard = true;
                }

                @Override
                public void visitMethodInsn(int opcode, String owner, String methodName,
                                            String methodDescriptor, boolean isInterface) {
                    if(owner.equals("net/vulkanmod/vulkan/util/MappedBuffer")
                            && methodName.equals("free"))
                        cleanupMappedBuffers++;
                    if(owner.equals("org/lwjgl/system/MemoryUtil")
                            && methodName.equals("memFree"))
                        cleanupClearColor = true;
                }
            };
        }
    }

    private static final class DeviceInfoVisitor extends ClassVisitor {
        boolean hasClosedField;
        int closeFreeCalls;
        boolean closeReadsGuard;
        boolean closeWritesGuard;

        DeviceInfoVisitor() { super(Opcodes.ASM9); }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor,
                                       String signature, Object value) {
            if("closed".equals(name) && "Z".equals(descriptor))
                hasClosedField = true;
            return null;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            if(!"close".equals(name))
                return null;
            return new MethodVisitor(Opcodes.ASM9) {
                @Override
                public void visitFieldInsn(int opcode, String owner, String fieldName,
                                           String fieldDescriptor) {
                    if(!owner.equals("net/vulkanmod/vulkan/DeviceInfo")
                            || !"closed".equals(fieldName))
                        return;
                    if(opcode == Opcodes.GETFIELD)
                        closeReadsGuard = true;
                    if(opcode == Opcodes.PUTFIELD)
                        closeWritesGuard = true;
                }

                @Override
                public void visitMethodInsn(int opcode, String owner, String methodName,
                                            String methodDescriptor, boolean isInterface) {
                    if((owner.equals("org/lwjgl/vulkan/VkPhysicalDeviceFeatures2")
                            || owner.equals("org/lwjgl/vulkan/VkPhysicalDeviceVulkan11Features"))
                            && methodName.equals("free"))
                        closeFreeCalls++;
                }
            };
        }
    }

    private static final class DeviceVisitor extends ClassVisitor {
        boolean hasStaticSurfaceProperties;
        boolean destroyCleansPresentQueue;
        boolean destroyClosesDeviceInfo;
        int destroyStructFreeCalls;
        DeviceVisitor() { super(Opcodes.ASM9); }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor,
                                       String signature, Object value) {
            if("surfaceProperties".equals(name)
                    && "Lnet/vulkanmod/vulkan/Device$SurfaceProperties;".equals(descriptor)
                    && (access & Opcodes.ACC_STATIC) != 0)
                hasStaticSurfaceProperties = true;
            return null;
        }
        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            if(!"destroy".equals(name))
                return null;
            return new MethodVisitor(Opcodes.ASM9) {
                @Override
                public void visitMethodInsn(int opcode, String owner, String methodName,
                                            String methodDescriptor, boolean isInterface) {
                    if(owner.equals("net/vulkanmod/vulkan/queue/PresentQueue")
                            && methodName.equals("cleanUp"))
                        destroyCleansPresentQueue = true;
                    if(owner.equals("net/vulkanmod/vulkan/DeviceInfo")
                            && methodName.equals("close"))
                        destroyClosesDeviceInfo = true;
                    if((owner.equals("org/lwjgl/vulkan/VkPhysicalDeviceProperties")
                            || owner.equals("org/lwjgl/vulkan/VkPhysicalDeviceMemoryProperties"))
                            && methodName.equals("free"))
                        destroyStructFreeCalls++;
                }
            };
        }
    }

    private static final class VulkanVisitor extends ClassVisitor {
        int sequence;
        int rendererCleanupOrder = -1;
        int syncCleanupOrder = -1;
        int drawerCleanupOrder = -1;
        int renderStateCleanupOrder = -1;
        int deviceDestroyOrder = -1;

        VulkanVisitor() { super(Opcodes.ASM9); }

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
                    if(owner.equals("net/vulkanmod/vulkan/Synchronization")
                            && methodName.equals("cleanUpNativeState"))
                        syncCleanupOrder = order;
                    if(owner.equals("net/vulkanmod/vulkan/Drawer")
                            && methodName.equals("destroyNativeState"))
                        drawerCleanupOrder = order;
                    if(owner.equals("net/vulkanmod/vulkan/VRenderSystem")
                            && methodName.equals("cleanUpNativeState"))
                        renderStateCleanupOrder = order;
                    if(owner.equals("net/vulkanmod/vulkan/Device")
                            && methodName.equals("destroy"))
                        deviceDestroyOrder = order;
                }
            };
        }
    }
}
