package net.vulkanmod.render.chunk;

import net.vulkanmod.Initializer;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.memory.MemoryTypes;
import net.vulkanmod.vulkan.memory.StorageBuffer;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/** CI-only proof for the production upload-submission consumer ordering contract. */
public final class AreaUploadManagerPostSubmitSmokeTest {
    private static boolean verified;

    private AreaUploadManagerPostSubmitSmokeTest() {}

    public static synchronized void verifyInRecordingFrame() {
        if(verified)
            return;

        Renderer renderer = Renderer.getInstance();
        require(renderer != null && renderer.isRecordingFrame(),
                "Post-submit smoke requires a recording frame");
        AreaUploadManager manager = AreaUploadManager.INSTANCE;
        require(manager != null,
                "Post-submit smoke requires the terrain upload manager");

        StorageBuffer destination = new StorageBuffer(Integer.BYTES, MemoryTypes.GPU_MEM);
        ByteBuffer source = MemoryUtil.memAlloc(Integer.BYTES);
        int[] phase = {0};
        boolean submitted = false;
        try {
            source.putInt(0x564B4D44).flip();
            manager.uploadStorageAsync(destination, 0L, source, () -> {
                require(phase[0] == 0,
                        "Submitted-upload callback ran out of order");
                phase[0] = 1;
            });
            manager.enqueuePostSubmitOp(() -> {
                require(phase[0] == 1,
                        "Post-submit consumer ran before upload residency publication");
                require(!Thread.holdsLock(manager),
                        "Post-submit consumer must run outside AreaUploadManager monitor");
                phase[0] = 2;
            });

            manager.submitUploads();
            submitted = true;
            require(phase[0] == 2,
                    "Post-submit consumer did not run in the submitting frame");
            verified = true;
            Initializer.LOGGER.info(
                    "VULKANMOD_TERRAIN_POST_SUBMIT_OK: submitted-upload publication preceded the consumer and the consumer ran outside the AreaUploadManager monitor");
        } finally {
            MemoryUtil.memFree(source);
            // Once submitted, StorageBuffer.freeBuffer uses the normal deferred-free
            // path and the current frame fence owns completion of this helper copy.
            // On an assertion before submission, deliberately leak into the failing
            // smoke process rather than freeing a buffer still referenced by a recorded command.
            if(submitted)
                destination.freeBuffer();
        }
    }

    private static void require(boolean condition, String message) {
        if(!condition)
            throw new AssertionError(message);
    }
}
