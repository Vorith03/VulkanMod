package net.vulkanmod.render.instancing;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.vulkanmod.Initializer;
import net.vulkanmod.gl.GlTexture;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.VRenderSystem;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.shader.PipelineState;
import net.vulkanmod.vulkan.texture.ShaderTextureState;
import net.vulkanmod.vulkan.texture.VTextureSelector;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.joml.Matrix4f;

import java.nio.ByteBuffer;
import java.util.BitSet;

/** Real RenderType state/sampler/event-matrix pixels, independent of optional Flywheel classes. */
public final class LegacyFlywheelRendererSmokeTest {
    private LegacyFlywheelRendererSmokeTest() {}

    static void verify(Minecraft minecraft, SharedModelBuffer mesh, ByteBuffer source, VulkanImage atlas, VulkanImage light) {
        int atlasId=GlTexture.genTextureId(),lightId=GlTexture.genTextureId();
        GlTexture.setVulkanImage(atlasId,atlas); GlTexture.setVulkanImage(lightId,light);
        int[] textures={RenderSystem.getShaderTexture(0),RenderSystem.getShaderTexture(1),RenderSystem.getShaderTexture(2)};
        float start=RenderSystem.getShaderFogStart(),end=RenderSystem.getShaderFogEnd();
        float[] fog=RenderSystem.getShaderFogColor().clone();
        int oldFunction=VRenderSystem.depthFun, oldMask=VRenderSystem.colorMask;
        boolean oldBlend=PipelineState.blendInfo.enabled;
        TextureTarget target=new TextureTarget(80,48,true,Minecraft.ON_OSX);
        byte[] bytes=new byte[source.remaining()]; source.duplicate().get(bytes);
        ByteBuffer records=org.lwjgl.system.MemoryUtil.memAlloc(bytes.length).put(bytes).flip();
        // Restore the original down/up normal columns changed by the preceding directional-diffuse oracle.
        records.putFloat(84,0); records.putFloat(88,-2); records.putFloat(92,0);
        records.putFloat(108+84,0); records.putFloat(108+88,3); records.putFloat(108+92,0);
        byte[] vertexBytes=new byte[mesh.geometry().vertices().remaining()]; mesh.geometry().vertices().get(vertexBytes);
        SharedModelBuffer reversed=new SharedModelBuffer(new ModelGeometry(vertexBytes,new int[]{0,2,1,2,0,3},new BitSet()));
        Renderer renderer=Renderer.getInstance();
        BlockPos origin=new BlockPos(30_000_000,-300,-30_000_000);
        Matrix4f event=new Matrix4f().translation(0.25f,0.5f,0.125f);
        var scene=new LegacyFlywheelRenderer.Scene(event,origin.getX()+0.25,origin.getY()+0.5,origin.getZ()+0.125,origin,false);
        try(var owner=new LegacyFlywheelRenderer()) {
            RenderSystem.setShaderFogStart(100); RenderSystem.setShaderFogEnd(200); RenderSystem.setShaderFogColor(0.2f,0.4f,0.6f);
            for(int pass=0;pass<5;pass++) {
                renderer.resetBuffers(); renderer.beginFrame();
                target.setClearColor(0,0,0,1); target.clear(Minecraft.ON_OSX); target.bindWrite(true);
                Renderer.resetScissor(); Renderer.setDepthBias(0,0);
                VRenderSystem.cull=false; VRenderSystem.depthTest=false; VRenderSystem.depthMask=false;
                VRenderSystem.depthFun=516; VRenderSystem.colorMask=1; PipelineState.blendInfo.enabled=true;
                int active=VTextureSelector.getActiveTextureUnit();
                VulkanImage priorAtlas=VTextureSelector.getBoundTexture(),priorLight=VTextureSelector.getLightTexture();
                var priorShader=RenderSystem.getShader();
                int prior0=RenderSystem.getShaderTexture(0),prior2=RenderSystem.getShaderTexture(2);
                RenderType type=pass==2 ? RenderType.cutout() : pass==3 ? RenderType.cutoutMipped() : RenderType.solid();
                int selected=pass;
                owner.draw(type,scene,pipeline -> {
                    if(!VRenderSystem.cull || !VRenderSystem.depthTest || !VRenderSystem.depthMask || VRenderSystem.depthFun!=515
                            || VRenderSystem.colorMask!=15 || PipelineState.blendInfo.enabled)
                        throw new AssertionError("Exact opaque RenderType state was not established");
                    if(VTextureSelector.getBoundTexture()!=GlTexture.getVulkanImage(RenderSystem.getShaderTexture(0))
                            || VTextureSelector.getLightTexture()!=GlTexture.getVulkanImage(RenderSystem.getShaderTexture(2)))
                        throw new AssertionError("RenderType helper sampler bindings were not repaired");
                    // Isolated fixture textures after real setup/repair: preserve the production atlas and lightmap.
                    RenderSystem.setShaderTexture(0,atlasId); RenderSystem.setShaderTexture(2,lightId);
                    VTextureSelector.bindTexture(0,light); // Deliberately disturb Sampler0 to exercise authoritative repair.
                    ShaderTextureState.syncFixedSamplers();
                    if(selected==2 || selected==3) records.put(7,(byte)16);
                    try {
                        pipeline.draw(selected==4 ? reversed : mesh,records,0,2);
                        if(selected==1) {
                            // A farther red instance must lose the depth test to the first left instance.
                            records.putFloat(8+56,0.8f); records.put(4,(byte)255); records.put(5,(byte)0); records.put(6,(byte)0);
                            pipeline.draw(mesh,records,0,1);
                        }
                    } finally { records.put(bytes); records.position(0); records.limit(records.capacity());
                        records.putFloat(84,0); records.putFloat(88,-2); records.putFloat(92,0);
                        records.putFloat(108+84,0); records.putFloat(108+88,3); records.putFloat(108+92,0); }
                });
                checkRestored(active,priorAtlas,priorLight,priorShader,prior0,prior2);
                try { owner.draw(type,scene,pipeline -> { throw new IllegalStateException("fixture abort"); }); throw new AssertionError("Draw failure swallowed"); }
                catch(IllegalStateException expected) { if(!"fixture abort".equals(expected.getMessage())) throw expected; }
                checkRestored(active,priorAtlas,priorLight,priorShader,prior0,prior2);
                try(var capture=LegacyFlywheelPipelineSmokeTest.RawCapture.record(target)) {
                    target.unbindWrite(); minecraft.getMainRenderTarget().bindWrite(true); renderer.endFrame(); Vulkan.waitIdle();
                    try(NativeImage image=capture.read()) {
                        expect(image,20,24,pass>=2 ? new int[]{0,0,0,255} : new int[]{22,47,6,64});
                        expect(image,60,24,pass==4 ? new int[]{0,0,0,255} : new int[]{188,22,50,128});
                    }
                }
            }
            if(!event.equals(new Matrix4f().translation(0.25f,0.5f,0.125f))) throw new AssertionError("Caller event matrix mutated");
            try { owner.draw(RenderType.translucent(),scene,pipeline -> { throw new AssertionError("Unsupported state reached draw"); }); throw new AssertionError("Translucent admitted"); }
            catch(UnsupportedOperationException expected) {}
            // Ignore-origin contexts already contain their translation; their event matrix must stay unchanged.
            renderer.resetBuffers(); renderer.beginFrame(); target.clear(Minecraft.ON_OSX); target.bindWrite(true);
            owner.draw(RenderType.solid(),new LegacyFlywheelRenderer.Scene(new Matrix4f(),123,456,789,origin,true),pipeline -> {
                RenderSystem.setShaderTexture(0,atlasId); RenderSystem.setShaderTexture(2,lightId); ShaderTextureState.syncFixedSamplers();
                pipeline.draw(mesh,records,0,2);
            });
            try(var capture=LegacyFlywheelPipelineSmokeTest.RawCapture.record(target)) {
                owner.close(); // Commands still reference the pipeline: normal fence retirement applies.
                target.unbindWrite(); minecraft.getMainRenderTarget().bindWrite(true); renderer.endFrame(); Vulkan.waitIdle();
                try(NativeImage image=capture.read()) { expect(image,20,24,new int[]{51,102,153,64}); expect(image,60,24,new int[]{51,102,153,128}); }
            }
            try { owner.draw(RenderType.solid(),scene,pipeline -> {}); throw new AssertionError("Retired renderer admitted"); }
            catch(IllegalStateException expected) {}
            Initializer.LOGGER.info("Vulkan Flywheel state dispatcher smoke passed: exact solid/cutout/mipped state, sampler repair, double-precision distant origin/event composition, ignore-origin, alpha discard, depth occlusion, backface cull, caller/failure restoration and recorded-pipeline retirement; backend remains off");
        } finally {
            Vulkan.waitIdle(); reversed.close(); target.destroyBuffers(); org.lwjgl.system.MemoryUtil.memFree(records);
            GlTexture.setVulkanImage(atlasId,null); GlTexture.setVulkanImage(lightId,null);
            GlTexture.glDeleteTextures(atlasId); GlTexture.glDeleteTextures(lightId);
            for(int slot=0;slot<3;slot++) RenderSystem.setShaderTexture(slot,textures[slot]);
            RenderSystem.setShaderFogStart(start); RenderSystem.setShaderFogEnd(end); RenderSystem.setShaderFogColor(fog[0],fog[1],fog[2],fog[3]);
            VRenderSystem.depthFun=oldFunction; VRenderSystem.colorMask=oldMask; PipelineState.blendInfo.enabled=oldBlend;
        }
    }
    private static void checkRestored(int active,VulkanImage atlas,VulkanImage light,net.minecraft.client.renderer.ShaderInstance shader,int texture0,int texture2) {
        if(VRenderSystem.cull || VRenderSystem.depthTest || VRenderSystem.depthMask || VRenderSystem.depthFun!=516
                || VRenderSystem.colorMask!=1 || !PipelineState.blendInfo.enabled || RenderSystem.getShader()!=shader
                || RenderSystem.getShaderTexture(0)!=texture0 || RenderSystem.getShaderTexture(2)!=texture2
                || VTextureSelector.getActiveTextureUnit()!=active || VTextureSelector.getBoundTexture()!=atlas || VTextureSelector.getLightTexture()!=light)
            throw new AssertionError("Flywheel dispatcher leaked caller state");
    }
    private static void expect(NativeImage image,int x,int y,int[] expected) {
        int value=image.getPixelRGBA(x,y);
        for(int component=0;component<4;component++) if(Math.abs((value >>> (component*8) & 255)-expected[component])>1)
            throw new AssertionError(String.format("Flywheel state pixel (%d,%d) expected %s got %08x",x,y,java.util.Arrays.toString(expected),value));
    }
}
