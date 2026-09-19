package net.vulkanmod.vulkan.texture;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.util.IdentityHashMap;
import java.util.Map;

public final class LegacyTextureUnitContractTest {
    private LegacyTextureUnitContractTest() {
    }

    public static void main(String[] args) throws Exception {
        RenderSystemVisitor renderSystem = inspect(
                "net/vulkanmod/mixin/render/RenderSystemMixin.class",
                new RenderSystemVisitor());
        require(renderSystem.activeTextureUpdatesSelector,
                "RenderSystem.activeTexture() must update VulkanMod's active texture unit");

        GlStateVisitor glState = inspect(
                "net/vulkanmod/mixin/render/GlStateManagerM.class",
                new GlStateVisitor());
        require(glState.activeTextureUpdatesSelector,
                "GlStateManager._activeTexture() must update VulkanMod's active texture unit");
        require(glState.bindDelegatesToGlTexture,
                "GlStateManager._bindTexture() must delegate through emulated GL texture state");

        GlTextureVisitor glTexture = inspect(
                "net/vulkanmod/gl/GlTexture.class",
                new GlTextureVisitor());
        require(glTexture.hasPerUnitIds && glTexture.hasPerUnitTextures,
                "GlTexture must retain bindings per active texture unit");
        require(glTexture.bindReadsActiveUnit,
                "GlTexture.bindTexture() must bind the currently active unit");
        require(glTexture.bindUpdatesSelector,
                "GlTexture.bindTexture() must update the matching Vulkan selector unit");

        SelectorVisitor selector = inspect(
                "net/vulkanmod/vulkan/texture/VTextureSelector.class",
                new SelectorVisitor());
        require(selector.uploadReadsActiveUnitBinding,
                "Texture upload must resolve its destination from the active legacy unit");
        require(selector.hasBindActiveTexture,
                "VTextureSelector must expose active-unit binding");
        require(selector.hasLegacyUnitLookup,
                "VTextureSelector must expose coherent legacy-unit lookup");
        require("overlayTexture".equals(selector.shaderSlot1Field)
                        && "lightTexture".equals(selector.shaderSlot2Field),
                "Shader sampler slots must retain Sampler1=overlay and Sampler2=lightmap");
        require("lightTexture".equals(selector.legacyUnit1Field)
                        && "overlayTexture".equals(selector.legacyUnit2Field),
                "Legacy GL units must retain unit1=lightmap and unit2=overlay");

        System.out.println("Legacy active texture-unit binding/upload contract passed");
    }

