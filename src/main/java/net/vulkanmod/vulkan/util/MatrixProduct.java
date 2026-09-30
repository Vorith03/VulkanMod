package net.vulkanmod.vulkan.util;

import org.joml.Matrix4f;

import java.nio.ByteBuffer;

/** Reusable scratch for projection * model-view; inputs and buffer positions are preserved. */
public final class MatrixProduct {
    private final Matrix4f projection = new Matrix4f();
    private final Matrix4f modelView = new Matrix4f();

    public void write(ByteBuffer projectionBytes, ByteBuffer modelViewBytes, ByteBuffer output) {
        this.projection.set(projectionBytes);
        this.modelView.set(modelViewBytes);
        this.projection.mul(this.modelView).get(output);
    }
}
