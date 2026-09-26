package net.vulkanmod.mixin.render;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class EmergencySaveMixinContractTest {
    private static final String MINECRAFT_MIXIN_RESOURCE =
            "net/vulkanmod/mixin/render/MinecraftMixin.class";
    private static final String BUFFER_UPLOADER_MIXIN_RESOURCE =
            "net/vulkanmod/mixin/render/BufferUploaderM.class";

    private EmergencySaveMixinContractTest() {
    }

    public static void main(String[] args) throws Exception {
        verifyEmergencySaveContract();
        verifyBufferUploaderShaderLifecycleContract();
        System.out.println("Rendering mixin safety contracts passed");
    }

    private static void verifyEmergencySaveContract() throws IOException {
        EmergencySaveVisitor visitor = inspectMinecraftMixin();
        require(!visitor.hasSkipEmergencySaveMethod,
                "VulkanMod must not define an emergency-save suppression method");
        require(visitor.emergencySaveReferences == 0,
                "VulkanMod's Minecraft mixin must not intercept Minecraft.emergencySave()");
    }

    private static void verifyBufferUploaderShaderLifecycleContract() throws IOException {
        ShaderLifecycleVisitor visitor = inspectBufferUploaderMixin();
        require(visitor.foundDrawWithShader,
                "Could not find BufferUploaderM.drawWithShader(RenderedBuffer)");
        require(visitor.applyCall >= 0,
                "BufferUploaderM.drawWithShader must apply the active ShaderInstance");
        require(visitor.samplerSyncCall > visitor.applyCall,
                "Fixed sampler reconciliation must happen after ShaderInstance.apply()");
        require(visitor.drawCall > visitor.samplerSyncCall,
                "The Vulkan draw must happen after ShaderInstance.apply() and sampler reconciliation");
        require(visitor.hasNormalClearAfterDraw(),
                "BufferUploaderM.drawWithShader must clear ShaderInstance after a successful Vulkan draw");
        require(visitor.hasExceptionalClearAroundDraw(),
                "BufferUploaderM.drawWithShader must clear ShaderInstance when the Vulkan draw path throws");
    }

    private static EmergencySaveVisitor inspectMinecraftMixin() throws IOException {
        EmergencySaveVisitor visitor = new EmergencySaveVisitor();
        inspect(MINECRAFT_MIXIN_RESOURCE, visitor);
        return visitor;
    }

    private static ShaderLifecycleVisitor inspectBufferUploaderMixin() throws IOException {
        ShaderLifecycleVisitor visitor = new ShaderLifecycleVisitor();
        inspect(BUFFER_UPLOADER_MIXIN_RESOURCE, visitor);
        return visitor;
    }

    private static void inspect(String resource, ClassVisitor visitor) throws IOException {
        try(InputStream input = EmergencySaveMixinContractTest.class.getClassLoader()
                .getResourceAsStream(resource)) {
            if(input == null)
                throw new AssertionError("Could not load " + resource);
            new ClassReader(input).accept(visitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
    }

    private static void require(boolean condition, String message) {
        if(!condition)
            throw new AssertionError(message);
    }

    private static final class EmergencySaveVisitor extends ClassVisitor {
        boolean hasSkipEmergencySaveMethod;
        int emergencySaveReferences;

        EmergencySaveVisitor() {
            super(Opcodes.ASM9);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            if("skipEmergencySave".equals(name))
                hasSkipEmergencySaveMethod = true;

            return new MethodVisitor(Opcodes.ASM9) {
                @Override
                public void visitMethodInsn(int opcode, String owner, String methodName,
                                            String methodDescriptor, boolean isInterface) {
                    if(owner.equals("net/minecraft/client/Minecraft")
                            && methodName.equals("emergencySave")
                            && methodDescriptor.equals("()V"))
                        emergencySaveReferences++;
                }
            };
        }
    }

    private static final class ShaderLifecycleVisitor extends ClassVisitor {
        private static final String DRAW_WITH_SHADER_DESCRIPTOR =
                "(Lcom/mojang/blaze3d/vertex/BufferBuilder$RenderedBuffer;)V";

        boolean foundDrawWithShader;
        int applyCall = -1;
        int samplerSyncCall = -1;
        int drawCall = -1;
        final List<Integer> clearCalls = new ArrayList<>();
        final List<Integer> returnPositions = new ArrayList<>();
        final List<Integer> throwPositions = new ArrayList<>();
        final Map<Label, Integer> labelPositions = new IdentityHashMap<>();
        final List<TryCatchRange> tryCatchRanges = new ArrayList<>();
        int methodCallOrdinal;

        ShaderLifecycleVisitor() {
            super(Opcodes.ASM9);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            if(!"drawWithShader".equals(name) || !DRAW_WITH_SHADER_DESCRIPTOR.equals(descriptor))
                return null;

            foundDrawWithShader = true;
            return new MethodVisitor(Opcodes.ASM9) {
                @Override
                public void visitLabel(Label label) {
                    labelPositions.put(label, methodCallOrdinal);
                }

                @Override
                public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
                    tryCatchRanges.add(new TryCatchRange(start, end, handler, type));
                }

                @Override
                public void visitMethodInsn(int opcode, String owner, String methodName,
                                            String methodDescriptor, boolean isInterface) {
                    int ordinal = methodCallOrdinal++;
                    if(owner.equals("net/minecraft/client/renderer/ShaderInstance")
                            && methodDescriptor.equals("()V")) {
                        if(methodName.equals("apply"))
                            applyCall = ordinal;
                        else if(methodName.equals("clear"))
                            clearCalls.add(ordinal);
                    } else if(owner.equals("net/vulkanmod/vulkan/texture/ShaderTextureState")
                            && methodName.equals("syncFixedSamplers")) {
                        samplerSyncCall = ordinal;
                    } else if(owner.equals("net/vulkanmod/vulkan/Drawer")
                            && methodName.equals("draw")) {
                        drawCall = ordinal;
                    }
                }

                @Override
                public void visitInsn(int opcode) {
                    if(opcode == Opcodes.RETURN)
                        returnPositions.add(methodCallOrdinal);
                    else if(opcode == Opcodes.ATHROW)
                        throwPositions.add(methodCallOrdinal);
                }
            };
        }

        boolean hasNormalClearAfterDraw() {
            if(drawCall < 0)
                return false;

            for(int clearCall : clearCalls) {
                if(clearCall <= drawCall)
                    continue;
                for(int returnPosition : returnPositions) {
                    if(clearCall < returnPosition)
                        return true;
                }
            }
            return false;
        }

        boolean hasExceptionalClearAroundDraw() {
            if(drawCall < 0)
                return false;

            for(TryCatchRange range : tryCatchRanges) {
                if(range.type != null)
                    continue;

                Integer start = labelPositions.get(range.start);
                Integer end = labelPositions.get(range.end);
                Integer handler = labelPositions.get(range.handler);
                if(start == null || end == null || handler == null)
                    continue;
                if(drawCall < start || drawCall >= end)
                    continue;

                for(int clearCall : clearCalls) {
                    if(clearCall < handler)
                        continue;
                    for(int throwPosition : throwPositions) {
                        if(clearCall < throwPosition)
                            return true;
                    }
                }
            }
            return false;
        }
    }

    private record TryCatchRange(Label start, Label end, Label handler, String type) {
    }
}
