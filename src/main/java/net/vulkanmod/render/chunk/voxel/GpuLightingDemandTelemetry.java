package net.vulkanmod.render.chunk.voxel;

import net.minecraft.core.Direction;
import net.vulkanmod.Initializer;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

/** Opt-in sizing telemetry only; this does not resolve or retain lighting values. */
public final class GpuLightingDemandTelemetry {
    private static final boolean ENABLED = Boolean.getBoolean(
            "vulkanmod.debugGpuLightingDemand");
    private static final int SAMPLE_WORDS =
            CanonicalCubeLightingLattice.RECTANGULAR_SAMPLE_COUNT / 32;
    private static final int BRICK_WIDTH = 4;
    private static final int BRICKS_PER_AXIS = 5;
    private static final int BRICK_SAMPLES = BRICK_WIDTH * BRICK_WIDTH * BRICK_WIDTH;
    private static final int BRICK_PAYLOAD_BYTES = BRICK_SAMPLES * 2 * Integer.BYTES
            + (BRICK_SAMPLES / 32) * Integer.BYTES;
    private static final int DIRECTIONAL_SHADE_BYTES = Direction.values().length * Integer.BYTES;
    private static final int BRICK_MASK_BYTES = 4 * Integer.BYTES;

    private static final AtomicLong SECTIONS = new AtomicLong();
    private static final AtomicLong QUALIFIED_VOXELS = new AtomicLong();
    private static final AtomicLong CANDIDATE_FACES = new AtomicLong();
    private static final AtomicLong UNIQUE_SAMPLES = new AtomicLong();
    private static final AtomicLong ACTIVE_BRICKS = new AtomicLong();
    private static final AtomicLong POINT_BYTES = new AtomicLong();
    private static final AtomicLong BRICK_BYTES = new AtomicLong();
    private static final AtomicLong CPU_MESH_BYTES = new AtomicLong();
    private static final AtomicLongArray DENSITY_BUCKETS = new AtomicLongArray(5);

    private GpuLightingDemandTelemetry() {}

    public static void record(SectionVoxelSnapshot snapshot, long cpuMeshBytes) {
        if(!ENABLED || snapshot == null)
            return;
        if(cpuMeshBytes < 0L)
            throw new IllegalArgumentException("CPU mesh byte count must be nonnegative");

        Demand demand = analyze(snapshot);
        long sections = SECTIONS.incrementAndGet();
        QUALIFIED_VOXELS.addAndGet(demand.qualifiedVoxels);
        CANDIDATE_FACES.addAndGet(demand.candidateFaces);
        UNIQUE_SAMPLES.addAndGet(demand.uniqueSamples);
        ACTIVE_BRICKS.addAndGet(demand.activeBricks);
        POINT_BYTES.addAndGet(demand.projectedPointBytes);
        BRICK_BYTES.addAndGet(demand.projectedBrickBytes);
        CPU_MESH_BYTES.addAndGet(cpuMeshBytes);
        DENSITY_BUCKETS.incrementAndGet(densityBucket(demand.uniqueSamples));

        if(sections == 1L || (sections & 127L) == 0L) {
            long meshBytes = CPU_MESH_BYTES.get();
            long pointBytes = POINT_BYTES.get();
            long brickBytes = BRICK_BYTES.get();
            Initializer.LOGGER.info(
                    "VULKANMOD_GPU_LIGHTING_DEMAND: sections={} latest=({}, {}, {}) qualified={} faces={} unique(avg/latest)={}/{} bricks(avg/latest)={}/{} projected(point/brick/cpuMesh)={}/{}/{} ratios={}pct/{}pct densityBuckets[0,<=25,<=50,<=75,>75]={}/{}/{}/{}/{}",
                    sections, snapshot.x(), snapshot.y(), snapshot.z(),
                    QUALIFIED_VOXELS.get(), CANDIDATE_FACES.get(),
                    UNIQUE_SAMPLES.get() / sections, demand.uniqueSamples,
                    ACTIVE_BRICKS.get() / sections, demand.activeBricks,
                    pointBytes, brickBytes, meshBytes,
                    percent(pointBytes, meshBytes), percent(brickBytes, meshBytes),
                    DENSITY_BUCKETS.get(0), DENSITY_BUCKETS.get(1),
                    DENSITY_BUCKETS.get(2), DENSITY_BUCKETS.get(3),
                    DENSITY_BUCKETS.get(4));
        }
    }

    static Demand analyze(SectionVoxelSnapshot snapshot) {
        if(snapshot == null)
            throw new IllegalArgumentException("Snapshot must be present");
        int[] samples = new int[SAMPLE_WORDS];
        int[] bricks = new int[4];
        int qualifiedVoxels = 0;
        int candidateFaces = 0;

        for(int index = 0; index < SectionVoxelSnapshot.BLOCK_COUNT; ++index) {
            if(!hasFlag(snapshot, index, SectionVoxelSnapshot.GPU_FULL_CUBE))
                continue;
            ++qualifiedVoxels;
            int faceMask = candidateFaceMask(snapshot, index);
            int x = index & 15;
            int y = (index >>> 4) & 15;
            int z = (index >>> 8) & 15;
            for(Direction face : Direction.values()) {
                if((faceMask & (1 << face.ordinal())) == 0)
                    continue;
                ++candidateFaces;
                markFaceSamples(samples, bricks, x, y, z, face);
            }
        }

        int uniqueSamples = bitCount(samples);
        int activeBricks = bitCount(bricks);
        int pointBytes = uniqueSamples == 0 ? 0
                : SAMPLE_WORDS * 2 * Integer.BYTES
                + uniqueSamples * 2 * Integer.BYTES
                + ((uniqueSamples + 31) >>> 5) * Integer.BYTES
                + DIRECTIONAL_SHADE_BYTES;
        int brickBytes = activeBricks == 0 ? 0
                : BRICK_MASK_BYTES + activeBricks * BRICK_PAYLOAD_BYTES
                + DIRECTIONAL_SHADE_BYTES;
        return new Demand(qualifiedVoxels, candidateFaces, uniqueSamples,
                activeBricks, pointBytes, brickBytes);
    }

