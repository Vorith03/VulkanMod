package net.vulkanmod.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.vulkanmod.Initializer;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;

/** Semantic regression probe, invoked only by the opt-in CI startup smoke test. */
public final class LiquidVertexSmokeTest {
    private LiquidVertexSmokeTest() {}

    public static void verify(Class<?> rendererClass) {
        try {
            Class<?>[] signature = {VertexConsumer.class, double.class, double.class, double.class,
                    float.class, float.class, float.class, float.class, float.class, float.class, int.class};
            Method vertex;
            try {
                vertex = rendererClass.getDeclaredMethod("vertex", signature);
            } catch (NoSuchMethodException developmentNameMissing) {
                vertex = rendererClass.getDeclaredMethod("m_110984_", signature);
            }
            vertex.setAccessible(true);
            Object renderer = rendererClass.getDeclaredConstructor().newInstance();
            // Distinct values are essential: an alpha/U/V permutation has the
            // same JVM descriptor and therefore passes Mixin application checks.
            for (float alpha : new float[]{0.375F, 1.0F}) {
                Object[] expected = {1.25F, 2.5F, -3.75F, 0.125F, 0.25F, 0.5F,
                        alpha, 0.625F, 0.875F, 0, 0x00D000B0, 0.0F, 1.0F, 0.0F};
                int[] calls = {0};
                VertexConsumer consumer = (VertexConsumer) Proxy.newProxyInstance(
                        VertexConsumer.class.getClassLoader(), new Class<?>[]{VertexConsumer.class},
                        (proxy, method, arguments) -> {
                            // The Vulkan injection emits one complete vertex and
                            // cancels vanilla's chained consumer calls.
                            if (!Arrays.equals(expected, arguments)) {
                                throw new AssertionError("Liquid vertex payload mismatch: "
                                        + method.getName() + " " + Arrays.toString(arguments));
                            }
                            calls[0]++;
                            return null;
                        });
                vertex.invoke(renderer, consumer, 1.25D, 2.5D, -3.75D,
                        0.125F, 0.25F, 0.5F, alpha, 0.625F, 0.875F, 0x00D000B0);
                if (calls[0] != 1) {
                    throw new AssertionError("Expected one liquid vertex, got " + calls[0]);
                }
            }
            Initializer.LOGGER.info("Liquid vertex alpha/UV smoke test passed");
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Liquid vertex semantic smoke test failed", e);
        }
    }
}
