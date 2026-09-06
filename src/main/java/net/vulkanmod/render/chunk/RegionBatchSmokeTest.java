package net.vulkanmod.render.chunk;

import net.minecraft.client.renderer.RenderType;
import net.vulkanmod.Initializer;
import net.vulkanmod.render.vertex.TerrainRenderType;
import org.joml.Vector3i;

/** Exercise the real cache and mapped command buffers during opt-in CI startup. */
public final class RegionBatchSmokeTest {
    private RegionBatchSmokeTest() {}

    public static void verify() {
        ChunkArea area = new ChunkArea(0, new Vector3i(-128, -128, 128));
        DrawBuffers buffers = area.drawBuffers;
        var first = new RegionDrawBatch.FrameBatch();
        var second = new RegionDrawBatch.FrameBatch();
        try {
            require(!TerrainShaderManager.useRegionBatching(RenderType.translucent()), "Water must retain its renderer");
            require(!TerrainShaderManager.useRegionBatching(RenderType.tripwire()), "Tripwire must retain its renderer");
            RenderSection section = new RenderSection(0, -16, -96, 176);
            var parameters = section.getDrawParameters(TerrainRenderType.CUTOUT_MIPPED);
            parameters.indexCount = 6;
            parameters.vertexOffset = 24;
            parameters.vertexBufferSegment.setPending();
            area.addSection(section);
            require(first.update(buffers, area, TerrainRenderType.CUTOUT_MIPPED), "Initial cache build");
            require(first.drawCount == 0 && first.pendingUploads, "Pending vertices must not draw");
            parameters.vertexBufferSegment.setReady();
            require(first.update(buffers, area, TerrainRenderType.CUTOUT_MIPPED), "Upload completion must retry");
            require(first.drawCount == 1 && !first.pendingUploads, "Ready geometry must draw");
            require(first.commands.getByteBuffer().getInt(16) == (7 | (2 << 3) | (3 << 6)), "GPU section coordinates");
            require(!first.update(buffers, area, TerrainRenderType.CUTOUT_MIPPED), "Unchanged cache must not upload");
            second.update(buffers, area, TerrainRenderType.CUTOUT_MIPPED);
            require(first.commands.getId() != second.commands.getId(), "Frames must own distinct buffers");
            parameters.indexCount = 12;
            buffers.meshRevision++;
            require(first.update(buffers, area, TerrainRenderType.CUTOUT_MIPPED), "Mesh edit invalidation");
            require(first.commands.getByteBuffer().getInt(0) == 12, "Edited count");
            require(second.commands.getByteBuffer().getInt(0) == 6, "Other frame must remain untouched");
            area.resetQueue();
            require(first.update(buffers, area, TerrainRenderType.CUTOUT_MIPPED) && first.drawCount == 0,
                    "Visibility removal must clear draws");
            area.addSection(section);
            first.update(buffers, area, TerrainRenderType.CUTOUT_MIPPED);
            parameters.reset(area);
            require(first.update(buffers, area, TerrainRenderType.CUTOUT_MIPPED) && first.drawCount == 0,
                    "Section reset must invalidate cached geometry");
            Initializer.LOGGER.info("Terrain region cache smoke test passed");
        } finally {
            if (first.commands != null) first.commands.freeBuffer();
            if (second.commands != null) second.commands.freeBuffer();
            RegionBatchStats.reset();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
