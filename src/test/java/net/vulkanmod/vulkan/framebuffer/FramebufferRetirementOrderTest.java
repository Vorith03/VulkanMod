package net.vulkanmod.vulkan.framebuffer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;

public final class FramebufferRetirementOrderTest {
    private static final String RESOURCE =
            "net/vulkanmod/vulkan/framebuffer/Framebuffer.class";

    private FramebufferRetirementOrderTest() {
    }

    public static void main(String[] args) throws Exception {
        ContractVisitor visitor = inspect();
        require(visitor.cleanupFrameOps == 1,
                "Framebuffer.cleanUp() must enqueue one ordered retirement unit");
        require(!visitor.cleanupCallsDeferredImageFree,
                "Framebuffer.cleanUp() must not separately queue attachment image retirement");
        require(visitor.destroyFramebufferOrder >= 0,
                "Framebuffer retirement helper must destroy VkFramebuffer handles");
        require(visitor.firstImageFreeOrder > visitor.destroyFramebufferOrder,
                "Framebuffer handles must be destroyed before attachment views/images");
        require(visitor.imageFreeCalls >= 1,
                "Framebuffer retirement helper must retire captured attachments");

        System.out.println("Framebuffer attachment retirement order contract passed");
    }

    private static ContractVisitor inspect() throws IOException {
        try (InputStream input = FramebufferRetirementOrderTest.class.getClassLoader()
                .getResourceAsStream(RESOURCE)) {
            if(input == null)
                throw new AssertionError("Could not load " + RESOURCE);

            ContractVisitor visitor = new ContractVisitor();
            new ClassReader(input).accept(visitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return visitor;
        }
    }

    private static void require(boolean condition, String message) {
        if(!condition)
            throw new AssertionError(message);
    }

    private static final class ContractVisitor extends ClassVisitor {
        int cleanupFrameOps;
        boolean cleanupCallsDeferredImageFree;
        int sequence;
        int destroyFramebufferOrder = -1;
        int firstImageFreeOrder = -1;
        int imageFreeCalls;

        ContractVisitor() {
            super(Opcodes.ASM9);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            if("cleanUp".equals(name)) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String methodName,
                                                String methodDescriptor, boolean isInterface) {
                        if(owner.equals("net/vulkanmod/vulkan/memory/MemoryManager")
                                && methodName.equals("addFrameOp"))
                            cleanupFrameOps++;
                        if(owner.equals("net/vulkanmod/vulkan/texture/VulkanImage")
                                && methodName.equals("free"))
                            cleanupCallsDeferredImageFree = true;
                    }
                };
            }

            if("retireNativeResources".equals(name)) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String methodName,
                                                String methodDescriptor, boolean isInterface) {
                        int order = sequence++;
                        if(owner.equals("org/lwjgl/vulkan/VK10")
                                && methodName.equals("vkDestroyFramebuffer"))
                            destroyFramebufferOrder = order;
                        if(owner.equals("net/vulkanmod/vulkan/texture/VulkanImage")
                                && methodName.equals("doFree")) {
                            if(firstImageFreeOrder < 0)
                                firstImageFreeOrder = order;
                            imageFreeCalls++;
                        }
                    }
                };
            }

            return null;
        }
    }
}
