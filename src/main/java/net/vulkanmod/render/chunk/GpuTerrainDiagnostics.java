package net.vulkanmod.render.chunk;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.vulkanmod.Initializer;
import net.vulkanmod.render.chunk.voxel.GpuTerrainModelRegistry;
import net.vulkanmod.render.chunk.voxel.SectionVoxelSnapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Low-noise diagnostics for the experimental GPU terrain path.
 *
 * <p>Normal gameplay can encounter thousands of identical fallback decisions, so
 * logging every section would make the useful edge case impossible to find. Record
 * one concrete sample per stage/reason and emit periodic aggregate counts instead.
 * The diagnostics are enabled automatically with the experimental mesher and may be
 * disabled explicitly with {@code -Dvulkanmod.experimentalGpuTerrainDiagnostics=false}.</p>
 */
public final class GpuTerrainDiagnostics {
    private static final boolean ENABLED = Boolean.parseBoolean(System.getProperty(
            "vulkanmod.experimentalGpuTerrainDiagnostics", "true"))
            && Boolean.getBoolean("vulkanmod.experimentalGpuTerrainMesher");
    private static final long SUMMARY_INTERVAL_NANOS = 10_000_000_000L;
    private static final int MAX_BLOCKER_KEYS = 256;
    private static final int BLOCKER_SUMMARY_LIMIT = 8;

    private static final ConcurrentHashMap<String, AtomicLong> COUNTS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, AtomicLong> BLOCKER_COUNTS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, AtomicBoolean> SAMPLE_LOGGED = new ConcurrentHashMap<>();
    private static final AtomicLong LAST_SUMMARY_NANOS = new AtomicLong(System.nanoTime());

    private GpuTerrainDiagnostics() {}

    public static boolean enabled() {
        return ENABLED;
    }

    public static void record(String stage, String reason, RenderSection section,
                              long generation, String detail) {
        if(!ENABLED)
            return;
        int x = section == null ? Integer.MIN_VALUE : section.xOffset();
        int y = section == null ? Integer.MIN_VALUE : section.yOffset();
        int z = section == null ? Integer.MIN_VALUE : section.zOffset();
        record(stage, reason, x, y, z, generation, detail);
    }

    public static void record(String stage, String reason, int x, int y, int z,
                              long generation, String detail) {
        if(!ENABLED)
            return;
        String key = stage + "/" + reason;
        long count = COUNTS.computeIfAbsent(key, ignored -> new AtomicLong()).incrementAndGet();
        AtomicBoolean sampled = SAMPLE_LOGGED.computeIfAbsent(key, ignored -> new AtomicBoolean());
        if(sampled.compareAndSet(false, true)) {
            Initializer.LOGGER.info(
                    "VULKANMOD_GPU_TERRAIN_FALLBACK_SAMPLE: stage={} reason={} section=({}, {}, {}) generation={} count={} detail={}",
                    stage, reason, x, y, z, generation, count,
                    detail == null || detail.isBlank() ? "-" : detail);
        }
        maybeLogSummary();
    }

    /**
     * Explain the first conservative qualification boundary hit by this snapshot.
     * This intentionally mirrors the production qualifier closely enough to identify
     * the concrete unsupported state without changing the production decision.
     */
    public static void recordQualificationFailure(SectionVoxelSnapshot snapshot,
                                                  RenderSection section,
                                                  long generation) {
        recordQualificationFailure("preflight", snapshot, section, generation);
    }

    public static void recordQualificationFailure(String stage,
                                                  SectionVoxelSnapshot snapshot,
                                                  RenderSection section,
                                                  long generation) {
        if(!ENABLED)
            return;
        if(snapshot == null) {
            record(stage, "snapshot_missing", section, generation, null);
            return;
        }

        for(int index = 0; index < SectionVoxelSnapshot.BLOCK_COUNT; ++index) {
            int stateId = snapshot.stateId(index);
            int flags = snapshot.flags(index);

            // These are hard CPU-ownership boundaries even if the baked block model
            // itself happens to look like a reusable full cube.
            if((flags & SectionVoxelSnapshot.HAS_FLUID) != 0) {
                recordState(stage, "fluid", snapshot, section,
                        generation, index, stateId, flags);
                return;
            }
            if((flags & SectionVoxelSnapshot.HAS_BLOCK_ENTITY) != 0) {
                recordState(stage, "block_entity", snapshot, section,
                        generation, index, stateId, flags);
                return;
            }

            if((flags & SectionVoxelSnapshot.GPU_FULL_CUBE) != 0) {
                if(GpuTerrainModelRegistry.getFullCubeTemplate(stateId) == null) {
                    recordState(stage, "qualified_template_stale", snapshot, section,
                            generation, index, stateId, flags);
                    return;
                }
                continue;
            }

            BlockState state = Block.stateById(stateId);
            if(state == null) {
                recordState(stage, "state_missing", snapshot, section,
                        generation, index, stateId, flags);
                return;
            }
            if(!state.getFluidState().isEmpty()) {
                recordState(stage, "fluid", snapshot, section,
                        generation, index, stateId, flags);
                return;
            }
            if(state.getRenderShape() != RenderShape.INVISIBLE) {
                recordState(stage, "unsupported_visible_model", snapshot, section,
                        generation, index, stateId, flags);
                return;
            }
        }

        record(stage, "qualification_generation_changed", section, generation,
                "snapshot passed state scan but production qualifier rejected it");
    }

