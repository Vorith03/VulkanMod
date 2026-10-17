package net.vulkanmod.vulkan.texture;

/**
 * Half-open destination region intersection for Vulkan upload batching.
 *
 * This is a geometry policy, not a declaration that mod-generated source
 * pixels can be recreated by a GPU shader. Writes to different mip levels
 * have independent destinations, including when their XY values coincide.
 */
public final class TextureTransferBatchPolicy {
    private TextureTransferBatchPolicy() {}

    /**
     * Stable source grouping within one pairwise-disjoint destination epoch.
     * The caller supplies reusable scratch arrays, each at least count long.
     * Returns the number of groups; groupEnds contains exclusive order offsets.
     * This does not authorize reordering across overlap, layout or compute boundaries.
     */
    public static int groupSources(long[] buffers, int count, int[] order,
                                   long[] sources, int[] groupEnds) {
        if(count < 0 || count > buffers.length || count > order.length
                || count > sources.length || count > groupEnds.length)
            throw new IllegalArgumentException("Invalid texture source group capacity");
        int groups = 0;
        for(int i = 0; i < count; i++) {
            int group = 0;
            while(group < groups && sources[group] != buffers[i]) group++;
            if(group == groups) sources[groups++] = buffers[i];
        }
        int out = 0;
        for(int group = 0; group < groups; group++) {
            for(int i = 0; i < count; i++)
                if(buffers[i] == sources[group]) order[out++] = i;
            groupEnds[group] = out;
        }
        return groups;
    }

    public static boolean overlapsAny(int[] mips, int[] xs, int[] ys,
                                      int[] widths, int[] heights, int count,
                                      int mip, int x, int y, int width, int height) {
        if(count < 0 || count > mips.length || count > xs.length || count > ys.length
                || count > widths.length || count > heights.length)
            throw new IllegalArgumentException("Invalid pending texture region count");
        if(width <= 0 || height <= 0)
            throw new IllegalArgumentException("Invalid texture transfer extent");

        long right = (long)x + width;
        long bottom = (long)y + height;
        for(int i = 0; i < count; i++) {
            if(mips[i] != mip) continue;
            if(x < (long)xs[i] + widths[i] && xs[i] < right
                    && y < (long)ys[i] + heights[i] && ys[i] < bottom)
                return true;
        }
        return false;
    }
}
