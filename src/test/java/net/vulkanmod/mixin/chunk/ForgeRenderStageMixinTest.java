package net.vulkanmod.mixin.chunk;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;

public final class ForgeRenderStageMixinTest {
    private static final String MIXIN_RESOURCE =
            "net/vulkanmod/mixin/chunk/LevelRendererMixin.class";

    private ForgeRenderStageMixinTest() {
    }

    public static void main(String[] args) throws Exception {
        ContractVisitor visitor = inspectMixin();
        require(visitor.foundRenderChunkLayer, "renderChunkLayer overwrite was not found");
        require(visitor.worldDrawCalls == 1,
                "Expected exactly one WorldRenderer.renderSectionLayer call, got " + visitor.worldDrawCalls);
        require(visitor.dispatchCalls == 1,
                "Expected exactly one Forge render-stage dispatch, got " + visitor.dispatchCalls);
        require(visitor.dispatchAfterWorldDraw,
                "Forge render-stage dispatch must occur after the Vulkan terrain-layer draw");
        require(visitor.renderTypeOverload,
                "Forge render-stage dispatch must use the RenderType overload");
        System.out.println("Forge terrain render-stage bytecode contract passed");
    }

    private static ContractVisitor inspectMixin() throws IOException {
        try (InputStream input = ForgeRenderStageMixinTest.class.getClassLoader()
                .getResourceAsStream(MIXIN_RESOURCE)) {
            if(input == null) {
                throw new AssertionError("Could not load compiled mixin resource " + MIXIN_RESOURCE);
            }

            ContractVisitor visitor = new ContractVisitor();
            new ClassReader(input).accept(visitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return visitor;
        }
    }

    private static void require(boolean condition, String message) {
        if(!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class ContractVisitor extends ClassVisitor {
        boolean foundRenderChunkLayer;
        int worldDrawCalls;
        int dispatchCalls;
        boolean dispatchAfterWorldDraw;
        boolean renderTypeOverload;

        ContractVisitor() {
            super(Opcodes.ASM9);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            if(!"renderChunkLayer".equals(name)) {
                return null;
            }

            foundRenderChunkLayer = true;
            return new MethodVisitor(Opcodes.ASM9) {
                private boolean sawWorldDraw;

                @Override
                public void visitMethodInsn(int opcode, String owner, String methodName,
                                            String methodDescriptor, boolean isInterface) {
                    if(owner.equals("net/vulkanmod/render/chunk/WorldRenderer")
                            && methodName.equals("renderSectionLayer")) {
                        worldDrawCalls++;
                        sawWorldDraw = true;
                    }

                    if(owner.equals("net/minecraftforge/client/ForgeHooksClient")
                            && methodName.equals("dispatchRenderStage")) {
                        dispatchCalls++;
                        dispatchAfterWorldDraw |= sawWorldDraw;
                        renderTypeOverload |= methodDescriptor.startsWith(
                                "(Lnet/minecraft/client/renderer/RenderType;");
                    }
                }
            };
        }
    }
}