    private static int candidateFaceMask(SectionVoxelSnapshot snapshot, int index) {
        int x = index & 15;
        int y = (index >>> 4) & 15;
        int z = (index >>> 8) & 15;
        int mask = 0;
        if(y == 0 ? !snapshot.boundaryNeighborSolidRender(index, 0)
                : !hasFlag(snapshot, index - 16, SectionVoxelSnapshot.SOLID_RENDER)) mask |= 1;
        if(y == 15 ? !snapshot.boundaryNeighborSolidRender(index, 1)
                : !hasFlag(snapshot, index + 16, SectionVoxelSnapshot.SOLID_RENDER)) mask |= 2;
        if(z == 0 ? !snapshot.boundaryNeighborSolidRender(index, 2)
                : !hasFlag(snapshot, index - 256, SectionVoxelSnapshot.SOLID_RENDER)) mask |= 4;
        if(z == 15 ? !snapshot.boundaryNeighborSolidRender(index, 3)
                : !hasFlag(snapshot, index + 256, SectionVoxelSnapshot.SOLID_RENDER)) mask |= 8;
        if(x == 0 ? !snapshot.boundaryNeighborSolidRender(index, 4)
                : !hasFlag(snapshot, index - 1, SectionVoxelSnapshot.SOLID_RENDER)) mask |= 16;
        if(x == 15 ? !snapshot.boundaryNeighborSolidRender(index, 5)
                : !hasFlag(snapshot, index + 1, SectionVoxelSnapshot.SOLID_RENDER)) mask |= 32;
        return mask;
    }

    private static void markFaceSamples(int[] samples, int[] bricks,
                                        int x, int y, int z, Direction face) {
        Direction[] tangents = tangents(face);
        int centerX = x + face.getStepX();
        int centerY = y + face.getStepY();
        int centerZ = z + face.getStepZ();
        mark(samples, bricks, centerX, centerY, centerZ);
        for(Direction tangent : tangents) {
            int sideX = centerX + tangent.getStepX();
            int sideY = centerY + tangent.getStepY();
            int sideZ = centerZ + tangent.getStepZ();
            mark(samples, bricks, sideX, sideY, sideZ);
            mark(samples, bricks, sideX + face.getStepX(),
                    sideY + face.getStepY(), sideZ + face.getStepZ());
        }
        int[][] diagonals = { { 0, 2 }, { 0, 3 }, { 1, 2 }, { 1, 3 } };
        for(int[] diagonal : diagonals) {
            Direction first = tangents[diagonal[0]];
            Direction second = tangents[diagonal[1]];
            mark(samples, bricks,
                    centerX + first.getStepX() + second.getStepX(),
                    centerY + first.getStepY() + second.getStepY(),
                    centerZ + first.getStepZ() + second.getStepZ());
        }
    }

    private static void mark(int[] samples, int[] bricks, int x, int y, int z) {
        int index = CanonicalCubeLightingLattice.index(
                CanonicalCubeLightingLattice.Layout.RECTANGULAR_20, x, y, z);
        if(index < 0)
            throw new AssertionError("Canonical lighting demand escaped the 20-cube");
        samples[index >>> 5] |= 1 << (index & 31);
        int brick = ((x + 2) / BRICK_WIDTH)
                + BRICKS_PER_AXIS * (((y + 2) / BRICK_WIDTH)
                + BRICKS_PER_AXIS * ((z + 2) / BRICK_WIDTH));
        bricks[brick >>> 5] |= 1 << (brick & 31);
    }

    private static Direction[] tangents(Direction face) {
        return switch(face) {
            case DOWN -> new Direction[] { Direction.WEST, Direction.EAST,
                    Direction.NORTH, Direction.SOUTH };
            case UP -> new Direction[] { Direction.EAST, Direction.WEST,
                    Direction.NORTH, Direction.SOUTH };
            case NORTH -> new Direction[] { Direction.UP, Direction.DOWN,
                    Direction.EAST, Direction.WEST };
            case SOUTH -> new Direction[] { Direction.WEST, Direction.EAST,
                    Direction.DOWN, Direction.UP };
            case WEST -> new Direction[] { Direction.UP, Direction.DOWN,
                    Direction.NORTH, Direction.SOUTH };
            case EAST -> new Direction[] { Direction.DOWN, Direction.UP,
                    Direction.NORTH, Direction.SOUTH };
        };
    }

    private static boolean hasFlag(SectionVoxelSnapshot snapshot, int index, int flag) {
        return (snapshot.flags(index) & flag) != 0;
    }

    private static int bitCount(int[] words) {
        int count = 0;
        for(int word : words)
            count += Integer.bitCount(word);
        return count;
    }

    private static int densityBucket(int samples) {
        if(samples == 0) return 0;
        if(samples <= 2_000) return 1;
        if(samples <= 4_000) return 2;
        if(samples <= 6_000) return 3;
        return 4;
    }

    private static long percent(long value, long total) {
        return total == 0L ? 0L : value * 100L / total;
    }

    record Demand(int qualifiedVoxels, int candidateFaces, int uniqueSamples,
                  int activeBricks, int projectedPointBytes,
                  int projectedBrickBytes) {}
}
