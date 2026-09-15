package net.vulkanmod.render.chunk.voxel;

import net.minecraft.core.Direction;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Pointer-free regression coverage for the bounded sparse lighting input ABI. */
public final class GpuSparseLightingSnapshotTest {
    private GpuSparseLightingSnapshotTest() {}

    public static void main(String[] args) {
        verify();
        System.out.println("Sparse lighting snapshot tests passed");
    }

    public static void verify() {
        SectionVoxelSnapshot isolated = isolatedCube();
        GpuLightingDemandMap demand = GpuLightingDemandMap.analyze(isolated);
        GpuLightingDemandTelemetry.Demand telemetry = GpuLightingDemandTelemetry.analyze(isolated);
        require(demand.uniqueSamples() == telemetry.uniqueSamples(),
                "Sparse capture and sizing telemetry must agree on exact demanded sample count");
        require(demand.candidateFaces() == telemetry.candidateFaces(),
                "Sparse capture and sizing telemetry must agree on surviving candidate faces");
        require(demand.uniqueSamples() > 0 && demand.uniqueSamples() < GpuSparseLightingSnapshot.MAX_SAMPLES,
                "Isolated canonical cube must produce a nonempty bounded lighting demand");

        int[] denseLight = new int[GpuLightingDemandMap.SAMPLE_COUNT];
        int[] denseShade = new int[GpuLightingDemandMap.SAMPLE_COUNT];
        boolean[] densePass = new boolean[GpuLightingDemandMap.SAMPLE_COUNT];
        for(int i = 0; i < denseLight.length; ++i) {
            denseLight[i] = 0x12000000 ^ i * 31;
            denseShade[i] = Float.floatToRawIntBits(0.2F + (i % 17) * 0.03125F);
            densePass[i] = (i % 3) != 0;
        }
        int[] directional = new int[Direction.values().length];
        for(Direction direction : Direction.values())
            directional[direction.ordinal()] = Float.floatToRawIntBits(0.45F + direction.ordinal() * 0.05F);

        GpuSparseLightingSnapshot sparse = GpuSparseLightingSnapshot.fromDenseReference(
                isolated, denseLight, denseShade, densePass, directional);
        require(sparse != null && sparse.sampleCount() == demand.uniqueSamples(),
                "Eligible sparse lighting must retain every demanded point exactly once");
        require(sparse.byteSize() <= GpuSparseLightingSnapshot.MAX_BYTES,
                "Sparse lighting payload must stay within its explicit cap");

        int visited = 0;
        for(int lattice = 0; lattice < GpuLightingDemandMap.SAMPLE_COUNT; ++lattice) {
            int x = lattice % GpuLightingDemandMap.DOMAIN_WIDTH - 2;
            int y = (lattice / GpuLightingDemandMap.DOMAIN_WIDTH)
                    % GpuLightingDemandMap.DOMAIN_WIDTH - 2;
            int z = lattice / (GpuLightingDemandMap.DOMAIN_WIDTH * GpuLightingDemandMap.DOMAIN_WIDTH) - 2;
            if(!demand.demanded(lattice)) {
                require(!sparse.demanded(x, y, z) && sparse.compactRank(x, y, z) == -1,
                        "Undemanded points must remain absent from compact lighting");
                continue;
            }
            int rank = sparse.compactRank(x, y, z);
            require(rank == visited,
                    "Demand bitmap/rank prefix must map ascending lattice points densely");
            require(sparse.packedLight(x, y, z) == denseLight[lattice],
                    "Compact packed-light value must match dense oracle");
            require(sparse.shadeBrightnessBits(x, y, z) == denseShade[lattice],
                    "Compact shade-brightness bits must match dense oracle");
            require(sparse.lightPasses(x, y, z) == densePass[lattice],
                    "Compact AO predicate must match dense oracle");
            ++visited;
        }
        require(visited == sparse.sampleCount(),
                "Every compact sparse sample must be reachable through rank lookup");
        for(Direction direction : Direction.values())
            require(sparse.directionalShadeBits(direction) == directional[direction.ordinal()],
                    "Directional shade bits must remain exact");

        ByteBuffer buffer = ByteBuffer.allocate(sparse.byteSize() + 8).order(ByteOrder.BIG_ENDIAN);
        buffer.putInt(0x12345678);
        int prefixPosition = buffer.position();
        sparse.writeTo(buffer);
        require(buffer.order() == ByteOrder.BIG_ENDIAN && buffer.getInt(0) == 0x12345678,
                "Serializer must preserve caller byte order and prefix");
        require(buffer.position() == prefixPosition + sparse.byteSize(),
                "Serializer must advance by its exact bounded byte size");
        ByteBuffer gpu = buffer.duplicate().position(prefixPosition).slice().order(ByteOrder.LITTLE_ENDIAN);
        require(gpu.getInt(0) == GpuSparseLightingSnapshot.MAGIC
                        && gpu.getInt(4) == GpuSparseLightingSnapshot.VERSION,
                "Sparse lighting magic/version must be explicit");
        require(gpu.getInt(8) == isolated.x() && gpu.getInt(12) == isolated.y()
                        && gpu.getInt(16) == isolated.z(),
                "Sparse lighting origin must retain signed section coordinates");
        require(gpu.getInt(20) == sparse.sampleCount(),
                "Sparse lighting header must advertise exact compact count");
        require(gpu.getInt(24) == GpuSparseLightingSnapshot.DEMAND_WORDS
                        && gpu.getInt(28) == GpuSparseLightingSnapshot.RANK_WORDS,
                "Sparse lighting header must advertise fixed bitmap/rank sizes");
        require(gpu.getInt(36) == GpuSparseLightingSnapshot.DIRECTIONAL_SHADE_WORDS,
                "Sparse lighting header must advertise directional shade words");
        require(gpu.getInt(40) == GpuSparseLightingSnapshot.HEADER_WORDS,
                "Demand map must start immediately after the ABI header");

        require(GpuSparseLightingSnapshot.MAX_BYTES == 18_340,
                "Two-thousand-sample sparse ABI cap must remain explicit");
        reject(() -> sparse.writeTo(ByteBuffer.allocate(sparse.byteSize() - 1)));
        reject(() -> sparse.writeTo(ByteBuffer.allocate(sparse.byteSize() + 1).position(1)));
        reject(() -> GpuSparseLightingSnapshot.bytesFor(GpuSparseLightingSnapshot.MAX_SAMPLES + 1));

        SectionVoxelSnapshot dense = allQualifiedOpen();
        require(GpuLightingDemandMap.analyze(dense).uniqueSamples() > GpuSparseLightingSnapshot.MAX_SAMPLES,
                "Worst-case open qualified section must exceed sparse production cap");
        require(GpuSparseLightingSnapshot.fromDenseReference(
                        dense, denseLight, denseShade, densePass, directional) == null,
                "Over-cap lighting demand must fail closed instead of truncating output");
    }

