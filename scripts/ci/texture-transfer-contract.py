#!/usr/bin/env python3
"""Exercise the real unified texture transfer overlap policy with Java 17."""
from pathlib import Path
import os
import subprocess
import tempfile

root = Path(__file__).resolve().parents[2]
pkg = Path("net/vulkanmod/vulkan/texture")
def method(signature):
    source = (root / "src/main/java" / pkg / "VTextureSelector.java").read_text()
    start = source.index(signature)
    opening = source.index('{', start)
    end, depth = opening + 1, 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]

# Exercise the production enqueue and flush methods, replacing only native API
# calls with a recorder. The actual capacity, overlap and source-switch branches
# and the source/stride/offset encoding run unchanged.
production = '\n'.join(method(sig) for sig in [
    "private static void queueSpriteUploadCopy(int mipLevel, int width, int height,\n                                              int xOffset, int yOffset,\n                                              long stagingBufferId, int bufferOffset,",
    "private static void flushSpriteUploadCopies()",
    "public static boolean canQueueComputedSpriteCopies()",
    "public static void flushPendingSpriteUploadSource(",
    "public static void flushPendingSpriteUploadRange(",
    "public static int queueComputedSpriteFrame(",
    "public static int queueComputedSpriteFrame(TextureResidentBuffer source, VulkanImage texture,\n                                               int[] offsets, int frameWidth, int frameHeight, int levels,\n                                               int destX, int destY, int sourceOffset)",
    "public static void flushPendingSpriteUploadCopies()",
    "private static void queueSpriteUploadCopy(int mipLevel, int width, int height,\n                                              int xOffset, int yOffset,\n                                              long stagingBufferId, int bufferOffset)"])
