package net.vulkanmod.render.texture;

import com.mojang.blaze3d.platform.NativeImage;
import net.vulkanmod.interfaces.VNativeImageI;
import org.lwjgl.system.MemoryUtil;

/** Exact, allocation-free CPU fallback. Validates every mip before writing any pixels. */
public final class CpuSpriteInterpolation {
    private static final boolean REQUESTED = Boolean.getBoolean("vulkanmod.experimentalCpuSpriteInterpolation");
    private static boolean ciEnabled, ciForceVanilla;
    private static long ciFrames;

    private CpuSpriteInterpolation() {}

    public static boolean enabled() { return (REQUESTED || ciEnabled) && !ciForceVanilla; }

    static void setCiEnabled(boolean enabled) {
        if(!Boolean.getBoolean("vulkanmod.ciScreenshotSmoke"))
            throw new IllegalStateException("Bulk interpolation override is CI-only");
        ciEnabled = enabled;
    }
    static void setCiForceVanilla(boolean force) {
        if(!Boolean.getBoolean("vulkanmod.ciScreenshotSmoke"))
            throw new IllegalStateException("Vanilla interpolation override is CI-only");
        ciForceVanilla = force;
    }
    static long ciFrames() { return ciFrames; }

    public static boolean tryInterpolate(NativeImage[] source, NativeImage[] output,
                                         int frameWidth, int frameHeight, int columns,
                                         int current, int next, int subFrame, int duration) {
        if(source == null || output == null || output.length == 0 || output.length > 32
                || source.length > 32 || source.length < output.length || frameWidth <= 0 || frameHeight <= 0
                || columns <= 0 || current < 0 || next < 0 || current == next
                || duration <= 0 || subFrame <= 0 || subFrame >= duration)
            return false;
        long currentX = (long)(current % columns) * frameWidth;
        long currentY = (long)(current / columns) * frameHeight;
        long nextX = (long)(next % columns) * frameWidth;
        long nextY = (long)(next / columns) * frameHeight;
        // The original coordinate expressions use int multiplication.
        if(currentX > Integer.MAX_VALUE || currentY > Integer.MAX_VALUE
                || nextX > Integer.MAX_VALUE || nextY > Integer.MAX_VALUE) return false;

        int levels = 0;
        for(; levels < output.length; levels++) {
            int w = frameWidth >> levels, h = frameHeight >> levels;
            if(w == 0 || h == 0) break; // Forge's positive-extent guard.
            NativeImage src = source[levels], dst = output[levels];
            if(src == null || dst == null) return false;
            long srcPointer = pixels(src), dstPointer = pixels(dst);
            if(srcPointer == 0L || dstPointer == 0L || w > dst.getWidth() || h > dst.getHeight()
                    || (currentX >> levels) + w > src.getWidth()
                    || (nextX >> levels) + w > src.getWidth()
                    || (currentY >> levels) + h > src.getHeight()
                    || (nextY >> levels) + h > src.getHeight()) return false;
            // No output may alias any source/output, including wrappers over the
            // same native memory. Reject before the first mip is modified.
            long dstBytes = (long)dst.getWidth() * dst.getHeight() * 4L;
            for(NativeImage image : source) {
                if(image != null && overlaps(dstPointer, dstBytes, pixels(image),
                        (long)image.getWidth() * image.getHeight() * 4L)) return false;
            }
            for(int i = 0; i < levels; i++) {
                NativeImage image = output[i];
                if(overlaps(dstPointer, dstBytes, pixels(image),
                        (long)image.getWidth() * image.getHeight() * 4L)) return false;
            }
        }
        if(levels == 0) return false;
        double weight = 1.0D - (double)subFrame / (double)duration;
        double inverse = 1.0D - weight;
        for(int mip = 0; mip < levels; mip++) {
            NativeImage src = source[mip], dst = output[mip];
            int w = frameWidth >> mip, h = frameHeight >> mip;
            long stride = (long)src.getWidth() * 4L;
            long a = pixels(src) + (currentY >> mip) * stride + (currentX >> mip) * 4L;
            long b = pixels(src) + (nextY >> mip) * stride + (nextX >> mip) * 4L;
            long out = pixels(dst), outStride = (long)dst.getWidth() * 4L;
            // Retain mutation invalidation without a NativeImage setter hook per pixel.
            ((VNativeImageI)(Object)dst).vulkanmod$markPixelsChanged();
            for(int y = 0; y < h; y++) {
                long rowA = a + y * stride, rowB = b + y * stride, rowOut = out + y * outStride;
                for(int x = 0; x < w; x++) {
                    int first = MemoryUtil.memGetInt(rowA + x * 4L);
                    int second = MemoryUtil.memGetInt(rowB + x * 4L);
                    int red = (int)(weight * (first & 255) + inverse * (second & 255));
                    int green = (int)(weight * ((first >>> 8) & 255) + inverse * ((second >>> 8) & 255));
                    int blue = (int)(weight * ((first >>> 16) & 255) + inverse * ((second >>> 16) & 255));
                    MemoryUtil.memPutInt(rowOut + x * 4L, (first & 0xff000000) | red | green << 8 | blue << 16);
                }
            }
        }
        if(ciEnabled) ciFrames++;
        return true;
    }

    private static long pixels(NativeImage image) {
        return ((VNativeImageI)(Object)image).vulkanmod$getRgbaPixels();
    }
    private static boolean overlaps(long a, long bytesA, long b, long bytesB) {
        return a != 0L && b != 0L && a < b + bytesB && b < a + bytesA;
    }
}