    public static void recordSuccess(String stage, RenderSection section, long generation,
                                     int faceCount, boolean cpuBypassed) {
        if(!ENABLED)
            return;
        String key = "success/" + stage + "/cpuBypassed=" + cpuBypassed;
        COUNTS.computeIfAbsent(key, ignored -> new AtomicLong()).incrementAndGet();
        AtomicBoolean sampled = SAMPLE_LOGGED.computeIfAbsent(key, ignored -> new AtomicBoolean());
        if(sampled.compareAndSet(false, true)) {
            Initializer.LOGGER.info(
                    "VULKANMOD_GPU_TERRAIN_SUCCESS_SAMPLE: stage={} section=({}, {}, {}) generation={} faces={} cpuBypassed={}",
                    stage, section.xOffset(), section.yOffset(), section.zOffset(),
                    generation, faceCount, cpuBypassed);
        }
        maybeLogSummary();
    }

    private static void recordState(String stage, String reason,
                                    SectionVoxelSnapshot snapshot, RenderSection section,
                                    long generation, int index, int stateId, int flags) {
        BlockState state = Block.stateById(stateId);
        recordBlocker(reason, stateId, state);
        int x = index & 15;
        int y = (index >>> 4) & 15;
        int z = (index >>> 8) & 15;
        String detail = "local=(" + x + "," + y + "," + z + ")"
                + " stateId=" + stateId
                + " state=" + String.valueOf(state)
                + " flags=0x" + Integer.toHexString(flags)
                + " snapshot=(" + snapshot.x() + "," + snapshot.y() + "," + snapshot.z() + ")";
        record(stage, reason, section, generation, detail);
    }

    private static void recordBlocker(String reason, int stateId, BlockState state) {
        String key = reason + "/stateId=" + stateId + "/state=" + String.valueOf(state);
        AtomicLong existing = BLOCKER_COUNTS.get(key);
        if(existing != null) {
            existing.incrementAndGet();
            return;
        }
        if(BLOCKER_COUNTS.size() >= MAX_BLOCKER_KEYS) {
            COUNTS.computeIfAbsent("diagnostics/blocker_key_overflow", ignored -> new AtomicLong())
                    .incrementAndGet();
            return;
        }
        BLOCKER_COUNTS.computeIfAbsent(key, ignored -> new AtomicLong()).incrementAndGet();
    }

    private static void maybeLogSummary() {
        long now = System.nanoTime();
        long previous = LAST_SUMMARY_NANOS.get();
        if(now - previous < SUMMARY_INTERVAL_NANOS
                || !LAST_SUMMARY_NANOS.compareAndSet(previous, now))
            return;

        Map<String, Long> ordered = new TreeMap<>();
        COUNTS.forEach((key, value) -> ordered.put(key, value.get()));
        StringBuilder summary = new StringBuilder();
        ordered.forEach((key, value) -> {
            if(summary.length() > 0)
                summary.append(", ");
            summary.append(key).append('=').append(value);
        });
        Initializer.LOGGER.info("VULKANMOD_GPU_TERRAIN_DIAGNOSTICS_SUMMARY: {}",
                summary.length() == 0 ? "no-events" : summary);

        List<Map.Entry<String, Long>> blockers = new ArrayList<>();
        BLOCKER_COUNTS.forEach((key, value) -> blockers.add(Map.entry(key, value.get())));
        blockers.sort(Comparator.<Map.Entry<String, Long>>comparingLong(Map.Entry::getValue)
                .reversed().thenComparing(Map.Entry::getKey));
        if(!blockers.isEmpty()) {
            StringBuilder top = new StringBuilder();
            int limit = Math.min(BLOCKER_SUMMARY_LIMIT, blockers.size());
            for(int i = 0; i < limit; ++i) {
                if(top.length() > 0)
                    top.append(", ");
                Map.Entry<String, Long> blocker = blockers.get(i);
                top.append(blocker.getKey()).append('=').append(blocker.getValue());
            }
            Initializer.LOGGER.info("VULKANMOD_GPU_TERRAIN_BLOCKER_SUMMARY: {}", top);
        }
    }
}
