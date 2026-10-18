package net.vulkanmod.mixin.render.vertex;

import com.mojang.blaze3d.vertex.*;
import net.vulkanmod.interfaces.ExtendedVertexBuilder;
import net.vulkanmod.interfaces.BufferBuilderMemory;
import net.vulkanmod.interfaces.VertexFormatMixed;
import net.vulkanmod.render.vertex.VertexUtil;
import net.vulkanmod.render.profiling.ParticleBufferGrowthAttribution;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.IntConsumer;

@Mixin(BufferBuilder.class)
public abstract class BufferBuilderM extends DefaultedVertexConsumer
        implements BufferVertexConsumer, ExtendedVertexBuilder, BufferBuilderMemory, net.vulkanmod.interfaces.ParticleBillboardWriter {

    @Override
    public void putBulkData(PoseStack.Pose pose, net.minecraft.client.renderer.block.model.BakedQuad quad,
                            float[] brightness, float red, float green, float blue, float alpha,
                            int[] light, int overlay, boolean useColor) {
        net.vulkanmod.render.texture.SpriteAnimationUsage.use(quad.getSprite());
        BufferVertexConsumer.super.putBulkData(pose, quad, brightness, red, green, blue, alpha, light, overlay, useColor);
    }

    @Shadow private int vertices;
    @Shadow private int renderedBufferPointer;
    @Shadow private VertexFormat.Mode mode;
    @Shadow private boolean building;
    @Shadow @Nullable private VertexSorting sorting;
    @Shadow @Nullable private Vector3f[] sortingPoints;
    @Shadow private boolean indexOnly;
    @Shadow private void ensureCapacity(int bytes) { throw new AssertionError(); }
    @org.spongepowered.asm.mixin.Unique
    private net.vulkanmod.render.particle.ParticleQuadBatch vulkanmod$billboards;

    public boolean vulkanmod$canWriteBillboard() {
        return ((Object)this == Tesselator.getInstance().getBuilder()
                || Boolean.getBoolean("vulkanmod.ciScreenshotSmoke"))
                && building && format == DefaultVertexFormat.PARTICLE && mode == VertexFormat.Mode.QUADS
                && elementIndex == 0 && (vertices & 3) == 0 && sorting == null && sortingPoints == null
                && !indexOnly && !defaultColorSet
                && (vulkanmod$billboards == null || vulkanmod$billboards.count() < net.vulkanmod.render.particle.ParticleQuadBatch.MAX_QUADS);
    }

    public void vulkanmod$billboard(float x,float y,float z,org.joml.Quaternionf q,float size,
                                   float u0,float u1,float v0,float v1,int color,int light) {
        if(!vulkanmod$canWriteBillboard()) throw new IllegalStateException("Particle builder changed during callbacks");
        int bytes=net.vulkanmod.render.particle.ParticleQuadBatch.QUAD_BYTES;
        ensureCapacity(bytes + format.getVertexSize());
        if(vulkanmod$billboards == null) vulkanmod$billboards=new net.vulkanmod.render.particle.ParticleQuadBatch();
        vulkanmod$billboards.add(nextElementByte-renderedBufferPointer);
        net.vulkanmod.render.particle.ParticleQuadBatch.pack(buffer,nextElementByte,x,y,z,q,size,u0,u1,v0,v1,color,light);
        nextElementByte+=bytes;
        vertices+=4;
    }

    @Inject(method="storeRenderedBuffer", at=@At("RETURN"))
    private void vulkanmod$publishBillboards(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<BufferBuilder.RenderedBuffer> ci) {
        ((net.vulkanmod.interfaces.ParticleBillboardBuffer)(Object)ci.getReturnValue()).vulkanmod$billboards(vulkanmod$billboards);
        vulkanmod$billboards=null;
    }

    private void vulkanmod$materializeBillboards() {
        if(vulkanmod$billboards == null) return;
        java.nio.ByteBuffer slice=buffer.duplicate().order(buffer.order());
        slice.position(renderedBufferPointer).limit(nextElementByte);
        vulkanmod$billboards.expandCpu(slice);
        net.vulkanmod.render.particle.ParticleBillboardCompute.cpuRecovery(vulkanmod$billboards.count());
        vulkanmod$billboards=null;
    }

    @Inject(method="setQuadSorting", at=@At("HEAD"))
    private void vulkanmod$beforeSorting(VertexSorting sorting, CallbackInfo ci) { vulkanmod$materializeBillboards(); }
    @Inject(method="getSortState", at=@At("HEAD"))
    private void vulkanmod$beforeSortState(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<BufferBuilder.SortState> ci) { vulkanmod$materializeBillboards(); }
    @Inject(method="restoreSortState", at=@At("HEAD"))
    private void vulkanmod$beforeRestore(BufferBuilder.SortState state, CallbackInfo ci) { vulkanmod$materializeBillboards(); }

    @Inject(method={"discard", "reset"}, at=@At("RETURN"))
    private void vulkanmod$discardBillboards(CallbackInfo ci) { vulkanmod$billboards=null; }

    @Shadow public abstract void endVertex();
    @Shadow public abstract void discard();

    @Shadow private ByteBuffer buffer;

    @Shadow private int nextElementByte;
    @Shadow private boolean fastFormat;
    @Shadow private boolean fullFormat;
    @Shadow private int elementIndex;
    @Shadow private @Nullable VertexFormatElement currentElement;
    @Shadow private VertexFormat format;

//    @Shadow @Nullable private Vector3f[] sortingPoints;
//    @Shadow private float sortX;
//    @Shadow private float sortY;
//    @Shadow private float sortZ;
//    @Shadow private VertexFormat.Mode mode;

    @Shadow protected abstract it.unimi.dsi.fastutil.ints.IntConsumer intConsumer(int i, VertexFormat.IndexType indexType);

    private long bufferPtr;

    @Override
    public long vulkanmod$retainedBytes() {
        return this.buffer == null ? 0 : this.buffer.capacity();
    }

    @Override
    public void vulkanmod$releaseMemory() {
        com.mojang.blaze3d.systems.RenderSystem.assertOnRenderThread();
        if (this.buffer == null) return;
        this.discard();
        MemoryUtil.memFree(this.buffer);
        this.buffer = null;
        this.bufferPtr = 0L;
    }

    @Inject(method = "begin", at = @At("HEAD"))
    private void rejectRetiredBuilder(VertexFormat.Mode mode, VertexFormat format, CallbackInfo ci) {
        if (this.buffer == null) throw new IllegalStateException("BufferBuilder native allocation is retired");
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void setPtrC(int initialCapacity, CallbackInfo ci) {
        this.bufferPtr = MemoryUtil.memAddress0(this.buffer);
    }

    // Original ensureCapacity is also called when no native growth is needed.
    // Updating the pointer on every check does repeated ByteBuffer address work.
    // Intercept only the original resize site, preserving its allocation policy and
    // updating the cached pointer before the grown allocation can be written to.
    @Redirect(method = "ensureCapacity", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/MemoryTracker;resize(Ljava/nio/ByteBuffer;I)Ljava/nio/ByteBuffer;"),
            require = 1)
    private ByteBuffer vulkanmod$resizeAndUpdatePointer(ByteBuffer previous, int capacity) {
        boolean measure = ParticleBufferGrowthAttribution.isActive();
        long before = measure ? System.nanoTime() : 0L;
        ByteBuffer resized = com.mojang.blaze3d.platform.MemoryTracker.resize(previous, capacity);
        if (measure) {
            ParticleBufferGrowthAttribution.resized(previous.capacity(), capacity,
                    resized.capacity(), System.nanoTime() - before);
        }
        this.bufferPtr = MemoryUtil.memAddress0(resized);
        return resized;
    }

    @Override
    public void vertex(float x, float y, float z, int packedColor, float u, float v, int overlay, int light, int packedNormal) {
        if (this.format == DefaultVertexFormat.NEW_ENTITY) {
            long ptr = this.nextElementPtr();
            MemoryUtil.memPutFloat(ptr, x);
            MemoryUtil.memPutFloat(ptr + 4, y);
            MemoryUtil.memPutFloat(ptr + 8, z);
            MemoryUtil.memPutInt(ptr + 12, packedColor);
            MemoryUtil.memPutFloat(ptr + 16, u);
            MemoryUtil.memPutFloat(ptr + 20, v);
            MemoryUtil.memPutInt(ptr + 24, overlay);
            MemoryUtil.memPutInt(ptr + 28, light);
            MemoryUtil.memPutInt(ptr + 32, packedNormal);
            this.nextElementByte += DefaultVertexFormat.NEW_ENTITY.getVertexSize();
            this.endVertex();
            return;
        }

        ExtendedVertexBuilder.emitFallback(this, x, y, z, packedColor, u, v, overlay, light, packedNormal);
    }

    /**
     * @author
     */
    @Overwrite
    public void vertex(float x, float y, float z, float red, float green, float blue, float alpha, float u, float v, int overlay, int light, float normalX, float normalY, float normalZ) {
        if (this.fastFormat) {
            long ptr = this.nextElementPtr();
            MemoryUtil.memPutFloat(ptr + 0, x);
            MemoryUtil.memPutFloat(ptr + 4, y);
            MemoryUtil.memPutFloat(ptr + 8, z);

            int temp = VertexUtil.packColor(red, green, blue, alpha);
            MemoryUtil.memPutInt(ptr + 12, temp);

            MemoryUtil.memPutFloat(ptr + 16, u);
            MemoryUtil.memPutFloat(ptr + 20, v);

            byte i;
            if (this.fullFormat) {
                MemoryUtil.memPutInt(ptr + 24, overlay);
                i = 28;
            } else {
                i = 24;
            }

            MemoryUtil.memPutInt(ptr + i, light);

            temp = VertexUtil.packNormal(normalX, normalY, normalZ);
            MemoryUtil.memPutInt(ptr + i + 4, temp);

            this.nextElementByte += i + 8;
            this.endVertex();
        } else {
            super.vertex(x, y, z, red, green, blue, alpha, u, v, overlay, light, normalX, normalY, normalZ);
        }
    }

    /**
     * @author
     */
    @Overwrite
    public void nextElement() {
        VertexFormatElement vertexFormatElement;
        List<VertexFormatElement> list = ((VertexFormatMixed)(this.format)).getFastList();

        // elementIndex starts at zero and only advances through this method.
        // A bounds check avoids integer remainder on every particle vertex element.
        int nextIndex = this.elementIndex + 1;
        this.elementIndex = nextIndex == list.size() ? 0 : nextIndex;
        this.nextElementByte += this.currentElement.getByteSize();

        this.currentElement = vertexFormatElement = list.get(this.elementIndex);

        if (vertexFormatElement.getUsage() == VertexFormatElement.Usage.PADDING) {
            this.nextElement();
        }
        if (this.defaultColorSet && this.currentElement.getUsage() == VertexFormatElement.Usage.COLOR) {
            BufferVertexConsumer.super.color(this.defaultR, this.defaultG, this.defaultB, this.defaultA);
        }
    }

//    /**
//     * @author
//     */
//    @Overwrite
//    private void putSortedQuadIndices(VertexFormat.IndexType indexType) {
//        float[] distances = new float[this.sortingPoints.length];
//        int[] is = new int[this.sortingPoints.length];
//
//        for(int i = 0; i < this.sortingPoints.length; is[i] = i++) {
//            float f = this.sortingPoints[i].x() - this.sortX;
//            float g = this.sortingPoints[i].y() - this.sortY;
//            float h = this.sortingPoints[i].z() - this.sortZ;
//            distances[i] = f * f + g * g + h * h;
//        }
//
////		IntArrays.mergeSort(is, (ix, jx) -> Floats.compare(distances[jx], distances[ix]));
//
//        IntConsumer intConsumer = this.intConsumer(this.nextElementByte, indexType);
//
//        for(int i = 0; i < is.length; ++i) {
//            int j = is[i];
//            intConsumer.accept(j * this.mode.primitiveStride + 0);
//            intConsumer.accept(j * this.mode.primitiveStride + 1);
//            intConsumer.accept(j * this.mode.primitiveStride + 2);
//            intConsumer.accept(j * this.mode.primitiveStride + 2);
//            intConsumer.accept(j * this.mode.primitiveStride + 3);
//            intConsumer.accept(j * this.mode.primitiveStride + 0);
//        }
//    }

    public void putByte(int index, byte value) {
        MemoryUtil.memPutByte(this.bufferPtr + this.nextElementByte + index, value);
    }

    public void putShort(int index, short value) {
        MemoryUtil.memPutShort(this.bufferPtr + this.nextElementByte + index, value);
    }

    public void putFloat(int index, float value) {
        MemoryUtil.memPutFloat(this.bufferPtr + this.nextElementByte + index, value);
    }

    private long nextElementPtr() {
        return (this.bufferPtr + this.nextElementByte);
    }

    protected void setNextElementByte(int i) {
        this.nextElementByte = i;
    }
}
