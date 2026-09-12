package net.vulkanmod.render.chunk.voxel;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class SectionVoxelSnapshotTest {
    public static void main(String[] args) {
        for (int paletteSize : new int[]{1, 2, 257, 4096}) {
            var builder = new SectionVoxelSnapshot.Builder(-16, -64, 29999984);
            for (int z = 0; z < 16; z++) for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                int i = SectionVoxelSnapshot.blockIndex(x, y, z);
                builder.add(100000 + i % paletteSize, 8 | (i & 7));
            }
            var snapshot = builder.finish();
            require(snapshot.paletteSize() == paletteSize, "Palette cardinality, including >256 states");
            require(snapshot.byteSize() <= SectionVoxelSnapshot.MAX_BYTES, "Bounded serialized size");
            // Nonzero offset and deliberately opposite caller byte order.
            var target = ByteBuffer.allocate(snapshot.byteSize() + 8).order(ByteOrder.BIG_ENDIAN);
            target.putInt(0x12345678);
            snapshot.writeTo(target);
            require(target.position() == 4 + snapshot.byteSize(), "Exact write size");
            require(target.order() == ByteOrder.BIG_ENDIAN && target.getInt(0) == 0x12345678,
                    "Caller state/prefix preserved");
            var gpu = target.duplicate().position(4).slice().order(ByteOrder.LITTLE_ENDIAN);
            require(gpu.getInt(0) == SectionVoxelSnapshot.MAGIC && gpu.getInt(4) == 1, "ABI/version");
            require(gpu.getInt(16) == -16 && gpu.getInt(20) == -64 && gpu.getInt(24) == 29999984,
                    "Signed world origins");
            for (int i = 0; i < 4096; i++) {
                // Independent GLSL-style uint32 reads, not the Java accessors.
                int packed = gpu.getInt(gpu.getInt(36) * 4 + (i >>> 1) * 4);
                int pi = (packed >>> ((i & 1) * 16)) & 65535;
                require(gpu.getInt(gpu.getInt(32) * 4 + pi * 4) == 100000 + i % paletteSize,
                        "Shader palette decode");
                int flags = 0;
                for (int plane = 0; plane < 4; plane++) {
                    int word = gpu.getInt((gpu.getInt(40) + plane * 128 + (i >>> 5)) * 4);
                    flags |= ((word >>> (i & 31)) & 1) << plane;
                }
                require(flags == (8 | (i & 7)), "Shader flag decode across word boundaries");
                require(snapshot.stateId(i) == 100000 + i % paletteSize && snapshot.flags(i) == flags,
                        "CPU reference decode");
            }
            reject(() -> builder.add(0, 8));
            reject(builder::finish);
            reject(() -> snapshot.writeTo(ByteBuffer.allocate(snapshot.byteSize() - 1)));
            reject(() -> snapshot.writeTo(ByteBuffer.allocate(snapshot.byteSize() + 1).position(1)));
            reject(() -> snapshot.writeTo(ByteBuffer.allocate(snapshot.byteSize()).asReadOnlyBuffer()));
        }
        reject(() -> new SectionVoxelSnapshot.Builder(1, 0, 0));
        reject(() -> new SectionVoxelSnapshot.Builder(0, 0, 0).finish());
        reject(() -> new SectionVoxelSnapshot.Builder(0, 0, 0).add(-1, 8));
        reject(() -> new SectionVoxelSnapshot.Builder(0, 0, 0).add(1, 0));
        reject(() -> SectionVoxelSnapshot.blockIndex(16, 0, 0));

        var small = uniform();
        var budget = new RegionVoxelStore.Budget(small.byteSize(), 1);
        var first = new RegionVoxelStore(budget);
        var second = new RegionVoxelStore(budget);
        require(first.put(0, small), "First reservation");
        long revision = first.revision();
        require(first.put(0, small) && first.revision() > revision, "Replacement at budget limit");
        require(!second.put(0, small) && second.get(0) == null, "Shared memory/entry limit");
        first.clear();
        first.clear();
        require(second.put(511, small), "Clear returns capacity exactly once");
        second.remove(511);
        require(first.put(0, small), "Remove returns capacity");
        var largeBuilder = new SectionVoxelSnapshot.Builder(0, 0, 0);
        for (int i = 0; i < 4096; i++) largeBuilder.add(i, 8);
        require(!first.put(0, largeBuilder.finish()) && first.get(0) == null,
                "Rejected replacement must invalidate old content");
        require(second.put(0, small), "Rejected replacement leaks no reservation");
        second.put(0, null);
        require(second.get(0) == null && first.put(0, small), "Absent snapshot clears residency");
        first.clear();
        var countBudget = new RegionVoxelStore.Budget(Integer.MAX_VALUE, 1);
        var countStore = new RegionVoxelStore(countBudget);
        require(countStore.put(0, small) && !countStore.put(1, small), "Entry limit independently enforced");
        countStore.clear();
        System.out.println("Terrain voxel snapshot tests passed");
    }

    private static SectionVoxelSnapshot uniform() {
        var builder = new SectionVoxelSnapshot.Builder(0, 0, 0);
        for (int i = 0; i < 4096; i++) builder.add(0, 8);
        return builder.finish();
    }
    private static void reject(Runnable action) {
        try { action.run(); }
        catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("Invalid operation accepted");
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
