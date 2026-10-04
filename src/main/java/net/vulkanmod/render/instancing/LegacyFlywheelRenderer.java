package net.vulkanmod.render.instancing;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.vulkanmod.vulkan.VRenderSystem;
import net.vulkanmod.vulkan.shader.EffectRenderState;
import net.vulkanmod.vulkan.shader.PipelineState;
import net.vulkanmod.vulkan.shader.ShaderRenderState;
import net.vulkanmod.vulkan.texture.ShaderTextureState;
import net.vulkanmod.vulkan.texture.VTextureSelector;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.util.Objects;
import java.util.function.Consumer;

/** Experimental solid/cutout dispatcher. Caller owns world identity, task synchronization and target. */
public final class LegacyFlywheelRenderer implements AutoCloseable {
    private LegacyFlywheelPipeline pipeline;
    private boolean closed;

    public static boolean supports(Object type) {
        return type == RenderType.solid() || type == RenderType.cutout() || type == RenderType.cutoutMipped();
    }

    /** The event matrix predates InstanceWorld's -camera stack translation. Never translate that stack twice. */
    public static final class Scene {
        private final Matrix4f viewProjection;
        private final float cameraX, cameraY, cameraZ;

        public Scene(Matrix4fc eventViewProjection, double camX, double camY, double camZ,
                     BlockPos origin, boolean ignoreOrigin) {
            Objects.requireNonNull(origin);
            if(!Double.isFinite(camX) || !Double.isFinite(camY) || !Double.isFinite(camZ))
                throw new IllegalArgumentException("Nonfinite Flywheel event camera");
            // Subtract in double precision before casting, preserving fractions at distant world coordinates.
            cameraX=(float)(camX-(ignoreOrigin ? 0 : origin.getX()));
            cameraY=(float)(camY-(ignoreOrigin ? 0 : origin.getY()));
            cameraZ=(float)(camZ-(ignoreOrigin ? 0 : origin.getZ()));
            viewProjection=new Matrix4f(eventViewProjection);
            if(!ignoreOrigin) viewProjection.translate(-cameraX,-cameraY,-cameraZ);
            if(!viewProjection.isFinite() || !Float.isFinite(cameraX) || !Float.isFinite(cameraY) || !Float.isFinite(cameraZ))
                throw new IllegalArgumentException("Invalid Flywheel event scene");
        }
    }

    /** Exact vanilla state setup, fixed sampler repair and restoration even if publication/drawing fails. */
    public void draw(RenderType type, Scene scene, Consumer<LegacyFlywheelPipeline> draw) {
        RenderSystem.assertOnRenderThread();
        if(closed) throw new IllegalStateException("Flywheel renderer is retired");
        if(!supports(type)) throw new UnsupportedOperationException("Unqualified Flywheel RenderType");
        Objects.requireNonNull(scene); Objects.requireNonNull(draw);
        // These owners override descriptor names globally. Do not silently sample their post/mod textures.
        if(EffectRenderState.isActive() || ShaderRenderState.isActive())
            throw new UnsupportedOperationException("Flywheel dispatch inside an applied effect/mod shader is unqualified");
        if(pipeline==null) pipeline=new LegacyFlywheelPipeline();
        State previous=new State();
        try {
            type.setupRenderState();
            // Vanilla's positive cull/write shards assume defaults and may be no-ops. Establish them explicitly.
            RenderSystem.enableCull(); RenderSystem.depthMask(true); RenderSystem.colorMask(true,true,true,true);
            RenderSystem.enableDepthTest(); RenderSystem.depthFunc(515); RenderSystem.disableBlend();
            ShaderTextureState.syncFixedSamplers();
            float[] fog=RenderSystem.getShaderFogColor();
            pipeline.setScene(scene.viewProjection,scene.cameraX,scene.cameraY,scene.cameraZ,
                    fog[0],fog[1],fog[2],RenderSystem.getShaderFogStart(),RenderSystem.getShaderFogEnd(),
                    type==RenderType.solid() ? 0 : 0.1f);
            draw.accept(pipeline);
        } finally {
            try { type.clearRenderState(); } finally { previous.restore(); }
        }
    }

    @Override public void close() {
        RenderSystem.assertOnRenderThread();
        if(closed) return;
        closed=true;
        if(pipeline!=null) { pipeline.close(); pipeline=null; }
    }

    /** Only fields touched by the admitted vanilla states; stencil/scissor/target ownership stays with caller. */
    static final class State {
        final boolean cull=VRenderSystem.cull, depth=VRenderSystem.depthTest, mask=VRenderSystem.depthMask;
        final int depthFunction=VRenderSystem.depthFun, colorMask=VRenderSystem.colorMask;
        final PipelineState.BlendInfo blend=PipelineState.blendInfo;
        final PipelineState.BlendState blendValues=blend.createBlendState();
        final ShaderInstance shader=RenderSystem.getShader();
        final int active=VTextureSelector.getActiveTextureUnit();
        final int texture0=RenderSystem.getShaderTexture(0), texture1=RenderSystem.getShaderTexture(1), texture2=RenderSystem.getShaderTexture(2);
        final VulkanImage atlas=VTextureSelector.getBoundTexture(), overlay=VTextureSelector.getOverlayTexture(), light=VTextureSelector.getLightTexture();

        void restore() {
            VRenderSystem.cull=cull; VRenderSystem.depthTest=depth; VRenderSystem.depthMask=mask;
            VRenderSystem.depthFun=depthFunction; VRenderSystem.colorMask=colorMask;
            blend.enabled=blendValues.enabled; blend.srcRgbFactor=blendValues.srcRgbFactor; blend.dstRgbFactor=blendValues.dstRgbFactor;
            blend.srcAlphaFactor=blendValues.srcAlphaFactor; blend.dstAlphaFactor=blendValues.dstAlphaFactor; blend.blendOp=blendValues.blendOp;
            PipelineState.blendInfo=blend;
            RenderSystem.setShader(() -> shader);
            RenderSystem.setShaderTexture(0,texture0); RenderSystem.setShaderTexture(1,texture1); RenderSystem.setShaderTexture(2,texture2);
            VTextureSelector.bindTexture(0,atlas); VTextureSelector.bindTexture(1,overlay); VTextureSelector.bindTexture(2,light);
            VTextureSelector.setActiveTexture(active);
        }
    }
}
