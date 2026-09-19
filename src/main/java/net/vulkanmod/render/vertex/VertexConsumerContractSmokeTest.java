package net.vulkanmod.render.vertex;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexMultiConsumer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraftforge.client.model.IQuadTransformer;
import net.vulkanmod.Initializer;
import net.vulkanmod.interfaces.ExtendedVertexBuilder;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Opt-in startup regression probe for the Forge/public VertexConsumer contracts
 * that VulkanMod's packed vertex acceleration must preserve.
 */
public final class VertexConsumerContractSmokeTest {
    private static final float EPSILON = 1.0e-6F;
    private static final byte SENTINEL = (byte)0x5A;

    private VertexConsumerContractSmokeTest() {
    }

    public static void verify() {
        try {
            verifyMultiConsumerFallbackAndLayout();
            verifyForgeBulkData();
            Initializer.LOGGER.info("Vertex/Forge consumer contract smoke passed");
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Vertex/Forge consumer contract smoke failed", e);
        }
    }

    private static void verifyMultiConsumerFallbackAndLayout() throws ReflectiveOperationException {
        RecordingConsumer primary = recordingConsumer();
        BufferBuilder secondary = new BufferBuilder(128);
        secondary.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);

        int stride = DefaultVertexFormat.POSITION_TEX.getVertexSize();
        require(stride == 20, "POSITION_TEX stride changed; update the exact-layout oracle");

        Field bufferField = BufferBuilder.class.getDeclaredField("buffer");
        bufferField.setAccessible(true);
        ByteBuffer bytes = (ByteBuffer)bufferField.get(secondary);
        for (int i = 0; i < stride + 16; ++i) {
            bytes.put(i, SENTINEL);
        }

        int packedColor = packColorBytes(0x10, 0x20, 0x40, 0x80);
        int packedNormal = packNormalBytes(32, -64, 127);
        float x = 1.25F;
        float y = -2.5F;
        float z = 3.75F;
        float u = 0.125F;
        float v = 0.875F;
        int overlay = 0x00110022;
        int light = 0x00330044;

        VertexConsumer multi = VertexMultiConsumer.create(primary.consumer, secondary);
        require(multi instanceof ExtendedVertexBuilder,
                "VertexMultiConsumer.Double did not receive VulkanMod's packed optional fast path");
        ((ExtendedVertexBuilder)multi).vertex(x, y, z, packedColor, u, v, overlay, light, packedNormal);

        require(primary.records.size() == 1, "Ordinary custom consumer did not receive exactly one vertex");
        VertexRecord record = primary.records.get(0);
        requireClose(record.x, x, "fallback x");
        requireClose(record.y, y, "fallback y");
        requireClose(record.z, z, "fallback z");
        requireClose(record.red, 0x10 / 255.0F, "fallback red");
        requireClose(record.green, 0x20 / 255.0F, "fallback green");
        requireClose(record.blue, 0x40 / 255.0F, "fallback blue");
        requireClose(record.alpha, 0x80 / 255.0F, "fallback alpha");
        requireClose(record.u, u, "fallback u");
        requireClose(record.v, v, "fallback v");
        require(record.overlay == overlay, "fallback overlay");
        require(record.light == light, "fallback light");
        requireClose(record.normalX, 32 / 127.0F, "fallback normal x");
        requireClose(record.normalY, -64 / 127.0F, "fallback normal y");
        requireClose(record.normalZ, 1.0F, "fallback normal z");

        Field nextElementByteField = BufferBuilder.class.getDeclaredField("nextElementByte");
        nextElementByteField.setAccessible(true);
        require(nextElementByteField.getInt(secondary) == stride,
                "Non-NEW_ENTITY consumer advanced by the wrong stride");

