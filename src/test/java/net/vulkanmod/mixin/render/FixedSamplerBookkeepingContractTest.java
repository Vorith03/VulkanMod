package net.vulkanmod.mixin.render;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;

/**
 * Guards the bridge between Minecraft's authoritative RenderSystem sampler ids
 * and VulkanMod's descriptor-facing fixed sampler state. Ordinary draws
 * reconcile Sampler0/1/2 from RenderSystem immediately before drawing, so the
 * lightmap and overlay mixins must update both representations.
 */
public final class FixedSamplerBookkeepingContractTest {
    private FixedSamplerBookkeepingContractTest() {
    }

    public static void main(String[] args) throws Exception {
        SamplerBridgeVisitor light = inspect(
                "net/vulkanmod/mixin/texture/MLightTexture.class",
                "turnOnLightLayer", "setLightTexture", 2);
        require(light.foundMethod, "Could not find MLightTexture.turnOnLightLayer()");
        require(light.renderSystemSlot == 2,
                "LightTexture.turnOnLightLayer() must publish the lightmap to RenderSystem Sampler2");
        require(light.selectorCall,
                "LightTexture.turnOnLightLayer() must mirror the lightmap into VTextureSelector");

        SamplerBridgeVisitor overlay = inspect(
                "net/vulkanmod/mixin/texture/MOverlayTexture.class",
                "setupOverlay", "setOverlayTexture", 1);
        require(overlay.foundMethod, "Could not find MOverlayTexture.setupOverlay callback");
        require(overlay.renderSystemSlot == 1,
                "OverlayTexture.setupOverlayColor() must publish the overlay to RenderSystem Sampler1");
        require(overlay.selectorCall,
                "OverlayTexture.setupOverlayColor() must mirror the overlay into VTextureSelector");

        System.out.println("Fixed shader sampler bookkeeping contract passed");
    }

    private static SamplerBridgeVisitor inspect(String resource, String methodName,
                                                 String selectorMethod, int requiredSlot)
            throws IOException {
        SamplerBridgeVisitor visitor = new SamplerBridgeVisitor(methodName, selectorMethod, requiredSlot);
        try(InputStream input = FixedSamplerBookkeepingContractTest.class.getClassLoader()
                .getResourceAsStream(resource)) {
            if(input == null)
                throw new AssertionError("Could not load " + resource);
            new ClassReader(input).accept(visitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        return visitor;
    }

    private static void require(boolean condition, String message) {
        if(!condition)
            throw new AssertionError(message);
    }

    private static final class SamplerBridgeVisitor extends ClassVisitor {
        private final String targetMethod;
        private final String selectorMethod;
        private final int requiredSlot;
        boolean foundMethod;
        boolean selectorCall;
        int renderSystemSlot = Integer.MIN_VALUE;

        SamplerBridgeVisitor(String targetMethod, String selectorMethod, int requiredSlot) {
            super(Opcodes.ASM9);
            this.targetMethod = targetMethod;
            this.selectorMethod = selectorMethod;
            this.requiredSlot = requiredSlot;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            if(!targetMethod.equals(name))
                return null;

            foundMethod = true;
            return new MethodVisitor(Opcodes.ASM9) {
                private int lastIntConstant = Integer.MIN_VALUE;

                @Override
                public void visitInsn(int opcode) {
                    if(opcode >= Opcodes.ICONST_M1 && opcode <= Opcodes.ICONST_5)
                        lastIntConstant = opcode - Opcodes.ICONST_0;
                }

                @Override
                public void visitIntInsn(int opcode, int operand) {
                    if(opcode == Opcodes.BIPUSH || opcode == Opcodes.SIPUSH)
                        lastIntConstant = operand;
                }

                @Override
                public void visitLdcInsn(Object value) {
                    if(value instanceof Integer integer)
                        lastIntConstant = integer;
                }

                @Override
                public void visitMethodInsn(int opcode, String owner, String methodName,
                                            String methodDescriptor, boolean isInterface) {
                    if(owner.equals("com/mojang/blaze3d/systems/RenderSystem")
                            && methodName.equals("setShaderTexture")
                            && methodDescriptor.equals("(II)V")
                            && lastIntConstant == requiredSlot) {
                        renderSystemSlot = lastIntConstant;
                    }
                    if(owner.equals("net/vulkanmod/vulkan/texture/VTextureSelector")
                            && methodName.equals(selectorMethod)) {
                        selectorCall = true;
                    }
                }
            };
        }
    }
}
