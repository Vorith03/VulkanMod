package net.vulkanmod.render.util;

import it.unimi.dsi.fastutil.ints.IntArrays;

/** Stable far-to-near quad order, including Float.compare NaN/signed-zero semantics. */
public final class SortUtil {
    private SortUtil() {}
    public static void mergeSort(int[] indices, float[] distances) {
        IntArrays.stableSort(indices, (left, right) -> Float.compare(distances[right], distances[left]));
    }
}
