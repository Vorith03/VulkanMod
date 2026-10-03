package net.vulkanmod.render.instancing;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.framebuffer.RenderTargetManager;
import net.vulkanmod.vulkan.memory.MemoryManager;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import net.vulkanmod.vulkan.shader.Pipeline;
import net.vulkanmod.vulkan.shader.descriptor.Image;
import net.vulkanmod.vulkan.shader.descriptor.ManualUBO;
import org.joml.Matrix4fc;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_ALL_GRAPHICS;

/** Pinned 0.6 transformed/world/block shader. Experimental; no backend registration or RenderType state ownership. */
public final class LegacyFlywheelPipeline implements AutoCloseable {
    private GraphicsPipeline pipeline;
    private ByteBuffer uniforms;
    private boolean sceneReady;

    public LegacyFlywheelPipeline() {
        RenderSystem.assertOnRenderThread();
        uniforms=MemoryUtil.memCalloc(112);
        try {
            var world=new ManualUBO(0,VK_SHADER_STAGE_ALL_GRAPHICS,28);
            world.setSrc(MemoryUtil.memAddress(uniforms),uniforms.capacity());
            var builder=new Pipeline.Builder(DefaultVertexFormat.BLOCK).setInstanceFormat(LegacyFlywheelInstances.format(5));
            builder.setUniforms(new ArrayList<>(List.of(world)),List.of(new Image(1,"sampler2D","Sampler0"),new Image(2,"sampler2D","Sampler2")));
            builder.compileShaders(source("vsh"),source("fsh"));
            pipeline=builder.createGraphicsPipeline();
        } catch(RuntimeException | Error failure) {
            MemoryUtil.memFree(uniforms); uniforms=null; throw failure;
        }
    }
    /** All coordinates are relative to the engine origin, including camera and ViewProjection's origin translation. */
    public void setScene(Matrix4fc viewProjection,float cameraX,float cameraY,float cameraZ,
                         float fogR,float fogG,float fogB,float fogStart,float fogEnd,float alphaDiscard) {
        requireOpen();
        if(!viewProjection.isFinite() || !Float.isFinite(cameraX) || !Float.isFinite(cameraY) || !Float.isFinite(cameraZ)
                || !Float.isFinite(fogR) || !Float.isFinite(fogG) || !Float.isFinite(fogB)
                || !Float.isFinite(fogStart) || !Float.isFinite(fogEnd) || fogEnd<=fogStart || !Float.isFinite(fogEnd-fogStart)
                || !Float.isFinite(alphaDiscard) || alphaDiscard<0 || alphaDiscard>1)
            throw new IllegalArgumentException("Invalid Flywheel world/fog/alpha uniforms");
        for(int col=0;col<4;col++) for(int row=0;row<4;row++)
            uniforms.putFloat(col*16+row*4,viewProjection.get(col,row));
        uniforms.putFloat(64,cameraX); uniforms.putFloat(68,cameraY); uniforms.putFloat(72,cameraZ);
        uniforms.putFloat(80,fogR); uniforms.putFloat(84,fogG); uniforms.putFloat(88,fogB);
        uniforms.putFloat(96,fogStart); uniforms.putFloat(100,fogEnd); uniforms.putFloat(104,alphaDiscard);
        sceneReady=true;
    }
    /** Caller binds exact Sampler0/Sampler2 and supplies blend/depth/cull/target state for this material. */
    public void draw(SharedModelBuffer mesh,ByteBuffer instances,int firstInstance,int instanceCount) {
        requireOpen();
        if(!sceneReady) throw new IllegalStateException("Flywheel world uniforms are unset");
        RenderTargetManager.preparePipelineTextures(pipeline);
        Renderer.getDrawer().drawIndexedInstanced(pipeline,mesh.vertices(),instances,mesh.indices(),mesh.indexType(),
                mesh.geometry().vertexCount(),mesh.geometry().indexCount(),firstInstance,instanceCount);
    }
    @Override public void close() {
        RenderSystem.assertOnRenderThread();
        if(pipeline==null) return;
        GraphicsPipeline retired=pipeline;
        pipeline=null; sceneReady=false;
        MemoryUtil.memFree(uniforms); uniforms=null; // Dynamic UBO uploads already copied each draw's data.
        MemoryManager.getInstance().addFrameOp(retired::cleanUp);
    }
    private void requireOpen() {
        RenderSystem.assertOnRenderThread();
        if(pipeline==null) throw new IllegalStateException("Flywheel material pipeline is retired");
    }
    private static String source(String extension) {
        String path="/assets/vulkanmod/shaders/instancing/legacy_transformed."+extension;
        try(var stream=LegacyFlywheelPipeline.class.getResourceAsStream(path)) {
            if(stream==null) throw new IllegalStateException("Missing Flywheel material shader: "+path);
            return new String(stream.readAllBytes(),StandardCharsets.UTF_8);
        } catch(IOException failure) { throw new IllegalStateException("Cannot read Flywheel material shader",failure); }
    }
}
