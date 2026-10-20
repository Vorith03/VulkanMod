package net.vulkanmod.render.util;
import java.util.Arrays;
import java.util.Random;
/** Historical differential and independent stable boxed-sort oracle. */
public final class StableQuadSortTest {
    public static void main(String[] args) {
        verify(new float[0]); verify(new float[]{1});
        verify(new float[]{Float.NaN, 0f, -0f, Float.POSITIVE_INFINITY,
                Float.NEGATIVE_INFINITY, Float.intBitsToFloat(0x7fc00001), 1, 1});
        float[] equal = new float[2048]; Arrays.fill(equal, 42); verify(equal);
        Random random = new Random(0x51ab1e);
        for (int n = 0; n < 500; n++) {
            float[] values = new float[random.nextInt(2049)];
            for (int i = 0; i < values.length; i++)
                values[i] = n % 2 == 0 ? random.nextInt(12) : Float.intBitsToFloat(random.nextInt());
            verify(values);
        }
        System.out.println("Stable quad sort differential/independent checks passed");
    }
    private static void verify(float[] distances) {
        int[] actual = new int[distances.length]; Integer[] expected = new Integer[distances.length];
        for (int i = 0; i < actual.length; i++) actual[i] = expected[i] = actual.length - i - 1;
        int[] legacy = actual.clone(); LegacySortOracle.mergeSort(legacy, distances);
        Arrays.sort(expected, (a,b) -> Float.compare(distances[b], distances[a]));
        SortUtil.mergeSort(actual, distances);
        if (!Arrays.equals(actual, legacy)) throw new AssertionError("Historical order changed");
        for (int i = 0; i < actual.length; i++)
            if (actual[i] != expected[i]) throw new AssertionError("Independent order mismatch");
    }
}