        ByteBuffer expected = ByteBuffer.allocate(stride).order(bytes.order());
        expected.putFloat(x).putFloat(y).putFloat(z).putFloat(u).putFloat(v);
        for (int i = 0; i < stride; ++i) {
            require(bytes.get(i) == expected.get(i),
                    "POSITION_TEX byte mismatch at offset " + i);
        }
        for (int i = stride; i < stride + 16; ++i) {
            require(bytes.get(i) == SENTINEL,
                    "Packed fallback wrote beyond POSITION_TEX stride at offset " + i);
        }
    }

    private static void verifyForgeBulkData() {
        int[] vertexData = new int[IQuadTransformer.STRIDE * 4];
        int[] bakedColors = new int[4];
        int[] bakedLights = new int[4];
        int[] inputLights = new int[4];
        int[] normalX = {64, 32, -64, -32};
        int[] normalY = {-32, 64, 32, -64};
        int[] normalZ = {127, 96, 80, 48};
        float[] brightness = {0.25F, 0.5F, 0.75F, 1.0F};

        for (int vertex = 0; vertex < 4; ++vertex) {
            int base = vertex * IQuadTransformer.STRIDE;
            vertexData[base + IQuadTransformer.POSITION] = Float.floatToRawIntBits(1.0F + vertex);
            vertexData[base + IQuadTransformer.POSITION + 1] = Float.floatToRawIntBits(2.0F + vertex);
            vertexData[base + IQuadTransformer.POSITION + 2] = Float.floatToRawIntBits(3.0F + vertex);

            bakedColors[vertex] = packColorBytes(40 + vertex * 10, 80 + vertex * 10,
                    120 + vertex * 10, 64 + vertex * 32);
            vertexData[base + IQuadTransformer.COLOR] = bakedColors[vertex];
            vertexData[base + IQuadTransformer.UV0] = Float.floatToRawIntBits(0.1F + vertex * 0.1F);
            vertexData[base + IQuadTransformer.UV0 + 1] = Float.floatToRawIntBits(0.2F + vertex * 0.1F);

            bakedLights[vertex] = packLight(0x40 + vertex, 0x10 + vertex);
            inputLights[vertex] = packLight(0x20 + vertex, 0x30 + vertex);
            vertexData[base + IQuadTransformer.UV2] = bakedLights[vertex];
            vertexData[base + IQuadTransformer.NORMAL] =
                    packNormalBytes(normalX[vertex], normalY[vertex], normalZ[vertex]);
        }

        BakedQuad quad = new BakedQuad(vertexData, -1, Direction.UP, null, true);
        PoseStack poseStack = new PoseStack();
        RecordingConsumer recording = recordingConsumer();
        float red = 0.8F;
        float green = 0.6F;
        float blue = 0.4F;
        int overlay = 0x00550066;

        recording.consumer.putBulkData(poseStack.last(), quad, brightness, red, green, blue,
                inputLights, overlay, true);
        assertForgeQuad(recording.records, bakedColors, bakedLights, inputLights, normalX, normalY,
                normalZ, brightness, red, green, blue, 1.0F, overlay);

        recording.records.clear();
        float callerAlpha = 0.5F;
        recording.consumer.putBulkData(poseStack.last(), quad, brightness, red, green, blue, callerAlpha,
                inputLights, overlay, true);
        assertForgeQuad(recording.records, bakedColors, bakedLights, inputLights, normalX, normalY,
                normalZ, brightness, red, green, blue, callerAlpha, overlay);
    }

    private static void assertForgeQuad(List<VertexRecord> records, int[] bakedColors, int[] bakedLights,
                                        int[] inputLights, int[] normalX, int[] normalY, int[] normalZ,
                                        float[] brightness, float red, float green, float blue,
                                        float callerAlpha, int overlay) {
        require(records.size() == 4, "Forge putBulkData did not emit four vertices");

        for (int vertex = 0; vertex < 4; ++vertex) {
            VertexRecord record = records.get(vertex);
            int color = bakedColors[vertex];
            float bakedRed = (color & 0xFF) / 255.0F;
            float bakedGreen = ((color >>> 8) & 0xFF) / 255.0F;
            float bakedBlue = ((color >>> 16) & 0xFF) / 255.0F;
            float bakedAlpha = ((color >>> 24) & 0xFF) / 255.0F;

            requireClose(record.red, bakedRed * brightness[vertex] * red, "Forge baked red");
            requireClose(record.green, bakedGreen * brightness[vertex] * green, "Forge baked green");
            requireClose(record.blue, bakedBlue * brightness[vertex] * blue, "Forge baked blue");
            requireClose(record.alpha, bakedAlpha * callerAlpha, "Forge per-vertex alpha");
            require(record.overlay == overlay, "Forge overlay");

            int expectedBlock = Math.max(inputLights[vertex] & 0xFFFF, bakedLights[vertex] & 0xFFFF);
            int expectedSky = Math.max((inputLights[vertex] >>> 16) & 0xFFFF,
                    (bakedLights[vertex] >>> 16) & 0xFFFF);
            require(record.light == packLight(expectedBlock, expectedSky), "Forge baked lighting");
            requireClose(record.normalX, normalX[vertex] / 127.0F, "Forge baked normal x");
            requireClose(record.normalY, normalY[vertex] / 127.0F, "Forge baked normal y");
            requireClose(record.normalZ, normalZ[vertex] / 127.0F, "Forge baked normal z");
        }
    }

    private static RecordingConsumer recordingConsumer() {
        List<VertexRecord> records = new ArrayList<>();
        VertexConsumer consumer = (VertexConsumer)Proxy.newProxyInstance(
                VertexConsumer.class.getClassLoader(),
                new Class<?>[]{VertexConsumer.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("vertex") && arguments != null && arguments.length == 14
                            && method.getReturnType() == void.class) {
                        records.add(new VertexRecord(
                                (Float)arguments[0], (Float)arguments[1], (Float)arguments[2],
                                (Float)arguments[3], (Float)arguments[4], (Float)arguments[5], (Float)arguments[6],
                                (Float)arguments[7], (Float)arguments[8], (Integer)arguments[9], (Integer)arguments[10],
                                (Float)arguments[11], (Float)arguments[12], (Float)arguments[13]));
                        return null;
                    }

                    if (method.isDefault()) {
                        return InvocationHandler.invokeDefault(proxy, method, arguments);
                    }

                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "toString" -> "VertexConsumerContractSmokeProxy";
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == arguments[0];
                            default -> throw new AssertionError("Unexpected Object method " + method);
                        };
                    }

                    throw new AssertionError("Fallback used chained/unsupported VertexConsumer call: " + method);
                });
        require(!(consumer instanceof ExtendedVertexBuilder),
                "Regression fixture must remain a valid non-VulkanMod VertexConsumer");
        return new RecordingConsumer(consumer, records);
    }

    private static int packColorBytes(int red, int green, int blue, int alpha) {
        return (red & 0xFF) | ((green & 0xFF) << 8) | ((blue & 0xFF) << 16) | ((alpha & 0xFF) << 24);
    }

    private static int packNormalBytes(int x, int y, int z) {
        return (x & 0xFF) | ((y & 0xFF) << 8) | ((z & 0xFF) << 16);
    }

    private static int packLight(int block, int sky) {
        return (block & 0xFFFF) | ((sky & 0xFFFF) << 16);
    }

    private static void requireClose(float actual, float expected, String message) {
        require(Math.abs(actual - expected) <= EPSILON,
                message + " expected " + expected + " but got " + actual);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private record RecordingConsumer(VertexConsumer consumer, List<VertexRecord> records) {
    }

    private record VertexRecord(float x, float y, float z, float red, float green, float blue, float alpha,
                                float u, float v, int overlay, int light,
                                float normalX, float normalY, float normalZ) {
    }
}
