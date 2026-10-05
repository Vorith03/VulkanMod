package net.vulkanmod.render.texture;

/**
 * Diagnostic numeric reference for the pinned Forge ticker. Never selects a
 * production upload path. Mip pixels are supplied by the actual loaded source,
 * rather than regenerated or inferred from level zero.
 */
public final class SpriteAnimationOracle {
    public record Frame(int index, int duration) {
        public Frame {
            if(index < 0 || duration <= 0) throw new IllegalArgumentException("Unresolved animation frame");
        }
    }
    public enum Update { NONE, FRAME, INTERPOLATE }
    private final Frame[] frames;
    private final boolean interpolate;
    private int frame, subFrame;

    public SpriteAnimationOracle(Frame[] resolvedFrames, boolean interpolate) {
        if(resolvedFrames.length < 2) throw new IllegalArgumentException("Not an animated schedule");
        this.frames = resolvedFrames.clone();
        for(Frame value : frames) if(value == null) throw new IllegalArgumentException("Missing frame");
        this.interpolate = interpolate;
    }

    public Update tick() {
        Frame current = frames[frame];
        if(++subFrame >= current.duration()) {
            int old = current.index();
            frame = (frame + 1) % frames.length;
            subFrame = 0;
            return old == frames[frame].index() ? Update.NONE : Update.FRAME;
        }
        return interpolate && current.index() != next().index() ? Update.INTERPOLATE : Update.NONE;
    }

    public long clock() { return ((long)frame << 32) | (subFrame & 0xffffffffL); }
    public int currentIndex() { return frames[frame].index(); }
    private Frame next() { return frames[(frame + 1) % frames.length]; }

    /** ABGR NativeImage integer: only RGB is mixed; alpha belongs to the current frame. */
    public static int mix(int current, int next, int subFrame, int duration) {
        if(duration <= 0 || subFrame < 0 || subFrame >= duration)
            throw new IllegalArgumentException("Invalid interpolation clock");
        double weight = 1.0D - (double)subFrame / duration;
        int result = current & 0xff000000;
        for(int shift = 0; shift < 24; shift += 8) {
            int a = (current >>> shift) & 255, b = (next >>> shift) & 255;
            int channel = (int)(weight * a + (1.0D - weight) * b);
            result |= channel << shift;
        }
        return result;
    }

    /** Pixels for an upload/first-use refresh, with base-sheet offsets shifted per mip. */
    public int pixel(int[] sheet, int sheetWidth, int columns, int frameWidth,
                     int frameHeight, int mip, int x, int y) {
        int current = sample(sheet, sheetWidth, columns, frameWidth, frameHeight, mip,
                currentIndex(), x, y);
        if(!interpolate || subFrame == 0 || currentIndex() == next().index()) return current;
        int following = sample(sheet, sheetWidth, columns, frameWidth, frameHeight, mip,
                next().index(), x, y);
        return mix(current, following, subFrame, frames[frame].duration());
    }

    private static int sample(int[] sheet, int sheetWidth, int columns, int frameWidth,
                              int frameHeight, int mip, int index, int x, int y) {
        int sourceX = ((index % columns * frameWidth) >> mip) + x;
        int sourceY = ((index / columns * frameHeight) >> mip) + y;
        return sheet[sourceY * sheetWidth + sourceX];
    }
}
