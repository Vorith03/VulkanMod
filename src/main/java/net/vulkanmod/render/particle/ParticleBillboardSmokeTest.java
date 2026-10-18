package net.vulkanmod.render.particle;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.GameRenderer;
import net.vulkanmod.Initializer;
import net.vulkanmod.interfaces.BufferBuilderMemory;
import net.vulkanmod.interfaces.ParticleBillboardBuffer;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.memory.MemoryManager;
import org.joml.Quaternionf;
import org.lwjgl.system.MemoryUtil;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Runs transformed 1.20.1 inherited render, the real builder, compute, and production uploader. */
public final class ParticleBillboardSmokeTest {
    private ParticleBillboardSmokeTest() {}
    public static class Probe extends SingleQuadParticle {
        int lights, sizes;
        public Probe(float x) { super(null,x,0,0.5); this.xo=x-0.1; this.yo=-0.05; this.zo=0.45; this.quadSize=0.2f; }
        public void configure(float roll) { this.roll=roll; this.oRoll=roll-0.3f; rCol=0.3f;gCol=0.7f;bCol=0.9f;alpha=0.6f; }
        @Override public float getQuadSize(float tick) { sizes++; return super.getQuadSize(tick); }
        @Override protected float getU0() { return 0.125f; }
        @Override protected float getU1() { return 0.875f; }
        @Override protected float getV0() { return 0.25f; }
        @Override protected float getV1() { return 0.75f; }
        @Override public int getLightColor(float tick) { lights++; return 0x00A00070; }
        @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }
    }
    /** Exact-class exclusion must preserve inherited/custom subclasses. */
    public static final class CustomProbe extends Probe { public CustomProbe(float x) { super(x); } }
    private static BufferBuilder.RenderedBuffer build(BufferBuilder builder, Camera camera) {
        builder.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.PARTICLE);
        for(int i=0;i<131;i++) {
            Probe p=i%3==1?new CustomProbe(i/300f):new Probe(i/300f);
            p.configure(i%2==0?0:0.73f);
            p.render(builder,camera,0.37f);
            if(p.lights!=1 || p.sizes!=1) throw new AssertionError("Particle callbacks were skipped/duplicated");
        }
        return builder.end();
    }
    public static void verify(Minecraft minecraft) {
        if(!ParticleBillboardCompute.requested()) throw new AssertionError("Particle pilot disabled in native oracle");
        BufferBuilder cpu=new BufferBuilder(16), gpu=new BufferBuilder(16), sorted=new BufferBuilder(16);
        ParticleVertexBuffer buffer=new ParticleVertexBuffer(16);
        ByteBuffer reference=null;
        Camera camera=new Camera();
        // Deliberately non-unit; JOML transform divides by quaternion norm.
        Quaternionf rotation=new Quaternionf().rotationYXZ(0.43f,-0.21f,0.19f);
        camera.rotation().set(rotation.x*1.3f,rotation.y*1.3f,rotation.z*1.3f,rotation.w*1.3f);
        Renderer renderer=Renderer.getInstance();
        try {
            for(int pass=0;pass<3;pass++) {
                renderer.resetBuffers(); renderer.beginFrame();
                ParticleBillboardCompute.ciUnavailable(true);
                var expected=build(cpu,camera);
                if(((ParticleBillboardBuffer)(Object)expected).vulkanmod$billboards()!=null)
                    throw new AssertionError("Forced unavailable path bypassed CPU");
                reference=MemoryUtil.memAlloc(expected.vertexBuffer().remaining()).order(ByteOrder.nativeOrder());
                reference.put(expected.vertexBuffer()).flip(); expected.release();
                ParticleBillboardCompute.ciUnavailable(false);
                var actual=build(gpu,camera);
                var jobs=((ParticleBillboardBuffer)(Object)actual).vulkanmod$billboards();
                if(jobs==null || jobs.count()!=87 || jobs.offset(1)!=224)
                    throw new AssertionError("Mixed stream lost exact GPU slots/order");
                buffer.reset();
                if(!ParticleBillboardCompute.expand(buffer,actual.vertexBuffer(),jobs))
                    throw new AssertionError("Compute did not accept real particle batch");
                long slice=buffer.getOffset();
                // Also exercise the real BufferUploader path and its original shader/state.
                long before=ParticleBillboardCompute.gpuQuads();
                com.mojang.blaze3d.systems.RenderSystem.setShader(GameRenderer::getParticleShader);
                BufferUploader.drawWithShader(actual);
                if(ParticleBillboardCompute.gpuQuads()!=before+87) throw new AssertionError("Production uploader kept CPU shadow");
                // CPU sorting reads vertex positions: materialize before that callback, never sort compact records.
                sorted.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.PARTICLE);
                new Probe(0).render(sorted,camera,0.37f);
                sorted.setQuadSorting(VertexSorting.DISTANCE_TO_ORIGIN);
                var sortedResult=sorted.end();
                if(((ParticleBillboardBuffer)(Object)sortedResult).vulkanmod$billboards()!=null)
                    throw new AssertionError("Sorted stream retained compact records");
                sortedResult.release();
                // Exceptional failure after packing must restore CPU vertices at upload.
                var recovery=build(gpu,camera);
                ParticleBillboardCompute.ciUnavailable(true);
                long recoveredBefore=ParticleBillboardCompute.gpuQuads();
                BufferUploader.drawWithShader(recovery);
                if(ParticleBillboardCompute.gpuQuads()!=recoveredBefore) throw new AssertionError("Unavailable compute executed");
                ParticleBillboardCompute.ciUnavailable(false);
                renderer.endFrame();
                Vulkan.waitIdle(); // Native oracle only: production performs no waits/readback.
                ByteBuffer expectedBytes=reference;
                MemoryManager.getInstance().MapAndCopy(buffer.getAllocation(),buffer.getBufferSize(),mapped->{
                    ByteBuffer output=mapped.getByteBuffer(0,buffer.getBufferSize())
                            .slice((int)slice,expectedBytes.remaining()).order(ByteOrder.nativeOrder());
                    for(int v=0;v<131*4;v++) {
                        int at=v*28;
                        for(int f=0;f<5;f++) {
                            float a=expectedBytes.getFloat(at+f*4), b=output.getFloat(at+f*4);
                            if(!Float.isFinite(b) || Math.abs(a-b)>0.000002f)
                                throw new AssertionError("Billboard geometry/UV mismatch vertex="+v+" component="+f+" expected="+a+" actual="+b);
                        }
                        if(expectedBytes.getInt(at+20)!=output.getInt(at+20) || expectedBytes.getInt(at+24)!=output.getInt(at+24))
                            throw new AssertionError("Particle packed color/light mismatch");
                    }
                });
                MemoryUtil.memFree(reference); reference=null;
                // Force another VkBuffer generation while older descriptor retirement is pending.
                buffer.reserve(buffer.getBufferSize()+16);
            }
            Initializer.LOGGER.info("GPU particle billboard smoke passed: transformed inherited render, 87/131 mixed quads, all geometry/UV/color/light, nonunit rotation/roll, exact callbacks, production uploader, CPU sorting/recovery, growth and generation retirement; {}",ParticleBillboardCompute.statistics());
        } finally {
            ParticleBillboardCompute.ciUnavailable(false);
            Vulkan.waitIdle();
            if(reference!=null) MemoryUtil.memFree(reference);
            buffer.freeBuffer();
            for(BufferBuilder builder:new BufferBuilder[]{cpu,gpu,sorted}) ((BufferBuilderMemory)(Object)builder).vulkanmod$releaseMemory();
        }
    }
}
