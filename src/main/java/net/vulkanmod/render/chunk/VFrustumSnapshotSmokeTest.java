package net.vulkanmod.render.chunk;

import org.joml.Matrix4f;

/** CI-only guard for generation-bound frustum state used by GPU terrain selection. */
public final class VFrustumSnapshotSmokeTest {
    private VFrustumSnapshotSmokeTest() {}

    public static void verify() {
        VFrustum live = new VFrustum();
        live.setCamOffset(0.0D, 0.0D, 0.0D);
        live.calculateFrustum(new Matrix4f().identity(),
                new Matrix4f().ortho(-32.0F, 32.0F, -32.0F, 32.0F, -32.0F, 32.0F));

        VFrustum frozen = live.snapshot();
        int originalResult = frozen.cubeInFrustum(-8.0F, -8.0F, -8.0F,
                8.0F, 8.0F, 8.0F);
        float[] originalPlanes = new float[VFrustum.PLANE_COUNT * VFrustum.PLANE_WORDS];
        frozen.copyPlaneEquations(originalPlanes);

        // Simulate the camera/frustum advancing while the older candidate table is
        // still waiting for its asynchronous GPU upload to become resident.
        live.setCamOffset(256.0D, 64.0D, -128.0D);
        live.calculateFrustum(new Matrix4f().identity(),
                new Matrix4f().ortho(-4.0F, 4.0F, -6.0F, 6.0F, -8.0F, 8.0F));

        require(frozen.cubeInFrustum(-8.0F, -8.0F, -8.0F,
                        8.0F, 8.0F, 8.0F) == originalResult,
                "Frozen GPU-selection frustum changed after live camera mutation");
        require(frozen.relativeX(128) == 128.0F
                        && frozen.relativeY(64) == 64.0F
                        && frozen.relativeZ(-32) == -32.0F,
                "Frozen GPU-selection camera offsets changed after live mutation");

        float[] frozenPlanes = new float[VFrustum.PLANE_COUNT * VFrustum.PLANE_WORDS];
        frozen.copyPlaneEquations(frozenPlanes);
        for(int i = 0; i < originalPlanes.length; ++i) {
            require(Float.floatToRawIntBits(originalPlanes[i])
                            == Float.floatToRawIntBits(frozenPlanes[i]),
                    "Frozen GPU-selection plane changed at word " + i);
        }
    }

    private static void require(boolean condition, String message) {
        if(!condition)
            throw new AssertionError(message);
    }
}
