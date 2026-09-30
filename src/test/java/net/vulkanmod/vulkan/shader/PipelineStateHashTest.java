package net.vulkanmod.vulkan.shader;

import java.util.Objects;
import java.util.Random;

public final class PipelineStateHashTest {
    public static void main(String[] args) {
        Random random = new Random(20260930L);
        for(int i = 0; i < 10000; ++i) {
            boolean enabled = random.nextBoolean();
            PipelineState.BlendState blend = new PipelineState.BlendState(enabled,
                    random.nextInt(), random.nextInt(), random.nextInt(), random.nextInt(), random.nextInt());
            expect(blend.hashCode(), enabled ? Objects.hash(true, blend.srcRgbFactor,
                    blend.dstRgbFactor, blend.srcAlphaFactor, blend.dstAlphaFactor, blend.blendOp)
                    : Boolean.hashCode(false));
            PipelineState.DepthState depth = new PipelineState.DepthState(enabled, random.nextBoolean(), 515);
            expect(depth.hashCode(), Objects.hash(depth.depthTest, depth.depthMask, depth.function));
            PipelineState.LogicOpState logic = new PipelineState.LogicOpState(enabled, random.nextInt());
            expect(logic.hashCode(), Objects.hash(enabled, logic.getLogicOp()));
            logic.setLogicOp(random.nextInt());
            expect(logic.hashCode(), Objects.hash(enabled, logic.getLogicOp()));
            PipelineState.StencilState stencil = new PipelineState.StencilState(enabled, 512 + i % 8,
                    random.nextInt(), random.nextInt(), random.nextInt(), 7680, 7681, 5386);
            expect(stencil.hashCode(), Objects.hash(stencil.enabled, stencil.function, stencil.reference,
                    stencil.compareMask, stencil.writeMask, stencil.failOp, stencil.depthFailOp, stencil.passOp));
        }
        PipelineState.BlendState a = new PipelineState.BlendState(false, 1, 2, 3, 4, 5);
        PipelineState.BlendState b = new PipelineState.BlendState(false, 6, 7, 8, 9, 10);
        if(!a.equals(b) || a.hashCode() != b.hashCode())
            throw new AssertionError("Disabled blend states must remain equivalent");
        System.out.println("Pipeline component hashes match legacy values, including mutable/disabled states");
    }

    private static void expect(int actual, int expected) {
        if(actual != expected) throw new AssertionError(actual + " != " + expected);
    }
}