    private static SectionVoxelSnapshot isolatedCube() {
        SectionVoxelSnapshot.Builder builder = new SectionVoxelSnapshot.Builder(-32, 64, 128);
        int center = SectionVoxelSnapshot.blockIndex(8, 8, 8);
        for(int i = 0; i < SectionVoxelSnapshot.BLOCK_COUNT; ++i) {
            int flags = SectionVoxelSnapshot.CPU_REQUIRED;
            if(i == center)
                flags |= SectionVoxelSnapshot.GPU_FULL_CUBE;
            builder.add(i == center ? 1 : 0, flags);
        }
        return builder.finish();
    }

    private static SectionVoxelSnapshot allQualifiedOpen() {
        SectionVoxelSnapshot.Builder builder = new SectionVoxelSnapshot.Builder(0, 0, 0);
        for(int i = 0; i < SectionVoxelSnapshot.BLOCK_COUNT; ++i)
            builder.add(1, SectionVoxelSnapshot.CPU_REQUIRED | SectionVoxelSnapshot.GPU_FULL_CUBE);
        return builder.finish();
    }

    private static void reject(Runnable action) {
        try { action.run(); }
        catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("Invalid sparse-lighting operation accepted");
    }

    private static void require(boolean condition, String message) {
        if(!condition) throw new AssertionError(message);
    }
}
