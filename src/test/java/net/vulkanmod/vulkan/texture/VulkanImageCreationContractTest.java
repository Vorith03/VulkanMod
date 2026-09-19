package net.vulkanmod.vulkan.texture;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;

public final class VulkanImageCreationContractTest {
    private static final String RESOURCE =
            "net/vulkanmod/vulkan/texture/VulkanImage.class";

    private VulkanImageCreationContractTest() {
    }

    public static void main(String[] args) throws Exception {
        ContractVisitor visitor = inspect();

        require(!visitor.createImagePrintsFailure,
                "VulkanImage.createImage() must not swallow/print allocation failure");
        require(visitor.textureFactoryTransactional,
                "Texture image factory must clean partial native construction on failure");
        require(visitor.depthFactoryTransactional,
                "Depth image factory must clean partial native construction on failure");
        require(visitor.samplerFailureDestroysSampler,
                "Sampler construction must destroy a just-created sampler if Java-side publication fails");
        require(visitor.imageViewDestroyOrder >= 0,
                "Native image cleanup must destroy the image view");
        require(visitor.imageFreeOrder > visitor.imageViewDestroyOrder,
                "Native image cleanup must destroy the view before freeing the image allocation");

        System.out.println("Vulkan image transactional creation contract passed");
    }

    private static ContractVisitor inspect() throws IOException {
        try (InputStream input = VulkanImageCreationContractTest.class.getClassLoader()
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
        boolean createImagePrintsFailure;
        boolean textureFactoryTransactional;
        boolean depthFactoryTransactional;
        boolean samplerFailureDestroysSampler;
        int cleanupSequence;
        int imageViewDestroyOrder = -1;
        int imageFreeOrder = -1;

        ContractVisitor() {
            super(Opcodes.ASM9);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            if("createImage".equals(name)) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String methodName,
                                                String methodDescriptor, boolean isInterface) {
                        if(methodName.equals("printStackTrace"))
                            createImagePrintsFailure = true;
                    }
                };
            }

            if("createTextureImage".equals(name) || "createDepthImage".equals(name)) {
                final boolean texture = "createTextureImage".equals(name);
                return new MethodVisitor(Opcodes.ASM9) {
                    private boolean catchesRuntime;
                    private boolean catchesError;
                    private boolean callsCleanup;

                    @Override
                    public void visitTryCatchBlock(org.objectweb.asm.Label start,
                                                   org.objectweb.asm.Label end,
                                                   org.objectweb.asm.Label handler,
                                                   String type) {
                        if("java/lang/RuntimeException".equals(type))
                            catchesRuntime = true;
                        if("java/lang/Error".equals(type))
                            catchesError = true;
                    }

                    @Override
                    public void visitMethodInsn(int opcode, String owner, String methodName,
                                                String methodDescriptor, boolean isInterface) {
                        if(owner.equals("net/vulkanmod/vulkan/texture/VulkanImage")
                                && methodName.equals("destroyFailedConstruction"))
                            callsCleanup = true;
                    }

                    @Override
                    public void visitEnd() {
                        boolean transactional = catchesRuntime && catchesError && callsCleanup;
                        if(texture)
                            textureFactoryTransactional = transactional;
                        else
                            depthFactoryTransactional = transactional;
                    }
                };
            }

            if("createTextureSampler".equals(name)) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String methodName,
                                                String methodDescriptor, boolean isInterface) {
                        if(owner.equals("org/lwjgl/vulkan/VK10")
                                && methodName.equals("vkDestroySampler"))
                            samplerFailureDestroysSampler = true;
                    }
                };
            }

            if("destroyNativeResources".equals(name)) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String methodName,
                                                String methodDescriptor, boolean isInterface) {
                        int order = cleanupSequence++;
                        if(owner.equals("org/lwjgl/vulkan/VK10")
                                && methodName.equals("vkDestroyImageView"))
                            imageViewDestroyOrder = order;
                        if(owner.equals("net/vulkanmod/vulkan/memory/MemoryManager")
                                && methodName.equals("freeImage"))
                            imageFreeOrder = order;
                    }
                };
            }

            return null;
        }
    }
}
