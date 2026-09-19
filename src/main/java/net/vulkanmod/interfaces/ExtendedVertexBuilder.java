package net.vulkanmod.interfaces;

import com.mojang.blaze3d.vertex.VertexConsumer;

public interface ExtendedVertexBuilder {

    void vertex(float x, float y, float z, int packedColor, float u, float v, int overlay, int light, int packedNormal);

    static void emit(VertexConsumer consumer, float x, float y, float z, int packedColor,
                     float u, float v, int overlay, int light, int packedNormal) {
        if (consumer instanceof ExtendedVertexBuilder extended) {
            extended.vertex(x, y, z, packedColor, u, v, overlay, light, packedNormal);
        } else {
            emitFallback(consumer, x, y, z, packedColor, u, v, overlay, light, packedNormal);
        }
    }

    static void emitFallback(VertexConsumer consumer, float x, float y, float z, int packedColor,
                             float u, float v, int overlay, int light, int packedNormal) {
        float red = (packedColor & 0xFF) / 255.0F;
        float green = ((packedColor >>> 8) & 0xFF) / 255.0F;
        float blue = ((packedColor >>> 16) & 0xFF) / 255.0F;
        float alpha = ((packedColor >>> 24) & 0xFF) / 255.0F;
        float normalX = (byte)(packedNormal & 0xFF) / 127.0F;
        float normalY = (byte)((packedNormal >>> 8) & 0xFF) / 127.0F;
        float normalZ = (byte)((packedNormal >>> 16) & 0xFF) / 127.0F;

        consumer.vertex(x, y, z, red, green, blue, alpha, u, v, overlay, light, normalX, normalY, normalZ);
    }
}
