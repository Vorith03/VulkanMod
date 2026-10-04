package net.vulkanmod.render.instancing;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.vulkanmod.Initializer;
import net.vulkanmod.gl.GlTexture;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.VRenderSystem;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.shader.EffectRenderState;
import net.vulkanmod.vulkan.shader.ShaderRenderState;
import net.vulkanmod.vulkan.texture.ShaderTextureState;
import net.vulkanmod.vulkan.texture.VTextureSelector;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;

import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.lwjgl.vulkan.VK10.*;

/** Opt-in two-frame loaded-client probe. Never registers InstanceWorld, teleports, reloads or activates Backend. */
@Mod.EventBusSubscriber(modid=Initializer.MOD_ID,value=Dist.CLIENT)
public final class LegacyFlywheelWorldSmokeTest {
    private static final boolean ENABLED=Boolean.getBoolean("vulkanmod.flywheelWorldProbe");
    private static boolean finished;
    private static int waitingTicks;
    private LegacyFlywheelWorldSmokeTest() {}

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(!ENABLED || finished || event.phase!=TickEvent.Phase.END) return;
        Minecraft minecraft=Minecraft.getInstance();
        if(minecraft.level==null || minecraft.player==null) return;
        if(++waitingTicks<20) return; // Let the actual client's atlas/shaders/lightmap finish entering its world.
        if(Renderer.getInstance().isRecordingFrame() || EffectRenderState.isActive() || ShaderRenderState.isActive()
                || GameRenderer.getRendertypeSolidShader()==null) {
            if(waitingTicks<200) return;
            finished=true; Initializer.LOGGER.error("Flywheel loaded-world probe failed: no safe between-frame shader boundary"); return;
        }
        finished=true;
        try { verify(minecraft); }
        catch(Throwable failure) { Initializer.LOGGER.error("Flywheel loaded-world probe failed",failure); }
    }

    private static void verify(Minecraft minecraft) throws ReflectiveOperationException {
        RenderSystem.assertOnRenderThread();
        ClassLoader loader=Class.forName("com.jozufozu.flywheel.backend.instancing.Engine").getClassLoader();
        Class<?> taskApi=Class.forName("com.jozufozu.flywheel.backend.instancing.TaskEngine",false,loader);
        Class<?> engineApi=Class.forName("com.jozufozu.flywheel.backend.instancing.Engine",false,loader);
        Class<?> groupApi=Class.forName("com.jozufozu.flywheel.api.MaterialGroup",false,loader);
        Class<?> materialApi=Class.forName("com.jozufozu.flywheel.api.Material",false,loader);
        Class<?> specApi=Class.forName("com.jozufozu.flywheel.api.struct.StructType",false,loader);
        Class<?> instancerApi=Class.forName("com.jozufozu.flywheel.api.Instancer",false,loader);
        Class<?> eventApi=Class.forName("com.jozufozu.flywheel.event.RenderLayerEvent",false,loader);
        Constructor<?> eventConstructor=eventApi.getConstructor(ClientLevel.class,RenderType.class,PoseStack.class,RenderBuffers.class,
                double.class,double.class,double.class);
        AtomicInteger syncs=new AtomicInteger(),delegatedDraws=new AtomicInteger(),recreations=new AtomicInteger();
        Object tasks=Proxy.newProxyInstance(loader,new Class<?>[]{taskApi},(self,method,args) -> {
            if(method.getName().equals("syncPoint")) { syncs.incrementAndGet(); return null; }
            if(method.getName().equals("submit")) { ((Runnable)args[0]).run(); return null; }
            throw new AssertionError("Unexpected probe task call: "+method);
        });
        var unsupported=new LegacyFlywheelEngine.Unsupported() {
            public Object model(Object layer,Object type,Object spec,Object key,Supplier<?> supplier) { throw new AssertionError("Probe model unexpectedly unsupported"); }
            public void clearForOriginShift(BlockPos origin) {}
            public void close() {}
            public void render(Object task,Object event,BlockPos origin) { delegatedDraws.incrementAndGet(); }
        };
        var previous=new LegacyFlywheelRenderer.State();
        float fogStart=RenderSystem.getShaderFogStart(),fogEnd=RenderSystem.getShaderFogEnd();
        float[] color=RenderSystem.getShaderColor().clone();
        boolean stencil=VRenderSystem.stencilTest;
        float chunkX=VRenderSystem.getChunkOffset().getFloat(0),chunkY=VRenderSystem.getChunkOffset().getFloat(4),chunkZ=VRenderSystem.getChunkOffset().getFloat(8);
        VulkanImage atlas=null,light=null,originalAtlas=null,originalLight=null;
        int atlasId=0,lightId=0;
        TextureTarget target=null;
        ByteBuffer white=MemoryUtil.memAlloc(16*16*4);
        for(int i=0;i<16*16;i++) white.putInt(-1); white.flip();
        RenderSystem.backupProjectionMatrix(); RenderSystem.getModelViewStack().pushPose();
        try(var engine=new LegacyFlywheelEngine(loader,minecraft.level,12,tasks,unsupported,false,true)) {
            RenderType.solid().setupRenderState(); ShaderTextureState.syncFixedSamplers();
            atlasId=RenderSystem.getShaderTexture(0); lightId=RenderSystem.getShaderTexture(2);
            originalAtlas=GlTexture.getVulkanImage(atlasId); originalLight=GlTexture.getVulkanImage(lightId);
            if(atlasId<=0 || lightId<=0 || atlasId==lightId || originalAtlas==null || originalLight==null)
                throw new IllegalStateException("Loaded world atlas/lightmap identities are unavailable");
            RenderType.solid().clearRenderState(); previous.restore();
            atlas=whiteTexture(1,white.duplicate().limit(4)); light=whiteTexture(16,white.duplicate()); Vulkan.waitIdle();
            // Swap only the numeric emulation mappings during this isolated probe; original images retain ownership.
            GlTexture.setVulkanImage(atlasId,atlas); GlTexture.setVulkanImage(lightId,light);
            VTextureSelector.bindTexture(0,atlas); VTextureSelector.bindTexture(2,light);
            RenderSystem.setProjectionMatrix(new Matrix4f(),VertexSorting.DISTANCE_TO_ORIGIN);
            RenderSystem.getModelViewStack().last().pose().identity(); RenderSystem.getModelViewStack().last().normal().identity();
            RenderSystem.applyModelViewMatrix(); VRenderSystem.setChunkOffset(0,0,0);
            RenderSystem.setShaderColor(1,1,1,1); RenderSystem.setShaderFogStart(100); RenderSystem.setShaderFogEnd(200);
            VRenderSystem.stencilTest=false;
            Object group=engineApi.getMethod("defaultSolid").invoke(engine.engine());
            Object modelSpec=Class.forName("com.jozufozu.flywheel.core.materials.model.ModelType",false,loader).getConstructor().newInstance();
            Object orientedSpec=Class.forName("com.jozufozu.flywheel.core.materials.oriented.OrientedType",false,loader).getConstructor().newInstance();
            var material=groupApi.getMethod("material",specApi); var model=materialApi.getMethod("model",Object.class,Supplier.class);
            Supplier<?> geometry=() -> LegacyFlywheelNativeSmokeTest.model(loader,0.3f,0.8f);
            Object gpuOwner=model.invoke(material.invoke(group,modelSpec),"gpu",geometry);
            Object cpuOwner=model.invoke(material.invoke(group,orientedSpec),"cpu",geometry);
            var create=instancerApi.getMethod("createInstance");
            Runnable recreate=() -> {
                try {
                    if(syncs.get()==0) throw new AssertionError("Origin recreation preceded task synchronization");
                    Object gpu=create.invoke(gpuOwner),cpu=create.invoke(cpuOwner);
                    ((Matrix4f)gpu.getClass().getField("model").get(gpu)).translation(-0.5f,0,0.5f);
                    cpu.getClass().getField("posX").setFloat(cpu,0.5f); cpu.getClass().getField("posZ").setFloat(cpu,0.5f);
                    basic(gpu,128,64,32,128); basic(cpu,200,100,50,255); recreations.incrementAndGet();
                } catch(ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
            };
            engine.addOriginListener(recreate);
            target=new TextureTarget(80,48,true,Minecraft.ON_OSX); Renderer renderer=Renderer.getInstance();
            var camera=new ProbeCamera();
            for(int pass=0;pass<2;pass++) {
                double x=30_000_000.25-pass*201,y=-299.5,z=-29_999_999.875;
                camera.move(x,y,z); engine.beginFrame(camera);
                PoseStack stack=new PoseStack();
                Object event=eventConstructor.newInstance(minecraft.level,RenderType.solid(),stack,minecraft.renderBuffers(),x,y,z);
                Matrix4f preCamera=new Matrix4f(stack.last().pose()); stack.translate(-x,-y,-z);
                Matrix4f caller=new Matrix4f(stack.last().pose());
                reject(() -> engine.render(new Object(),event));
                Object foreign=eventConstructor.newInstance(null,RenderType.solid(),new PoseStack(),minecraft.renderBuffers(),x,y,z);
                reject(() -> engine.render(tasks,foreign));
                Object crumbling=eventConstructor.newInstance(minecraft.level,RenderType.lines(),new PoseStack(),minecraft.renderBuffers(),x,y,z);
                reject(() -> engine.render(tasks,crumbling));
                renderer.resetBuffers(); renderer.beginFrame(); target.setClearColor(0,0,0,1); target.clear(Minecraft.ON_OSX); target.bindWrite(true);
                Renderer.resetScissor(); Renderer.setDepthBias(0,0); engine.render(tasks,event);
                if(!caller.equals(stack.last().pose()) || !preCamera.equals((Matrix4f)eventApi.getField("viewProjection").get(event)))
                    throw new AssertionError("Engine mutated loaded-world event matrices");
                try(var capture=LegacyFlywheelPipelineSmokeTest.RawCapture.record(target)) {
                    target.unbindWrite(); minecraft.getMainRenderTarget().bindWrite(true); renderer.endFrame(); Vulkan.waitIdle();
                    try(var image=capture.read()) {
                        expect(image.getPixelRGBA(10,36),new int[]{128,64,32,128});
                        float shade=minecraft.level.effects().constantAmbientLight() ? 0.9f : 1;
                        expect(image.getPixelRGBA(50,36),new int[]{(int)(200*shade),(int)(100*shade),(int)(50*shade),255});
                        expect(image.getPixelRGBA(30,12),new int[]{0,0,0,255});
                    }
                }
            }
            if(recreations.get()!=2 || delegatedDraws.get()!=2 || syncs.get()!=4)
                throw new AssertionError("World Engine recreation/task/fallback routing count differs");
            Initializer.LOGGER.info("Flywheel loaded-world probe passed: actual ClientLevel/RenderLayerEvent, mixed transformed GPU + Oriented CPU pixels, distant fractional origin/recreation, task/world/crumbling rejection and caller matrix isolation; synthetic textures; backend remains off; portal/reload/custom coverage remains open");
        } finally {
            // Probe-only waits. Default gameplay never enters this method or changes these mappings.
            if(Renderer.getInstance().isRecordingFrame()) { minecraft.getMainRenderTarget().bindWrite(true); Renderer.getInstance().endFrame(); }
            Vulkan.waitIdle();
            if(originalAtlas!=null) GlTexture.setVulkanImage(atlasId,originalAtlas);
            if(originalLight!=null) GlTexture.setVulkanImage(lightId,originalLight);
            previous.restore(); RenderSystem.restoreProjectionMatrix(); RenderSystem.getModelViewStack().popPose(); RenderSystem.applyModelViewMatrix();
            RenderSystem.setShaderColor(color[0],color[1],color[2],color[3]); RenderSystem.setShaderFogStart(fogStart); RenderSystem.setShaderFogEnd(fogEnd);
            VRenderSystem.setChunkOffset(chunkX,chunkY,chunkZ); VRenderSystem.stencilTest=stencil;
            if(target!=null) target.destroyBuffers(); if(atlas!=null) atlas.free(); if(light!=null) light.free(); MemoryUtil.memFree(white);
        }
    }
    private static VulkanImage whiteTexture(int size,ByteBuffer data) {
        VulkanImage image=VulkanImage.createTextureImage(VK_FORMAT_R8G8B8A8_UNORM,1,size,size,VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_SAMPLED_BIT,4,false,true);
        VTextureSelector.setActiveTexture(0); VTextureSelector.bindTexture(image); VTextureSelector.uploadSubTexture(0,size,size,0,0,0,0,size,data); image.readOnlyLayout(); return image;
    }
    private static void basic(Object data,int r,int g,int b,int a) throws ReflectiveOperationException {
        Class<?> type=data.getClass(); type.getField("r").setByte(data,(byte)r); type.getField("g").setByte(data,(byte)g);
        type.getField("b").setByte(data,(byte)b); type.getField("a").setByte(data,(byte)a);
        type.getField("blockLight").setByte(data,(byte)15); type.getField("skyLight").setByte(data,(byte)15);
    }
    private static void reject(Runnable call) {
        try { call.run(); } catch(IllegalArgumentException | UnsupportedOperationException expected) { return; }
        throw new AssertionError("Foreign/unsupported world event accepted");
    }
    private static void expect(int actual,int[] expected) {
        for(int i=0;i<4;i++) if(Math.abs((actual >>> (i*8) & 255)-expected[i])>1)
            throw new AssertionError(String.format("World Engine pixel expected %s got %08x",java.util.Arrays.toString(expected),actual));
    }
    private static final class ProbeCamera extends Camera { void move(double x,double y,double z) { setPosition(x,y,z); } }
}
