package net.vulkanmod.interfaces;

import org.joml.Quaternionf;

/** Qualified numeric input; ordinary vertices retain their exact positions in the stream. */
public interface ParticleBillboardWriter {
    boolean vulkanmod$canWriteBillboard();
    void vulkanmod$billboard(float x, float y, float z, Quaternionf rotation, float size,
                            float u0, float u1, float v0, float v1, int color, int light);
}
