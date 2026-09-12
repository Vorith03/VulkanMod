package net.vulkanmod.render.chunk;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.vulkanmod.Initializer;
import net.vulkanmod.render.chunk.build.ChunkTask;
import net.vulkanmod.render.chunk.build.TaskDispatcher;
import net.vulkanmod.render.chunk.voxel.RegionVoxelStore;
import net.vulkanmod.render.chunk.voxel.SectionVoxelSnapshot;
import net.vulkanmod.render.vertex.TerrainRenderType;
import org.joml.Vector3i;

import java.util.EnumMap;

/** Exercises the actual section publication queue and region/fine-grid lifecycle at startup. */
public final class SectionVoxelSmokeTest {
    private SectionVoxelSmokeTest() {}

    public static void verify() {
        var area = new ChunkArea(0, new Vector3i(-128, -128, 128));
        var section = new RenderSection(0, -16, -96, 176);
        section.setChunkArea(area);
        var dispatcher = new TaskDispatcher();
        try {
            var builder = new SectionVoxelSnapshot.Builder(-16, -96, 176);
            int index = 0;
            for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(-16, -96, 176), new BlockPos(-1, -81, 191))) {
                require(SectionVoxelSnapshot.blockIndex(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15)
                        == index++, "Minecraft traversal must match voxel ABI");
                builder.add(Block.getId((index & 1) == 0 ? Blocks.STONE.defaultBlockState()
                        : Blocks.WATER.defaultBlockState()), SectionVoxelSnapshot.CPU_REQUIRED);
            }
            var snapshot = builder.finish();
            require(snapshot.paletteSize() == 2, "Live Minecraft registry state IDs");
            long generation = section.getVoxelGeneration();
            var task = new ChunkTask.BuildTask(section, null, false);
            dispatcher.scheduleSectionUpdate(task, section, new EnumMap<>(TerrainRenderType.class),
                    () -> section.publishVoxels(snapshot, generation));
            dispatcher.uploadAllPendingUploads();
            require((area.getVoxels(-16, -96, 176) == snapshot) == RegionVoxelStore.ENABLED,
                    "Feature-gated accepted publication");
            require(!area.drawBuffers.hasLiveGeometry(), "Voxel publication must not invent mesh geometry");
            long meshRevision = area.drawBuffers.getMeshRevision(TerrainRenderType.CUTOUT_MIPPED);
            area.publishVoxels(-16, -96, 176, snapshot);
            require(area.drawBuffers.getMeshRevision(TerrainRenderType.CUTOUT_MIPPED) == meshRevision,
                    "Voxel updates must be independent of mesh revisions");

            area.removeVoxels(-16, -96, 176);
            task = new ChunkTask.BuildTask(section, null, false);
            dispatcher.scheduleSectionUpdate(task, section, new EnumMap<>(TerrainRenderType.class),
                    () -> section.publishVoxels(snapshot, generation));
            task.cancel();
            dispatcher.uploadAllPendingUploads();
            require(area.getVoxels(-16, -96, 176) == null, "Cancelled worker publication must be rejected");

            section.publishVoxels(snapshot, generation);
            section.setOrigin(-16, -96, 176);
            section.publishVoxels(snapshot, generation);
            require(area.getVoxels(-16, -96, 176) == null, "Old generation rejected even at identical coordinates");
            section.publishVoxels(snapshot, section.getVoxelGeneration());
            long beforeDirty = section.getVoxelGeneration();
            section.setDirty(false);
            section.publishVoxels(snapshot, beforeDirty);
            require(area.getVoxels(-16, -96, 176) == null, "Dirty generation rejects pending snapshot");
            section.publishVoxels(snapshot, section.getVoxelGeneration());
            section.release();
            require(area.getVoxels(-16, -96, 176) == null, "Section release clears voxel residency");

            section.publishVoxels(snapshot, section.getVoxelGeneration());
            area.repositionForReuse(256, -128, 128);
            require(area.getVoxels(-16, -96, 176) == null && area.getVoxels(368, -96, 176) == null,
                    "Coarse region wrap cannot reinterpret old voxel contents");
            // Releasing a fine section after the coarse ring has wrapped is harmless.
            section.setOrigin(368, -96, 176);
            area.setPosition(-128, -128, 128);
            area.publishVoxels(-16, -96, 176, snapshot);
            area.releaseBuffers();
            require(area.getVoxels(-16, -96, 176) == null, "World/region release clears voxel residency");
            Initializer.LOGGER.info("Terrain voxel lifecycle smoke passed (capture={})", RegionVoxelStore.ENABLED);
        } finally {
            dispatcher.stopThreads();
            dispatcher.fixedBuffers.freeAll();
            area.releaseBuffers();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
