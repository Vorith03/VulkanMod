package net.vulkanmod.render.particle;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.ShaderInstance;
import com.mojang.blaze3d.systems.RenderSystem;
import net.vulkanmod.Initializer;
import net.vulkanmod.interfaces.BufferBuilderMemory;
import net.vulkanmod.interfaces.ParticleBillboardBuffer;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.Device;
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
        BufferBuilder sortDuringLight;
        public Probe(float x) { super(null,x,0,0.5); this.xo=x-0.1; this.yo=-0.05; this.zo=0.45; this.quadSize=0.2f; }
        public void configure(float roll) { this.roll=roll; this.oRoll=roll-0.3f; rCol=0.3f;gCol=0.7f;bCol=0.9f;alpha=0.6f; }
        @Override public float getQuadSize(float tick) { sizes++; return super.getQuadSize(tick); }
        @Override protected float getU0() { return 0.125f; }
        @Override protected float getU1() { return 0.875f; }
        @Override protected float getV0() { return 0.25f; }
        @Override protected float getV1() { return 0.75f; }
        @Override public int getLightColor(float tick) {
            lights++;
            if(sortDuringLight != null) {
                sortDuringLight.setQuadSorting(VertexSorting.DISTANCE_TO_ORIGIN);
                sortDuringLight = null;
            }
            return 0x00A00070;
        }
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

    /**
     * A callback changes the builder after the mixin's first admission test.
     * Sorting must recover any earlier packed quad and the current particle
     * must use the captured CPU fallback without invoking its callbacks twice.
     */
    private static BufferBuilder.RenderedBuffer buildCallbackMutation(BufferBuilder builder, Camera camera) {
        builder.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.PARTICLE);
        for(int i=0;i<3;i++) {
            Probe p=new Probe(i/11f);
            p.configure(i==0?0:0.73f);
            if(i==1) p.sortDuringLight=builder;
            p.render(builder,camera,0.37f);
            if(p.lights!=1 || p.sizes!=1)
                throw new AssertionError("Callback-state recovery duplicated/skipped callbacks");
        }
        return builder.end();
    }

    private static void verifyCallbackMutation(BufferBuilder cpu, BufferBuilder gpu, Camera camera) {
        ByteBuffer reference=null;
        try {
            ParticleBillboardCompute.ciUnavailable(true);
            var expected=buildCallbackMutation(cpu,camera);
            reference=MemoryUtil.memAlloc(expected.vertexBuffer().remaining()).order(ByteOrder.nativeOrder());
            reference.put(expected.vertexBuffer()).flip();
            expected.release();
            ParticleBillboardCompute.ciUnavailable(false);
            long recoveredBefore=ParticleBillboardCompute.callbackStateRecoveriesForSmoke();
            var actual=buildCallbackMutation(gpu,camera);
            try {
                if(((ParticleBillboardBuffer)(Object)actual).vulkanmod$billboards()!=null)
                    throw new AssertionError("Callback mutation left compact GPU records in sorted stream");
                if(ParticleBillboardCompute.callbackStateRecoveriesForSmoke()!=recoveredBefore+1)
                    throw new AssertionError("Late callback mutation did not take captured CPU fallback");
                ByteBuffer produced=actual.vertexBuffer();
                if(reference.remaining()!=produced.remaining())
                    throw new AssertionError("Callback recovery changed particle vertex count");
                for(int at=0;at<reference.remaining();at+=ParticleQuadBatch.STRIDE) {
                    for(int f=0;f<5;f++) {
                        float a=reference.getFloat(at+4*f),b=produced.getFloat(at+4*f);
                        if(!Float.isFinite(b) || Math.abs(a-b)>0.000002f)
                            throw new AssertionError("Callback CPU recovery geometry/UV at "+at+" component="+f);
                    }
                    if(reference.getInt(at+20)!=produced.getInt(at+20)
                            || reference.getInt(at+24)!=produced.getInt(at+24))
                        throw new AssertionError("Callback CPU recovery altered color/light");
                }
            } finally { actual.release(); }
        } finally {
            ParticleBillboardCompute.ciUnavailable(false);
            if(reference!=null) MemoryUtil.memFree(reference);
        }
    }

    /**
     * Real Vulkan descriptor exhaustion, not the CI forced-unavailable switch.
     * Every accepted compact quad occupies its original vertex slot; a rejected
     * batch is restored on the CPU before upload, with no compute dispatch.
     * Freeing the temporary buffers schedules normal fence-owned retirement.
     */
    private static int verifyDescriptorPressure() {
        ParticleVertexBuffer[] buffers = new ParticleVertexBuffer[80];
        ByteBuffer compact = MemoryUtil.memAlloc(ParticleQuadBatch.QUAD_BYTES)
                .order(ByteOrder.nativeOrder());
        try {
            ParticleQuadBatch jobs = new ParticleQuadBatch();
            jobs.add(0);
            ParticleQuadBatch.pack(compact, 0, 0.25f, -0.5f, 0.75f,
                    new Quaternionf(), 0.5f, 0.125f, 0.875f, 0.25f, 0.75f,
                    0xffffffff, 0x00f000f0);
            long rejectedBefore = ParticleBillboardCompute.descriptorRejectionsForSmoke();
            int reservedBefore = ParticleBillboardCompute.allocatedBindingsForSmoke();
            int accepted = 0;
            boolean rejected = false;
            for(int i = 0; i < buffers.length; ++i) {
                buffers[i] = new ParticleVertexBuffer(16);
                if(!ParticleBillboardCompute.expand(buffers[i], compact, jobs)) {
                    rejected = true;
                    // Same compact source must remain usable by the CPU fallback.
                    jobs.expandCpu(compact);
                    for(int vertex = 0; vertex < 4; ++vertex) {
                        if(!Float.isFinite(compact.getFloat(vertex * ParticleQuadBatch.STRIDE)))
                            throw new AssertionError("Descriptor pressure corrupted CPU recovery");
                    }
                    break;
                }
                accepted++;
            }
            if(!rejected || accepted == 0
                    || ParticleBillboardCompute.descriptorRejectionsForSmoke() <= rejectedBefore)
                throw new AssertionError("Descriptor pool pressure did not fail closed: accepted=" + accepted
                        + " rejected=" + rejected + " " + ParticleBillboardCompute.statistics());
            // The rejected draw must not consume an additional descriptor reservation.
            if(ParticleBillboardCompute.allocatedBindingsForSmoke()!=reservedBefore+accepted)
                throw new AssertionError("Descriptor pool reservation accounting drifted under pressure");
            return accepted;
        } finally {
            for(ParticleVertexBuffer pressure : buffers)
                if(pressure != null) pressure.freeBuffer();
            MemoryUtil.memFree(compact);
        }
    }

    public static void verify(Minecraft minecraft) throws Exception {
        if(!ParticleBillboardCompute.requested()) throw new AssertionError("Particle pilot disabled in native oracle");
        BufferBuilder cpu=new BufferBuilder(16), gpu=new BufferBuilder(16), sorted=new BufferBuilder(16);
        ParticleVertexBuffer buffer=new ParticleVertexBuffer(16);
        ByteBuffer reference=null;
        Camera camera=new Camera();
        // Deliberately non-unit; JOML transform divides by quaternion norm.
        Quaternionf rotation=new Quaternionf().rotationYXZ(0.43f,-0.21f,0.19f);
        camera.rotation().set(rotation.x*1.3f,rotation.y*1.3f,rotation.z*1.3f,rotation.w*1.3f);
        Renderer renderer=Renderer.getInstance();
        ShaderInstance previous=RenderSystem.getShader();
        // Constructor-return smoke precedes GameRenderer's asynchronous core shader reload.
        ShaderInstance shader=new ShaderInstance(minecraft.getResourceManager(),"particle",DefaultVertexFormat.PARTICLE);
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
                // First pass forces a nonzero, aligned descriptor window in the
                // original frame-slot vertex buffer. The GPU must still draw and
                // match exact CPU reference pixels without a new buffer system.
                if(pass==0) {
                    try(var stack=org.lwjgl.system.MemoryStack.stackPush()) {
                        buffer.copyToVertexBuffer(1,32768,stack.calloc(32768));
                    }
                    ParticleBillboardCompute.ciStorageRangeLimit(32768);
                }
                boolean expanded;
                var limits=Device.deviceProperties.limits();
                int groups=limits.maxComputeWorkGroupCount(0);
                try {
                    // Still only two workgroups: model a uint32 host limit, not
                    // extra device capability or a larger native dispatch.
                    if(pass==0) limits.maxComputeWorkGroupCount().put(0,-1);
                    expanded=ParticleBillboardCompute.expand(buffer,actual.vertexBuffer(),jobs);
                } finally {
                    ParticleBillboardCompute.ciStorageRangeLimit(0);
                    limits.maxComputeWorkGroupCount().put(0,groups);
                }
                if(!expanded)
                    throw new AssertionError("Compute did not accept real particle batch");
                long slice=buffer.getOffset();
                // Also exercise the real BufferUploader path and its original shader/state.
                long before=ParticleBillboardCompute.gpuQuads();
                RenderSystem.setShader(()->shader);
                BufferUploader.drawWithShader(actual);
                if(ParticleBillboardCompute.gpuQuads()!=before+87) throw new AssertionError("Production uploader kept CPU shadow");
                verifyCallbackMutation(cpu,gpu,camera);
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
                // Exercise safe device-idle teardown with frame-retired descriptors
                // still scheduled, then rebuild the pipeline on the next pass.
                if(pass == 1) ParticleBillboardCompute.shutdownAfterDeviceIdle();
                // Force another VkBuffer generation while older descriptor retirement is pending.
                buffer.reserve(buffer.getBufferSize()+16);
            }
            // Pressure is isolated in its own final recording frame. Fence-retired
            // sets legitimately stay charged until later frame slots recycle;
            // do not require unrelated geometry passes to use those slots.
            renderer.resetBuffers();
            renderer.beginFrame();
            int firstAccepted=verifyDescriptorPressure();
            int pressureReservations=ParticleBillboardCompute.allocatedBindingsForSmoke();
            renderer.endFrame();
            Vulkan.waitIdle();
            // The owner frees rejected/accepted temporary buffers via frame ops, not
            // immediately. Cycle the actual submitted frame slots (and their fences)
            // before expecting the same fixed descriptor pool to admit new work.
            for(int slot=0;slot<MemoryManager.getFrames();slot++) {
                renderer.resetBuffers();
                renderer.beginFrame();
                renderer.endFrame();
                Vulkan.waitIdle(); // Fixture only; production does not add waits.
            }
            int afterRetirement=ParticleBillboardCompute.allocatedBindingsForSmoke();
            if(afterRetirement>=pressureReservations)
                throw new AssertionError("Particle descriptor frame retirement never released reservations: before="
                        +pressureReservations+" after="+afterRetirement);
            renderer.resetBuffers();
            renderer.beginFrame();
            int secondAccepted=verifyDescriptorPressure();
            if(secondAccepted<firstAccepted)
                throw new AssertionError("Particle descriptor pool did not recover after frame fences: first="
                        +firstAccepted+" second="+secondAccepted);
            renderer.endFrame();
            Vulkan.waitIdle();
            Initializer.LOGGER.info("GPU particle billboard smoke passed: transformed inherited render, 87/131 mixed quads, all geometry/UV/color/light, nonunit rotation/roll, exact callbacks, callback-time sorting state recovery, production uploader, CPU sorting/recovery, descriptor pressure and frame-slot reuse, device-idle reinitialization, growth and generation retirement; {}",ParticleBillboardCompute.statistics());
        } finally {
            ParticleBillboardCompute.ciUnavailable(false);
            RenderSystem.setShader(()->previous);
            Vulkan.waitIdle();
            if(reference!=null) MemoryUtil.memFree(reference);
            shader.close();
            buffer.freeBuffer();
            for(BufferBuilder builder:new BufferBuilder[]{cpu,gpu,sorted}) ((BufferBuilderMemory)(Object)builder).vulkanmod$releaseMemory();
        }
    }
}
