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

public final class BufferUploaderShaderLifecycleContractTest {
    private static final String BUFFER_UPLOADER_MIXIN_RESOURCE =
            "net/vulkanmod/mixin/render/BufferUploaderM.class";

    private BufferUploaderShaderLifecycleContractTest() {
    }

    public static void main(String[] args) throws Exception {
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
        ShaderInstanceLegacyApplyContractTest.main(args);
        System.out.println("BufferUploader shader lifecycle contract passed");
    }

    private static ShaderLifecycleVisitor inspectBufferUploaderMixin() throws IOException {
        ShaderLifecycleVisitor visitor = new ShaderLifecycleVisitor();
        try(InputStream input = BufferUploaderShaderLifecycleContractTest.class.getClassLoader()
                .getResourceAsStream(BUFFER_UPLOADER_MIXIN_RESOURCE)) {
            if(input == null)
                throw new AssertionError("Could not load " + BUFFER_UPLOADER_MIXIN_RESOURCE);
            new ClassReader(input).accept(visitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        return visitor;
    }

    private static void require(boolean condition, String message) {
        if(!condition)
            throw new AssertionError(message);
    }

    private static final class ShaderLifecycleVisitor extends ClassVisitor {
        private static final String DRAW_WITH_SHADER_DESCRIPTOR =
                "(Lcom/mojang/blaze3d/vertex/BufferBuilder$RenderedBuffer;)V";

        boolean foundDrawWithShader;
        int applyCall = -1;
        int samplerSyncCall = -1;
        int drawCall = -1;
        final List<Integer> clearCalls = new ArrayList<>();
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
                    if(opcode == Opcodes.ATHROW)
                        throwPositions.add(methodCallOrdinal);
                }
            };
        }

        boolean hasNormalClearAfterDraw() {
            for(TryCatchRange range : catchAllRangesCoveringDraw()) {
                Integer handler = labelPositions.get(range.handler);
                if(handler == null)
                    continue;
                for(int clearCall : clearCalls) {
                    if(clearCall > drawCall && clearCall < handler)
                        return true;
                }
            }
            return false;
        }

        boolean hasExceptionalClearAroundDraw() {
            for(TryCatchRange range : catchAllRangesCoveringDraw()) {
                Integer handler = labelPositions.get(range.handler);
                if(handler == null)
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

        private List<TryCatchRange> catchAllRangesCoveringDraw() {
            List<TryCatchRange> matches = new ArrayList<>();
            if(drawCall < 0)
                return matches;

            for(TryCatchRange range : tryCatchRanges) {
                if(range.type != null)
                    continue;
                Integer start = labelPositions.get(range.start);
                Integer end = labelPositions.get(range.end);
                if(start != null && end != null && drawCall >= start && drawCall < end)
                    matches.add(range);
            }
            return matches;
        }
    }

    private record TryCatchRange(Label start, Label end, Label handler, String type) {
    }
}
