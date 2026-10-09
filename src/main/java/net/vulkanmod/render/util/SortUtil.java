package net.vulkanmod.render.util;


public class SortUtil {

    public static void mergeSort(int[] a, float[] distances) {
        mergeSort(a, distances, 0, a.length, null);
    }

    public static void mergeSort(int[] indices, float[] distances, int from, int to, int[] supp) {
        int len = to - from;
        if (len < 16) {
            insertionSort(indices, distances, from, to);
        } else {
            if (supp == null) {
                supp = java.util.Arrays.copyOf(indices, to);
            }

            int mid = from + to >>> 1;
            mergeSort(supp, distances, from, mid, indices);
            mergeSort(supp, distances, mid, to, indices);

            if (Float.compare(distances[supp[mid]], distances[supp[mid - 1]]) <= 0) {
                System.arraycopy(supp, from, indices, from, len);
            }
            else {
                int i = from;
                int p = from;

                for(int q = mid; i < to; ++i) {
                    if (q < to && (p >= mid || Float.compare(distances[supp[q]],  distances[supp[p]]) > 0)) {
                        indices[i] = supp[q++];
                    } else {
                        indices[i] = supp[p++];
                    }
                }

            }
        }
    }

    private static void insertionSort(int[] is, float[] distances, int from, int to) {
        int i = from;

        while(true) {
            ++i;
            if (i >= to) {
                return;
            }

            int t = is[i];
            int j = i;

            for(int u = is[i - 1]; Float.compare(distances[u], distances[t]) < 0; u = is[j - 1]) {
                is[j] = u;
                if (from == j - 1) {
                    --j;
                    break;
                }

                --j;
            }

            is[j] = t;
        }
    }

}
