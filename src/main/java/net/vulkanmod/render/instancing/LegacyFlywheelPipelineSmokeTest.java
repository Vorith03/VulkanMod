package net.vulkanmod.render.instancing;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.vulkanmod.Initializer;
import net.vulkanmod.gl.GlTexture;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.VRenderSystem;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.shader.PipelineState;
import net.vulkanmod.vulkan.framebuffer.RenderTargetManager;
import net.vulkanmod.vulkan.memory.MemoryManager;
import net.vulkanmod.vulkan.texture.VTextureSelector;
import net.vulkanmod.vulkan.texture.VulkanImage;
import net.vulkanmod.vulkan.util.ColorUtil;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.BitSet;

import static org.lwjgl.vulkan.VK10.*;

/** Exact textured-material pixels, with no optional Flywheel dependency needed for the native shader oracle. */
public final class LegacyFlywheelPipelineSmokeTest {
    private LegacyFlywheelPipelineSmokeTest() {}
    public static void verify(Minecraft minecraft) throws Exception {
        Renderer renderer=Renderer.getInstance();
        boolean oldCull=VRenderSystem.cull,oldDepth=VRenderSystem.depthTest,oldMask=VRenderSystem.depthMask;
        boolean oldStencil=VRenderSystem.stencilTest,oldBlend=PipelineState.blendInfo.enabled;
        int oldColorMask=VRenderSystem.colorMask;
        int previousActive=VTextureSelector.getActiveTextureUnit();
        VulkanImage previousAtlas=VTextureSelector.getBoundTexture(),previousLight=VTextureSelector.getLightTexture();
        VulkanImage atlas=null,light=null;
        SharedModelBuffer mesh=null;
        LegacyFlywheelPipeline pipeline=null;
        TextureTarget target=null;
        ByteBuffer atlasData=MemoryUtil.memAlloc(4),lightData=MemoryUtil.memAlloc(16*16*4);
        ByteBuffer instances=MemoryUtil.memCalloc(LegacyFlywheelInstances.STRIDE*2);
        try {
            VRenderSystem.cull=false; VRenderSystem.depthTest=false; VRenderSystem.depthMask=false;
            VRenderSystem.stencilTest=false; PipelineState.blendInfo.enabled=false; VRenderSystem.colorMask=15;
            atlas=texture(1,1); light=texture(16,16);
            light.updateTextureSampler(true,true,false); // Linear sampling makes a missing half-texel adjustment visible.
            atlasData.put(new byte[]{(byte)200,100,50,(byte)128}).flip();
            for(int y=0;y<16;y++) for(int x=0;x<16;x++)
                lightData.put((byte)(x*16)).put((byte)(y*16)).put((byte)255).put((byte)17);
            lightData.flip();
            VTextureSelector.setActiveTexture(0);
            VTextureSelector.bindTexture(atlas); VTextureSelector.uploadSubTexture(0,1,1,0,0,0,0,1,atlasData); atlas.readOnlyLayout();
            VTextureSelector.bindTexture(light); VTextureSelector.uploadSubTexture(0,16,16,0,0,0,0,16,lightData); light.readOnlyLayout();
            VTextureSelector.bindTexture(atlas); VTextureSelector.setLightTexture(light);
            // Oracle only: finish helper uploads before the frame staging arena resets.
            Vulkan.waitIdle();

            byte[] vertexBytes=new byte[4*ModelGeometry.STRIDE];
            var vertices=ByteBuffer.wrap(vertexBytes).order(ByteOrder.nativeOrder());
            int index=0;
            for(float[] position:new float[][]{{-1,-1,0},{1,-1,0},{1,1,0},{-1,1,0}}) {
                int at=index++*ModelGeometry.STRIDE;
                for(int axis=0;axis<3;axis++) vertices.putFloat(at+axis*4,position[axis]);
                // Black/zero-light model vertices must be replaced by instance color/light.
                vertices.putInt(at+12,0); vertices.putFloat(at+16,0.5f); vertices.putFloat(at+20,0.5f);
                vertices.putInt(at+24,0); vertices.put(at+29,(byte)127);
            }
            mesh=new SharedModelBuffer(new ModelGeometry(vertexBytes,ModelGeometry.quadIndices(4),new BitSet()));
            record(instances,0,-0.5f,128,255,64,128,112,240,-2); // Normalize a transformed down normal.
            record(instances,LegacyFlywheelInstances.STRIDE,0.5f,255,128,255,255,240,112,3);
            pipeline=new LegacyFlywheelPipeline();
            try { pipeline.draw(mesh,instances,0,0); throw new AssertionError("Unset scene accepted"); }
            catch(IllegalStateException expected) {}
            try { scene(pipeline,0,1,1,0); throw new AssertionError("Degenerate fog accepted"); }
            catch(IllegalArgumentException expected) {}
            target=new TextureTarget(64,32,true,Minecraft.ON_OSX);
            for(int pass=0;pass<9;pass++) {
                if(pass==4) { target.resize(80,48,Minecraft.ON_OSX); pipeline=new LegacyFlywheelPipeline(); }
                renderer.resetBuffers(); renderer.beginFrame();
                target.setClearColor(0,0,0,1); target.clear(Minecraft.ON_OSX); target.bindWrite(true);
                Renderer.resetScissor(); Renderer.setDepthBias(0,0);
                if(pass==1) {
                    // Changing the same uniform source cannot overwrite the first draw's dynamic UBO upload.
                    scene(pipeline,10.5f,0,20,0); pipeline.draw(mesh,instances,0,1);
                    scene(pipeline,0,100,200,0); pipeline.draw(mesh,instances,1,1);
                } else if(pass==8) {
                    int second=LegacyFlywheelInstances.STRIDE;
                    instances.putFloat(72+12,3); instances.putFloat(72+16,0); instances.putFloat(72+20,4);
                    instances.putFloat(second+72+12,-3); instances.putFloat(second+72+16,0);
                    scene(pipeline,0,100,200,0); pipeline.draw(mesh,instances,0,2);
                } else if(pass==5) {
                    pipeline.setScene(new Matrix4f().translation(1,0,0),0,0,0,0.2f,0.4f,0.6f,100,200,0);
                    pipeline.draw(mesh,instances,0,2);
                } else if(pass>=6) {
                    // Mixed camera axes distinguish cylindrical max(XZ,Y) from Euclidean or XZ-only distance.
                    pipeline.setScene(new Matrix4f(),0,pass==6 ? 6 : 8,pass==6 ? 8.5f : 6.5f,
                            0.2f,0.4f,0.6f,0,20,0);
                    pipeline.draw(mesh,instances,0,2);
                } else {
                    scene(pipeline,pass==3 ? 100 : 0,pass==3 ? 0 : 100,pass==3 ? 50 : 200,pass==2 ? 0.3f : 0);
                    pipeline.draw(mesh,instances,0,pass==4 ? 0 : 2);
                }
                // Screenshots deliberately force opaque alpha. This shader oracle needs raw attachment alpha.
                try(var capture=RawCapture.record(target)) {
                    target.unbindWrite(); minecraft.getMainRenderTarget().bindWrite(true);
                    if(pass==3) {
                        // Recorded commands still reference this pipeline. Production close must wait for its frame fence.
                        pipeline.close(); pipeline.close();
                        try { scene(pipeline,0,100,200,0); throw new AssertionError("Retired material reused"); }
                        catch(IllegalStateException expected) {}
                    }
                    renderer.endFrame();
                    Vulkan.waitIdle(); // Oracle only: complete this frame's raw transfer before mapping it.
                    try(NativeImage image=capture.read()) {
                        int[] left=pass==8 ? new int[]{32,69,9,64} : pass>=6 ? new int[]{34,69,65,64} : pass==1 ? new int[]{37,75,80,64} : pass==2 || pass==4 || pass==5 ? new int[]{0,0,0,255}
                                : pass==3 ? new int[]{51,102,153,64} : new int[]{22,47,6,64};
                        int[] right=pass==8 ? new int[]{113,13,30,128} : pass>=6 ? new int[]{133,54,91,128} : pass==5 ? new int[]{22,47,6,64} : pass==4 ? new int[]{0,0,0,255} : pass==3 ? new int[]{51,102,153,128} : new int[]{188,22,50,128};
                        expect(image,image.getWidth()/4,image.getHeight()/2,left);
                        expect(image,3*image.getWidth()/4,image.getHeight()/2,right);
                        expect(image,1,1,new int[]{0,0,0,255});
                    }
                }
                if(instances.position()!=0 || instances.limit()!=instances.capacity()) throw new AssertionError("Material draw changed caller snapshot");
            }
            Initializer.LOGGER.info("Vulkan Flywheel material shader smoke passed: transformed model/normalized normals, instance color/light override, both lightmap channels and legacy half-texel shift, atlas/light alpha separation, cylindrical linear fog, alpha discard, per-draw uniform isolation, resize/zero count, fence-deferred pipeline retirement; backend remains off");
        } finally {
            Vulkan.waitIdle(); // Test-only readback/cleanup boundary.
            if(pipeline!=null) pipeline.close();
            if(mesh!=null) mesh.close();
            if(target!=null) target.destroyBuffers();
            VTextureSelector.bindTexture(previousAtlas); VTextureSelector.setLightTexture(previousLight);
            VTextureSelector.setActiveTexture(previousActive);
            if(atlas!=null) atlas.doFree(); if(light!=null) light.doFree();
            MemoryUtil.memFree(atlasData); MemoryUtil.memFree(lightData); MemoryUtil.memFree(instances);
            VRenderSystem.cull=oldCull; VRenderSystem.depthTest=oldDepth; VRenderSystem.depthMask=oldMask;
            VRenderSystem.stencilTest=oldStencil; PipelineState.blendInfo.enabled=oldBlend; VRenderSystem.colorMask=oldColorMask;
        }
    }
    private static VulkanImage texture(int width,int height) {
        return VulkanImage.createTextureImage(VK_FORMAT_R8G8B8A8_UNORM,1,width,height,
                VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_SAMPLED_BIT,4,false,true);
    }
    /** Test-only raw transfer. Production screenshot opacity and asynchronous lifetime remain unchanged. */
    private record RawCapture(long buffer,long allocation,int width,int height,int format) implements AutoCloseable {
        static RawCapture record(TextureTarget target) {
            VulkanImage image=GlTexture.getVulkanImage(target.getColorTextureId());
            if(image==null || (image.format!=VK_FORMAT_R8G8B8A8_UNORM && image.format!=VK_FORMAT_B8G8R8A8_UNORM))
                throw new AssertionError("Raw shader oracle target has an unexpected format");
            int bytes=Math.multiplyExact(Math.multiplyExact(image.width,image.height),4);
            try(MemoryStack stack=MemoryStack.stackPush()) {
                var buffer=stack.mallocLong(1); var allocation=stack.mallocPointer(1);
                MemoryManager.getInstance().createBuffer(bytes,VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                        VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,buffer,allocation);
                var capture=new RawCapture(buffer.get(0),allocation.get(0),image.width,image.height,image.format);
                try { RenderTargetManager.copyColorToBuffer(image,capture.buffer); return capture; }
                catch(RuntimeException | Error failure) { capture.close(); throw failure; }
            }
        }
        NativeImage read() {
            NativeImage image=new NativeImage(width,height,false);
            try {
                MemoryManager.getInstance().MapAndCopy(allocation,width*height*4,pointer -> {
                    ByteBuffer bytes=pointer.getByteBuffer(0,width*height*4).order(ByteOrder.LITTLE_ENDIAN);
                    for(int y=0;y<height;y++) for(int x=0;x<width;x++) {
                        int rgba=bytes.getInt((y*width+x)*4);
                        image.setPixelRGBA(x,y,format==VK_FORMAT_B8G8R8A8_UNORM ? ColorUtil.BGRAtoRGBA(rgba) : rgba);
                    }
                });
                return image;
            } catch(RuntimeException | Error failure) { image.close(); throw failure; }
        }
        public void close() { MemoryManager.freeBuffer(buffer,allocation); }
    }
    private static void scene(LegacyFlywheelPipeline pipeline,float cameraZ,float start,float end,float alpha) {
        pipeline.setScene(new Matrix4f(),0,0,cameraZ,0.2f,0.4f,0.6f,start,end,alpha);
    }
    private static void record(ByteBuffer out,int at,float x,int r,int g,int b,int a,int block,int sky,float normalY) {
        out.put(at,(byte)block); out.put(at+1,(byte)sky);
        out.put(at+4,(byte)r); out.put(at+5,(byte)g); out.put(at+6,(byte)b); out.put(at+7,(byte)a);
        for(int col=0;col<4;col++) for(int row=0;row<4;row++) {
            float value=col==row ? col==0 ? 0.3f : col==1 ? 0.8f : 1 : 0;
            if(col==3 && row==0) value=x; if(col==3 && row==2) value=0.5f;
            out.putFloat(at+8+col*16+row*4,value);
        }
        for(int col=0;col<3;col++) for(int row=0;row<3;row++)
            out.putFloat(at+72+col*12+row*4,col==row ? col==1 ? normalY : 1 : 0);
    }
    private static void expect(NativeImage image,int x,int y,int[] expected) {
        int actual=image.getPixelRGBA(x,y);
        for(int component=0;component<4;component++)
            if(Math.abs((actual >>> (component*8) & 255)-expected[component])>1)
                throw new AssertionError(String.format("Flywheel material pixel (%d,%d) channel %d expected %d got %08x",x,y,component,expected[component],actual));
    }
}