harness = r'''package net.vulkanmod.vulkan.texture;
import java.util.*;
public final class TextureTransferBatchContract {
    static boolean UNIFIED_GPU_UPLOADS = true;
    static final int MAX_SPRITE_COPY_BATCH_REGIONS = 256;
    static final long VK_NULL_HANDLE = 0;
    static final int VK_IMAGE_ASPECT_COLOR_BIT=1, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL=7;
    static int spriteUploadRegionCount, barriers, spriteUploadDepth=1;
    static long spriteUploadStagingBufferId;
    static VulkanImage spriteUploadTexture = new VulkanImage();
    static int[] spriteMipLevels=new int[256], spriteWidths=new int[256], spriteHeights=new int[256],
        spriteXOffsets=new int[256], spriteYOffsets=new int[256], spriteBufferOffsets=new int[256],
        spriteBufferRowLengths=new int[256], spriteBufferImageHeights=new int[256],
        spriteSourceOrder=new int[256], spriteGroupEnds=new int[256];
    static long[] spriteSourceBuffers=new long[256], spriteGroupSources=new long[256];
    static List<String> events=new ArrayList<>();
    static Map<String,Integer> pixels=new HashMap<>();
    static class TextureTickAttribution {
        enum CopyFlushReason { STAGING_BUFFER_SWITCH, REGION_LIMIT, OVERLAPPING_REGION, GPU_INTERPOLATION, TEXTURE_SWITCH }
        static void recordCopyCommands(int groups) {}
    }
    static void flushSpriteUploadCopies(TextureTickAttribution.CopyFlushReason reason) {
        events.add(reason.name()); flushSpriteUploadCopies();
    }
    static void transferWriteToWriteBarrier() { barriers++; events.add("barrier"); }
    static class MemoryStack implements AutoCloseable {
        static MemoryStack stackPush() { return new MemoryStack(); }
        public void close() {}
    }
    static class VulkanImage {
        int width=64, height=64, mipLevels=4, formatSize=4;
        long getId() { return 123; }
        void transferDstLayout(Object command) {}
    }
    static class TextureResidentBuffer {
        long getId() { return 1000; } int getBufferSize() { return 64; }
    }
    static class GraphicsQueue {
        boolean hasActiveUploadBatch() { return true; }
        GraphicsQueue getCommandBuffer() { return this; }
        Object getHandle() { return this; }
    }
    static class Device { static GraphicsQueue getGraphicsQueue() { return new GraphicsQueue(); } }
    static class VkBufferImageCopy {
        long offset; int row, height, mip, x, y, w, h;
        void bufferOffset(long v) { offset=v; }
        void bufferRowLength(int v) { row=v; }
        void bufferImageHeight(int v) { height=v; }
        VkBufferImageCopy imageSubresource() { return this; }
        void aspectMask(int v) {} void mipLevel(int v) { mip=v; }
        void baseArrayLayer(int v) {} void layerCount(int v) {}
        Vec imageOffset() { return (a,b,c)->{ x=a; y=b; }; }
        Vec imageExtent() { return (a,b,c)->{ w=a; h=b; }; }
        interface Vec { void set(int a,int b,int c); }
        static Buffer calloc(int count, MemoryStack stack) { return new Buffer(count); }
        static class Buffer {
            VkBufferImageCopy[] data; int start, end;
            Buffer(int count) { data=new VkBufferImageCopy[count]; end=count;
                for(int i=0;i<count;i++) data[i]=new VkBufferImageCopy(); }
            VkBufferImageCopy get(int i) { return data[i]; }
            void limit(int v) { end=v; } void position(int v) { start=v; }
        }
    }
    static void vkCmdCopyBufferToImage(Object cmd,long source,long image,int layout,VkBufferImageCopy.Buffer regions) {
        events.add("copy:"+source+":"+(regions.end-regions.start));
        for(int i=regions.start;i<regions.end;i++) {
            var r=regions.get(i);
            if(source != 1000) check(r.offset==r.y*1000+r.x*4L, "offset retained after grouping");
            if(source != 1000) check(r.row==r.x+11 && r.height==r.y+22, "source strides retained");
            pixels.put(r.mip+":"+r.x+":"+r.y, (int)source);
        }
    }
    __PRODUCTION__
    static void add(long source,int mip,int x,int y) {
        queueSpriteUploadCopy(mip,1,1,x,y,source,y*1000+x*4,x+11,y+22);
    }
    static void clear() {
        spriteUploadRegionCount=0; spriteUploadStagingBufferId=0; barriers=0;
        events.clear(); pixels.clear();
    }
    static void scheduling() {
        clear();
        for(int i=0;i<128;i++) add(10+i%2,0,i,0);
        check(events.isEmpty() && spriteUploadRegionCount==128, "source alternation must not drain");
        flushSpriteUploadCopies();
        check(events.equals(List.of("copy:10:64","copy:11:64","barrier")), "two commands, one dependency");
        for(int i=0;i<128;i++) check(pixels.get("0:"+i+":0")==10+i%2,"source identity");
        // Overlap across different source groups must drain all predecessors.
        clear(); add(10,0,0,0); add(11,0,1,0); add(11,0,0,0);
        check(events.equals(List.of("OVERLAPPING_REGION","copy:10:1","copy:11:1","barrier")),"cross-source overlap");
        flushSpriteUploadCopies(); check(pixels.get("0:0:0")==11,"last writer wins");
        clear(); add(10,0,0,0); add(11,1,0,0);
        check(events.isEmpty(),"different mip is independent"); flushSpriteUploadCopies();
        check(pixels.size()==2 && barriers==1,"both mips emitted");
        clear(); for(int i=0;i<257;i++) add(10+i%2,0,i,0);
        check(spriteUploadRegionCount==1 && events.get(0).equals("REGION_LIMIT"),"256-region bounded capacity");
        add(11,0,0,0); flushSpriteUploadCopies();
        check(pixels.get("0:0:0")==11 && barriers==2,"overwrite earlier capacity epoch");
        clear(); UNIFIED_GPU_UPLOADS=false; add(10,0,0,0); add(11,0,1,0);
        check(events.equals(List.of("STAGING_BUFFER_SWITCH","copy:10:1")),"legacy source boundary");
        flushSpriteUploadCopies(); check(barriers==0,"legacy barrier policy unchanged");
        UNIFIED_GPU_UPLOADS=true;
        // Random sequential-vs-grouped oracle, repeated overlaps and mip aliases.
        Random random=new Random(1981);
        for(int trial=0;trial<100;trial++) {
            clear(); Map<String,Integer> expected=new HashMap<>();
            for(int i=0;i<1000;i++) {
                int src=1+random.nextInt(7), mip=random.nextInt(4), x=random.nextInt(64), y=random.nextInt(4);
                add(src,mip,x,y); expected.put(mip+":"+x+":"+y,src);
            }
            flushSpriteUploadCopies(); check(pixels.equals(expected),"sequential oracle trial "+trial);
        }
        // Worst case: one command per distinct source, still bounded and correct.
        clear(); for(int i=0;i<256;i++) add(i+1,0,i,0); flushSpriteUploadCopies();
        check(events.size()==257 && pixels.size()==256 && barriers==1,"all distinct sources");
        clear(); flushSpriteUploadCopies(); check(events.isEmpty(),"empty drain");
        System.out.println("Production texture queue: alternating sources 128->2 commands, 128->1 barriers; 100000 randomized writes match sequential oracle");
    }

    private static void check(boolean value, String label) {
        if(!value) throw new AssertionError(label);
    }
    public static void main(String[] args) {
        scheduling();
        clear();
        var scratch = new TextureResidentBuffer();
        check(queueComputedSpriteFrame(scratch, spriteUploadTexture, new int[]{0,24}, 3,2,2,8,4)==2, "computed mips queued");
        check(spriteUploadRegionCount==2 && spriteSourceBuffers[0]==1000
                && spriteBufferOffsets[1]==24 && spriteXOffsets[1]==4
                && spriteYOffsets[1]==2 && spriteWidths[1]==1, "compute output geometry retained");
        flushPendingSpriteUploadSource(999); check(events.isEmpty(), "unrelated source does not drain");
        flushPendingSpriteUploadSource(1000);
        check(spriteUploadRegionCount==0 && events.get(0).equals("GPU_INTERPOLATION"), "pending scratch reader drains before reuse");
        clear();
        check(queueComputedSpriteFrame(scratch, spriteUploadTexture, new int[]{0,64},3,2,2,8,4)==-1
                && spriteUploadRegionCount==0 && events.isEmpty(), "all scratch ranges validated before enqueue");
        check(queueComputedSpriteFrame(scratch, spriteUploadTexture, new int[]{0},3,2,1,63,4)==-1
                && events.isEmpty(), "atlas extent validated before enqueue");
        clear();
        check(queueComputedSpriteFrame(scratch, spriteUploadTexture,
                new int[]{0,24}, 3,2,2,8,4,8)==2, "offset scratch mips queued");
        check(spriteBufferOffsets[0]==8 && spriteBufferOffsets[1]==32,
                "shared scratch offsets encoded exactly");
        flushPendingSpriteUploadRange(1000,40,4);
        check(spriteUploadRegionCount==2 && events.isEmpty(),
                "disjoint shared scratch slot must not drain pending reads");
        flushPendingSpriteUploadRange(1000,30,4);
        check(spriteUploadRegionCount==0 && events.get(0).equals("GPU_INTERPOLATION"),
                "overlapping shared scratch read must drain before rewrite");
        int[] mips = {0, 1, 0, 0, 0};
        int[] xs = {100, 50, 300, Integer.MAX_VALUE - 1, 0};
        int[] ys = {100, 50, 300, 0, Integer.MAX_VALUE - 1};
        int[] widths = {16, 8, 16, 2, 2};
        int[] heights = {16, 8, 16, 2, 2};
        check(!TextureTransferBatchPolicy.overlapsAny(mips,xs,ys,widths,heights,3,
                0,116,100,16,16),"touching right edge");
        check(!TextureTransferBatchPolicy.overlapsAny(mips,xs,ys,widths,heights,3,
                1,100,100,16,16),"different mip");
        check(TextureTransferBatchPolicy.overlapsAny(mips,xs,ys,widths,heights,3,
                0,115,115,16,16),"single texel overlap");
        check(!TextureTransferBatchPolicy.overlapsAny(mips,xs,ys,widths,heights,3,
                0,120,120,8,8),"diagonal disjoint");
        check(TextureTransferBatchPolicy.overlapsAny(mips,xs,ys,widths,heights,5,
                0,Integer.MAX_VALUE,0,1,1),"overflow safe X");
        check(TextureTransferBatchPolicy.overlapsAny(mips,xs,ys,widths,heights,5,
                0,0,Integer.MAX_VALUE,1,1),"overflow safe Y");
        check(!TextureTransferBatchPolicy.overlapsAny(mips,xs,ys,widths,heights,0,
                0,100,100,16,16),"empty queue");
        try {
            TextureTransferBatchPolicy.overlapsAny(mips,xs,ys,widths,heights,6,
                    0,100,100,1,1);
            throw new AssertionError("bad count accepted");
        } catch(IllegalArgumentException expected) {}
        System.out.println("Unified texture transfer region geometry passed");
    }
}
'''.replace('__PRODUCTION__', production)
with tempfile.TemporaryDirectory(prefix="vulkanmod-texture-batch-") as directory:
    target = Path(directory) / pkg
    target.mkdir(parents=True)
    (target / "TextureTransferBatchPolicy.java").write_text(
        (root / "src/main/java" / pkg / "TextureTransferBatchPolicy.java").read_text())
    (target / "TextureTransferBatchContract.java").write_text(harness)
    java_home = os.environ.get("JAVA_HOME")
    javac = str(Path(java_home)/"bin/javac") if java_home else "javac"
    java = str(Path(java_home)/"bin/java") if java_home else "java"
    subprocess.run([javac, "--release", "17", "-d", directory,
                    str(target/"TextureTransferBatchPolicy.java"),
                    str(target/"TextureTransferBatchContract.java")], check=True, timeout=30)
    subprocess.run([java,"-cp",directory,
                    "net.vulkanmod.vulkan.texture.TextureTransferBatchContract"],check=True,timeout=30)
