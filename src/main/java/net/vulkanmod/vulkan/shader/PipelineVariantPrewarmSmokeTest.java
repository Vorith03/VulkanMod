package net.vulkanmod.vulkan.shader;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.vulkanmod.Initializer;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.VRenderSystem;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.memory.IndexBuffer;
import net.vulkanmod.vulkan.memory.VertexBuffer;
import net.vulkanmod.vulkan.texture.ScreenshotReadback;
import org.lwjgl.system.MemoryUtil;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.lwjgl.vulkan.VK10.*;

/** Test-only persisted-key/native replay oracle; all waits and reflection stay outside gameplay. */
public final class PipelineVariantPrewarmSmokeTest {
    private PipelineVariantPrewarmSmokeTest() {}

    public static void verify(Minecraft minecraft) throws Exception {
        var renderer = Renderer.getInstance();
        boolean cull = VRenderSystem.cull, depth = VRenderSystem.depthTest;
        boolean mask = VRenderSystem.depthMask, stencil = VRenderSystem.stencilTest;
        int color = VRenderSystem.colorMask;
        boolean blend = PipelineState.blendInfo.enabled;
        boolean clamp = DepthClampState.isEnabled();
        GraphicsPipeline seed = null, replay = null;
        TextureTarget target = null;
        VertexBuffer vertices = null;
        IndexBuffer indices = null;
        ByteBuffer vertexBytes = MemoryUtil.memAlloc(48), indexBytes = MemoryUtil.memAlloc(12);
        try {
            VRenderSystem.cull = false; VRenderSystem.depthTest = false;
            VRenderSystem.depthMask = false; VRenderSystem.stencilTest = false;
            VRenderSystem.colorMask = 15; PipelineState.blendInfo.enabled = false;
            DepthClampState.disable();
            for(float[] position : new float[][]{{-1,-1,0.5f},{1,-1,0.5f},{1,1,0.5f},{-1,1,0.5f}})
                for(float component : position) vertexBytes.putFloat(component);
            vertexBytes.flip();
            for(short index : new short[]{0,1,2,2,3,0}) indexBytes.putShort(index);
            indexBytes.flip();
            vertices = new VertexBuffer(48); vertices.copyToVertexBuffer(12,4,vertexBytes);
            indices = new IndexBuffer(12); indices.copyBuffer(indexBytes);
            target = new TextureTarget(32,24,true,Minecraft.ON_OSX);
            renderer.resetBuffers(); renderer.beginFrame(); target.bindWrite(true);
            var pass = renderer.getBoundRenderPass();
            var white = state(pass,15);
            var red = state(pass,1);
            var builder = builder();
            verifyHistory(builder,white);
            seed = builder.createGraphicsPipeline();
            seed.getHandle(red);
            GraphicsPipeline.requestPrimitiveMode(VertexFormat.Mode.DEBUG_LINES);
            seed.getHandle(white);
            seed.getHandle(white);
            // No commands reference the seed. Cleanup persists history before reconstruction.
            seed.cleanUp(); seed = null;
            replay = builder().createGraphicsPipeline();
            replay.getHandle(white);
            // The required cold pipeline may consume the entire budget. A subsequent hit
            // must allow the persisted alternatives to be created before their first use.
            for(int attempt=0; attempt<3 && handles(replay).size()<3; attempt++) replay.getHandle(white);
            check(handles(replay).size()==3,"Persisted red-mask/line variants were not prewarmed");
            var warmed = List.copyOf(handles(replay).values());
            check(warmed.contains(replay.getHandle(red)),"Red-mask replay missed the exact lookup key");
            GraphicsPipeline.requestPrimitiveMode(VertexFormat.Mode.DEBUG_LINES);
            check(warmed.contains(replay.getHandle(white)),"Line replay missed the exact topology key");
            check(handles(replay).size()==3,"Replay caused lazy duplicate native creation");
            target.unbindWrite(); minecraft.getMainRenderTarget().bindWrite(true); renderer.endFrame();
            Vulkan.waitIdle();

            for(int frame=0; frame<2; frame++) {
                if(frame==1) target.resize(40,28,Minecraft.ON_OSX);
                VRenderSystem.colorMask = frame==0 ? 1 : 15;
                renderer.resetBuffers(); renderer.beginFrame();
                target.setClearColor(0,0,0,1); target.clear(Minecraft.ON_OSX); target.bindWrite(true);
                Renderer.resetScissor(); Renderer.setDepthBias(0,0);
                renderer.bindGraphicsPipeline(replay); renderer.uploadAndBindUBOs(replay);
                Renderer.getDrawer().drawIndexed(vertices,indices,6);
                var capture = ScreenshotReadback.request(target);
                target.unbindWrite(); minecraft.getMainRenderTarget().bindWrite(true); renderer.endFrame();
                try(var image = capture.get(10,TimeUnit.SECONDS)) {
                    int expected = frame==0 ? 0xFF0000FF : 0xFFFFFFFF;
                    check(image.getPixelRGBA(image.getWidth()/2,image.getHeight()/2)==expected,
                            "Replayed color-mask/resize pixels differ from the recorded state");
                }
                check(handles(replay).size()==3,"Compatible resize or draw missed the prewarmed key");
            }
            Initializer.LOGGER.info("Graphics pipeline prewarm smoke passed: bounded history/corruption/identity, persisted native replay, exact mask/topology handles, replayed pixels and compatible resize; hardware hitch benefit remains unmeasured");
        } finally {
            if(renderer.isRecordingFrame()) {
                minecraft.getMainRenderTarget().bindWrite(true); renderer.endFrame();
            }
            Vulkan.waitIdle();
            if(seed!=null) seed.cleanUp(); if(replay!=null) replay.cleanUp();
            if(vertices!=null) vertices.freeBuffer(); if(indices!=null) indices.freeBuffer();
            if(target!=null) target.destroyBuffers();
            MemoryUtil.memFree(vertexBytes); MemoryUtil.memFree(indexBytes);
            VRenderSystem.cull=cull; VRenderSystem.depthTest=depth; VRenderSystem.depthMask=mask;
            VRenderSystem.stencilTest=stencil; VRenderSystem.colorMask=color; PipelineState.blendInfo.enabled=blend;
            if(clamp) DepthClampState.enable(); else DepthClampState.disable();
        }
    }