    private static <T extends ClassVisitor> T inspect(String resource, T visitor) throws IOException {
        try(InputStream input = LegacyTextureUnitContractTest.class.getClassLoader()
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

    private static final class RenderSystemVisitor extends ClassVisitor {
        boolean activeTextureUpdatesSelector;

        RenderSystemVisitor() {
            super(Opcodes.ASM9);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            if(!"activeTexture".equals(name))
                return null;
            return selectorCallVisitor(() -> activeTextureUpdatesSelector = true, "setActiveTexture");
        }
    }

    private static final class GlStateVisitor extends ClassVisitor {
        boolean activeTextureUpdatesSelector;
        boolean bindDelegatesToGlTexture;

        GlStateVisitor() {
            super(Opcodes.ASM9);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            if("_activeTexture".equals(name))
                return selectorCallVisitor(() -> activeTextureUpdatesSelector = true, "setActiveTexture");

            if("_bindTexture".equals(name)) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String methodName,
                                                String methodDescriptor, boolean isInterface) {
                        if(owner.equals("net/vulkanmod/gl/GlTexture")
                                && methodName.equals("bindTexture"))
                            bindDelegatesToGlTexture = true;
                    }
                };
            }
            return null;
        }
    }

    private static MethodVisitor selectorCallVisitor(Runnable found, String targetMethod) {
        return new MethodVisitor(Opcodes.ASM9) {
            @Override
            public void visitMethodInsn(int opcode, String owner, String methodName,
                                        String methodDescriptor, boolean isInterface) {
                if(owner.equals("net/vulkanmod/vulkan/texture/VTextureSelector")
                        && methodName.equals(targetMethod))
                    found.run();
            }
        };
    }

    private static final class GlTextureVisitor extends ClassVisitor {
        boolean hasPerUnitIds;
        boolean hasPerUnitTextures;
        boolean bindReadsActiveUnit;
        boolean bindUpdatesSelector;

        GlTextureVisitor() {
            super(Opcodes.ASM9);
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor,
                                       String signature, Object value) {
            if("boundTextureIds".equals(name) && "[I".equals(descriptor))
                hasPerUnitIds = true;
            if("boundTextures".equals(name)
                    && "[Lnet/vulkanmod/gl/GlTexture;".equals(descriptor))
                hasPerUnitTextures = true;
            return null;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            if(!"bindTexture".equals(name))
                return null;

            return new MethodVisitor(Opcodes.ASM9) {
                @Override
                public void visitMethodInsn(int opcode, String owner, String methodName,
                                            String methodDescriptor, boolean isInterface) {
                    if(owner.equals("net/vulkanmod/vulkan/texture/VTextureSelector")
                            && methodName.equals("getActiveTextureUnit"))
                        bindReadsActiveUnit = true;
                    if(owner.equals("net/vulkanmod/vulkan/texture/VTextureSelector")
                            && methodName.equals("bindActiveTexture"))
                        bindUpdatesSelector = true;
                }
            };
        }
    }

    private static final class SelectorVisitor extends ClassVisitor {
        boolean uploadReadsActiveUnitBinding;
        boolean hasBindActiveTexture;
        boolean hasLegacyUnitLookup;
        String shaderSlot1Field;
        String shaderSlot2Field;
        String legacyUnit1Field;
        String legacyUnit2Field;

        SelectorVisitor() {
            super(Opcodes.ASM9);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            if("bindActiveTexture".equals(name))
                hasBindActiveTexture = true;
            if("getLegacyTextureUnit".equals(name))
                hasLegacyUnitLookup = true;

            boolean shaderBinding = "bindTexture".equals(name)
                    && "(ILnet/vulkanmod/vulkan/texture/VulkanImage;)V".equals(descriptor);
            boolean legacyBinding = "bindLegacyTextureUnit".equals(name)
                    && "(ILnet/vulkanmod/vulkan/texture/VulkanImage;)V".equals(descriptor);
            if(shaderBinding || legacyBinding)
                return switchBindingVisitor(shaderBinding);

            if(!"uploadSubTexture".equals(name))
                return null;

            return new MethodVisitor(Opcodes.ASM9) {
                @Override
                public void visitMethodInsn(int opcode, String owner, String methodName,
                                            String methodDescriptor, boolean isInterface) {
                    if(owner.equals("net/vulkanmod/vulkan/texture/VTextureSelector")
                            && methodName.equals("getLegacyTextureUnit"))
                        uploadReadsActiveUnitBinding = true;
                }
            };
        }

        private MethodVisitor switchBindingVisitor(boolean shaderBinding) {
            return new MethodVisitor(Opcodes.ASM9) {
                private final Map<Label, Integer> caseByLabel = new IdentityHashMap<>();
                private int currentCase = Integer.MIN_VALUE;

                @Override
                public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
                    for(int i = 0; i < labels.length; ++i)
                        caseByLabel.put(labels[i], min + i);
                }

                @Override
                public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
                    for(int i = 0; i < labels.length; ++i)
                        caseByLabel.put(labels[i], keys[i]);
                }

                @Override
                public void visitLabel(Label label) {
                    currentCase = caseByLabel.getOrDefault(label, Integer.MIN_VALUE);
                }

                @Override
                public void visitFieldInsn(int opcode, String owner, String fieldName,
                                           String fieldDescriptor) {
                    if(opcode != Opcodes.PUTSTATIC
                            || !owner.equals("net/vulkanmod/vulkan/texture/VTextureSelector"))
                        return;

                    if(shaderBinding) {
                        if(currentCase == 1)
                            shaderSlot1Field = fieldName;
                        else if(currentCase == 2)
                            shaderSlot2Field = fieldName;
                    } else {
                        if(currentCase == 1)
                            legacyUnit1Field = fieldName;
                        else if(currentCase == 2)
                            legacyUnit2Field = fieldName;
                    }
                }
            };
        }
    }

}
