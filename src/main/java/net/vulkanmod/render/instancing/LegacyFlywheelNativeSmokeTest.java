package net.vulkanmod.render.instancing;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.vulkanmod.Initializer;
import net.vulkanmod.gl.GlTexture;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.texture.ShaderTextureState;
import net.vulkanmod.vulkan.texture.VTextureSelector;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.function.Supplier;

import static org.lwjgl.vulkan.VK10.*;

/** Actual optional MaterialManager -> ModelData snapshot -> native shared mesh -> raw pixel oracle. */
public final class LegacyFlywheelNativeSmokeTest {
    private LegacyFlywheelNativeSmokeTest() {}
    static void verify(ClassLoader loader) throws ReflectiveOperationException {
        var layerApi=Class.forName("com.jozufozu.flywheel.backend.RenderLayer",false,loader);
        Object solid=layerApi.getField("SOLID").get(null);
        var managerApi=Class.forName("com.jozufozu.flywheel.api.MaterialManager",false,loader);
        var groupApi=Class.forName("com.jozufozu.flywheel.api.MaterialGroup",false,loader);
        var materialApi=Class.forName("com.jozufozu.flywheel.api.Material",false,loader);
        var specApi=Class.forName("com.jozufozu.flywheel.api.struct.StructType",false,loader);
        var instancerApi=Class.forName("com.jozufozu.flywheel.api.Instancer",false,loader);
        var fallback=new LegacyFlywheelMaterials.Fallback() {
            public Object model(Object layer,Object type,Object spec,Object key,Supplier<?> supplier) { throw new AssertionError("Native fixture unexpectedly declined"); }
            public void clearForOriginShift(BlockPos origin) {}
            public void close() {}
        };
        var renderer=Renderer.getInstance(); var minecraft=Minecraft.getInstance();
        float start=RenderSystem.getShaderFogStart(),end=RenderSystem.getShaderFogEnd();
        VulkanImage previous=VTextureSelector.getBoundTexture(); int active=VTextureSelector.getActiveTextureUnit();
        VulkanImage white=VulkanImage.createTextureImage(VK_FORMAT_R8G8B8A8_UNORM,1,1,1,
                VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_SAMPLED_BIT,4,false,true);
        int textureId=GlTexture.genTextureId(); GlTexture.setVulkanImage(textureId,white);
        ByteBuffer pixel=MemoryUtil.memAlloc(4).putInt(-1).flip();
        TextureTarget target=new TextureTarget(32,32,true,Minecraft.ON_OSX);
        try(var materials=new LegacyFlywheelMaterials(loader,new Object(),11,fallback,
                (layer,type) -> layer==solid && type==RenderType.solid()); var nativeRenderer=new LegacyFlywheelRenderer()) {
            VTextureSelector.setActiveTexture(0); VTextureSelector.bindTexture(white);
            VTextureSelector.uploadSubTexture(0,1,1,0,0,0,0,1,pixel); white.readOnlyLayout(); Vulkan.waitIdle();
            VTextureSelector.bindTexture(previous); VTextureSelector.setActiveTexture(active);
            Object group=managerApi.getMethod("state",layerApi,RenderType.class).invoke(materials.manager(),solid,RenderType.solid());
            Object spec=Class.forName("com.jozufozu.flywheel.core.materials.model.ModelType",false,loader).getConstructor().newInstance();
            Object material=groupApi.getMethod("material",specApi).invoke(group,spec);
            Object instancer=materialApi.getMethod("model",Object.class,Supplier.class).invoke(material,"quad",(Supplier<?>)() -> model(loader));
            Object data=instancerApi.getMethod("createInstance").invoke(instancer); Class<?> type=data.getClass();
            ((Matrix4f)type.getField("model").get(data)).translation(0,0,0.5f).scale(0.5f);
            type.getField("r").setByte(data,(byte)128); type.getField("g").setByte(data,(byte)64);
            type.getField("b").setByte(data,(byte)32); type.getField("a").setByte(data,(byte)128);
            type.getField("blockLight").setByte(data,(byte)15); type.getField("skyLight").setByte(data,(byte)15);
            RenderSystem.setShaderFogStart(100); RenderSystem.setShaderFogEnd(200);
            renderer.resetBuffers(); renderer.beginFrame(); target.setClearColor(0,0,0,1); target.clear(Minecraft.ON_OSX); target.bindWrite(true);
            Renderer.resetScissor(); Renderer.setDepthBias(0,0);
            var scene=new LegacyFlywheelRenderer.Scene(new Matrix4f(),0,0,0,BlockPos.ZERO,false);
            materials.visitStates(solid,state -> nativeRenderer.draw((RenderType)state,scene,pipeline -> {
                RenderSystem.setShaderTexture(0,textureId); RenderSystem.setShaderTexture(2,textureId); ShaderTextureState.syncFixedSamplers();
                materials.draw(solid,state,pipeline);
            }));
            try(var capture=LegacyFlywheelPipelineSmokeTest.RawCapture.record(target)) {
                materials.close(); nativeRenderer.close(); // Recorded draw owns uploaded copies until its frame fence.
                target.unbindWrite(); minecraft.getMainRenderTarget().bindWrite(true); renderer.endFrame(); Vulkan.waitIdle();
                try(var image=capture.read()) {
                    int value=image.getPixelRGBA(16,16);
                    for(int i=0;i<4;i++) if(Math.abs((value >>> (8*i) & 255)-new int[]{128,64,32,128}[i])>1)
                        throw new AssertionError(String.format("Actual Flywheel native material pixel differs: %08x",value));
                    if(image.getPixelRGBA(1,1)!=0xff000000) throw new AssertionError("Native material escaped transformed quad");
                }
            }
        } finally {
            Vulkan.waitIdle(); target.destroyBuffers(); MemoryUtil.memFree(pixel);
            GlTexture.glDeleteTextures(textureId); RenderSystem.setShaderFogStart(start); RenderSystem.setShaderFogEnd(end);
            VTextureSelector.bindTexture(previous); VTextureSelector.setActiveTexture(active);
        }
        Initializer.LOGGER.info("Flywheel native material dispatch smoke passed: actual manager/material/model supplier, ModelData numeric snapshot, lazily uploaded shared mesh, textured native pixels and recorded-owner retirement; backend remains off");
    }
    private static Object model(ClassLoader loader) {
        ByteBuffer vertices=MemoryUtil.memCalloc(128),indices=MemoryUtil.memAlloc(24);
        try {
            float[][] positions={{-1,-1,0},{1,-1,0},{1,1,0},{-1,1,0}};
            for(int v=0;v<4;v++) {
                for(int axis=0;axis<3;axis++) vertices.putFloat(v*32+axis*4,positions[v][axis]);
                vertices.putFloat(v*32+16,0.5f); vertices.putFloat(v*32+20,0.5f); vertices.put(v*32+29,(byte)127);
            }
            for(int index:ModelGeometry.quadIndices(4)) indices.putInt(index); indices.flip();
            var state=new BufferBuilder.DrawState(DefaultVertexFormat.BLOCK,4,6,VertexFormat.Mode.QUADS,VertexFormat.IndexType.INT,false,true);
            return Class.forName("com.jozufozu.flywheel.core.model.BlockModel",false,loader)
                    .getConstructor(ByteBuffer.class,ByteBuffer.class,BufferBuilder.DrawState.class,int.class,String.class)
                    .newInstance(vertices,indices,state,0,"native material fixture");
        } catch(ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
        finally { MemoryUtil.memFree(vertices); MemoryUtil.memFree(indices); }
    }
}
