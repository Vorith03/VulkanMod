package net.vulkanmod.vulkan.texture;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

public final class TextureSamplerStateTest {
    private TextureSamplerStateTest() {
    }

    public static void main(String[] args) throws Exception {
        Set<Byte> keys = new HashSet<>();
        for(boolean blur : new boolean[]{false, true}) {
            for(boolean clamp : new boolean[]{false, true}) {
                for(boolean mipmap : new boolean[]{false, true}) {
                    keys.add(VulkanImage.samplerKey(blur, clamp, mipmap));
                }
            }
        }
        require(keys.size() == 8,
                "Sampler cache key must distinguish blur, mipmap, and clamp independently");
        require(VulkanImage.samplerKey(false, false, false)
                        != VulkanImage.samplerKey(false, true, false),
                "Clamp and repeat samplers must not share a cache key");

        VulkanImageVisitor image = inspect(
                "net/vulkanmod/vulkan/texture/VulkanImage.class",
                new VulkanImageVisitor());
        require(image.hasClampField,
                "VulkanImage must retain wrap/clamp state");
        require(image.createUsesSamplerKey && image.updateUsesSamplerKey,
                "Sampler creation and lookup must use the full sampler key");
        require(image.createStoresClamp && image.updateStoresClamp,
                "Sampler creation/update must retain the selected clamp state");

        AbstractTextureVisitor mixin = inspect(
                "net/vulkanmod/mixin/texture/MAbstractTexture.class",
                new AbstractTextureVisitor());
        require(mixin.filterReadsClamp,
                "AbstractTexture.setFilter() must preserve the current Vulkan wrap state");
        require(mixin.filterUpdatesSampler,
                "AbstractTexture.setFilter() must still update the Vulkan sampler");

        System.out.println("Texture sampler clamp/filter identity contract passed");
    }

    private static <T extends ClassVisitor> T inspect(String resource, T visitor) throws IOException {
        try(InputStream input = TextureSamplerStateTest.class.getClassLoader()
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

    private static final class VulkanImageVisitor extends ClassVisitor {
        boolean hasClampField;
        boolean createUsesSamplerKey;
        boolean updateUsesSamplerKey;
        boolean createStoresClamp;
        boolean updateStoresClamp;

        VulkanImageVisitor() {
            super(Opcodes.ASM9);
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor,
                                       String signature, Object value) {
            if("clamp".equals(name) && "Z".equals(descriptor))
                hasClampField = true;
            return null;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            boolean create = "createTextureSampler".equals(name);
            boolean update = "updateTextureSampler".equals(name);
            if(!create && !update)
                return null;

            return new MethodVisitor(Opcodes.ASM9) {
                @Override
                public void visitMethodInsn(int opcode, String owner, String methodName,
                                            String methodDescriptor, boolean isInterface) {
                    if(owner.equals("net/vulkanmod/vulkan/texture/VulkanImage")
                            && methodName.equals("samplerKey")) {
                        if(create)
                            createUsesSamplerKey = true;
                        else
                            updateUsesSamplerKey = true;
                    }
                }

                @Override
                public void visitFieldInsn(int opcode, String owner, String fieldName,
                                           String fieldDescriptor) {
                    if(opcode == Opcodes.PUTFIELD
                            && owner.equals("net/vulkanmod/vulkan/texture/VulkanImage")
                            && fieldName.equals("clamp")) {
                        if(create)
                            createStoresClamp = true;
                        else
                            updateStoresClamp = true;
                    }
                }
            };
        }
    }

    private static final class AbstractTextureVisitor extends ClassVisitor {
        boolean filterReadsClamp;
        boolean filterUpdatesSampler;

        AbstractTextureVisitor() {
            super(Opcodes.ASM9);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            if(!"setFilter".equals(name))
                return null;

            return new MethodVisitor(Opcodes.ASM9) {
                @Override
                public void visitMethodInsn(int opcode, String owner, String methodName,
                                            String methodDescriptor, boolean isInterface) {
                    if(owner.equals("net/vulkanmod/vulkan/texture/VulkanImage")
                            && methodName.equals("isClamp"))
                        filterReadsClamp = true;
                    if(owner.equals("net/vulkanmod/vulkan/texture/VulkanImage")
                            && methodName.equals("updateTextureSampler"))
                        filterUpdatesSampler = true;
                }
            };
        }
    }
}