    private static Pipeline.Builder builder() {
        var builder = new Pipeline.Builder(DefaultVertexFormat.POSITION);
        builder.setUniforms(new ArrayList<>(),List.of());
        builder.compileShaders("""
                #version 450
                layout(location=0) in vec3 Position;
                void main() { gl_Position=vec4(Position,1.0); }
                ""","""
                #version 450
                layout(location=0) out vec4 color;
                void main() { color=vec4(1.0); }
                """);
        return builder;
    }

    private static PipelineState state(net.vulkanmod.vulkan.framebuffer.RenderPass pass,int color) {
        return new PipelineState(new PipelineState.BlendState(false,1,0,1,0,VK_BLEND_OP_ADD),
                VRenderSystem.getDepthState(),PipelineState.currentLogicOpState,
                new PipelineState.ColorMask(color),pass,VRenderSystem.getStencilState());
    }

    private static void verifyHistory(Pipeline.Builder builder,PipelineState state) throws Exception {
        var vertex=builder.vertShaderSPIRV.bytecode(); var fragment=builder.fragShaderSPIRV.bytecode();
        int vp=vertex.position(),vl=vertex.limit(),fp=fragment.position(),fl=fragment.limit();
        var session=PipelineVariantPrewarmer.open(vertex,fragment,DefaultVertexFormat.POSITION,null);
        check(session!=null,"CI did not enable prewarming");
        byte[] identity=(byte[])field(session,"key");
        check(Arrays.equals(identity,(byte[])field(PipelineVariantPrewarmer.open(vertex,fragment,DefaultVertexFormat.POSITION,null),"key")),"Identity is unstable");
        check(!Arrays.equals(identity,(byte[])field(PipelineVariantPrewarmer.open(fragment,vertex,DefaultVertexFormat.POSITION,null),"key")),"Shader identity was omitted");
        check(!Arrays.equals(identity,(byte[])field(PipelineVariantPrewarmer.open(vertex,fragment,DefaultVertexFormat.BLOCK,null),"key")),"Vertex layout identity was omitted");
        var instance=new InstanceVertexFormat(4,List.of(new InstanceVertexFormat.Attribute(1,InstanceVertexFormat.Format.FLOAT,0)));
        check(!Arrays.equals(identity,(byte[])field(PipelineVariantPrewarmer.open(vertex,fragment,DefaultVertexFormat.POSITION,instance),"key")),"Instance layout identity was omitted");
        check(vp==vertex.position() && vl==vertex.limit() && fp==fragment.position() && fl==fragment.limit(),"Identity consumed shader cursors");
        for(int i=0;i<40;i++) {
            var stencil=new PipelineState.StencilState(false,519,i,~0,~0,7680,7680,7680);
            session.observe(new PipelineState(state.blendState,state.depthState,state.logicOpState,state.colorMask,state.renderPass,stencil),
                    VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST,false);
        }
        List<?> observed=(List<?>)field(session,"observed");
        check(observed.size()==32,"History exceeded its bound");
        Method encode=PipelineVariantPrewarmer.class.getDeclaredMethod("encode",List.class);
        Method decode=PipelineVariantPrewarmer.class.getDeclaredMethod("decode",byte[].class);
        encode.setAccessible(true); decode.setAccessible(true);
        byte[] bytes=(byte[])encode.invoke(null,observed);
        check(observed.equals(decode.invoke(null,(Object)bytes)),"History state round trip changed keys");
        check(((List<?>)decode.invoke(null,(Object)Arrays.copyOf(bytes,bytes.length-1))).isEmpty(),"Truncated history accepted");
        byte[] corrupt=bytes.clone(); ByteBuffer.wrap(corrupt).order(ByteOrder.LITTLE_ENDIAN).putInt(8,33);
        check(((List<?>)decode.invoke(null,(Object)corrupt)).isEmpty(),"Oversized count accepted");
        corrupt=bytes.clone(); ByteBuffer.wrap(corrupt).order(ByteOrder.LITTLE_ENDIAN).putInt(12,2);
        check(((List<?>)decode.invoke(null,(Object)corrupt)).isEmpty(),"Malformed boolean accepted");
        // A bad enum skips only that record; valid neighbors survive.
        corrupt=bytes.clone(); ByteBuffer.wrap(corrupt).order(ByteOrder.LITTLE_ENDIAN).putInt(12+5*4,Integer.MAX_VALUE);
        check(((List<?>)decode.invoke(null,(Object)corrupt)).size()==31,"Bad topology poisoned valid history");
        // Replay reads the immutable history loaded at session creation, never this
        // session's newly recorded observations. Reconstruct the decoded session
        // without persisting the codec-only states into the native oracle's key.
        var constructor=session.getClass().getDeclaredConstructor(byte[].class,List.class);
        constructor.setAccessible(true);
        var restored=(PipelineVariantPrewarmer.Session)constructor.newInstance(identity,decode.invoke(null,(Object)bytes));
        check(restored.replays(state,true).isEmpty(),"Depth-clamp boundary crossed");
        var replays=restored.replays(state,false);
        check(!replays.isEmpty() && replays.size()<=4,"Replay count is not bounded");
        check(restored.replays(state,false).isEmpty(),"Boundary replayed twice");
        for(var replay:replays) check(replay.state().renderPass==state.renderPass,"Retired render-pass owner replayed");
        // This independent codec session is never persisted into the native oracle's key.
    }

    private static Object field(Object owner,String name) throws Exception {
        Field field=owner.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(owner);
    }
    private static Map<?,?> handles(GraphicsPipeline pipeline) throws Exception {
        return (Map<?,?>)field(pipeline,"graphicsPipelines");
    }
    private static void check(boolean condition,String message) {
        if(!condition) throw new AssertionError(message);
    }
}
