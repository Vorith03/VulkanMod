package net.vulkanmod.render.chunk.voxel;

import net.vulkanmod.vulkan.memory.StorageBufferUsageTest;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class SectionVoxelSnapshotTest {
    public static void main(String[] args) {
        for (int paletteSize : new int[]{1, 2, 257, 4096}) {
            var builder = new SectionVoxelSnapshot.Builder(-16, -64, 29999984);
            for (int z = 0; z < 16; z++) for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                int i = SectionVoxelSnapshot.blockIndex(x, y, z);
                int expectedFlags = SectionVoxelSnapshot.CPU_REQUIRED | (i & 7);
                if ((i & 1) == 0) expectedFlags |= SectionVoxelSnapshot.GPU_FULL_CUBE;
                builder.add(100000 + i % paletteSize, expectedFlags);
                if (y == 0) builder.setBoundaryNeighborSolidRender(i, 0, expectedHalo(i, 0));
                if (y == 15) builder.setBoundaryNeighborSolidRender(i, 1, expectedHalo(i, 1));
                if (z == 0) builder.setBoundaryNeighborSolidRender(i, 2, expectedHalo(i, 2));
                if (z == 15) builder.setBoundaryNeighborSolidRender(i, 3, expectedHalo(i, 3));
                if (x == 0) builder.setBoundaryNeighborSolidRender(i, 4, expectedHalo(i, 4));
                if (x == 15) builder.setBoundaryNeighborSolidRender(i, 5, expectedHalo(i, 5));
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
            require(gpu.getInt(0) == SectionVoxelSnapshot.MAGIC
                            && gpu.getInt(4) == SectionVoxelSnapshot.VERSION,
                    "ABI/version");
            require(gpu.getInt(44) == SectionVoxelSnapshot.FLAG_PLANES,
                    "Header advertises all flag planes");
            require(gpu.getInt(52) == SectionVoxelSnapshot.HALO_WORDS,
                    "Header advertises the six-face solid-render halo");
            require(gpu.getInt(16) == -16 && gpu.getInt(20) == -64 && gpu.getInt(24) == 29999984,
                    "Signed world origins");
            for (int i = 0; i < 4096; i++) {
                // Independent GLSL-style uint32 reads, not the Java accessors.
                int packed = gpu.getInt(gpu.getInt(36) * 4 + (i >>> 1) * 4);
                int pi = (packed >>> ((i & 1) * 16)) & 65535;
                require(gpu.getInt(gpu.getInt(32) * 4 + pi * 4) == 100000 + i % paletteSize,
                        "Shader palette decode");
                int flags = 0;
                for (int plane = 0; plane < SectionVoxelSnapshot.FLAG_PLANES; plane++) {
                    int word = gpu.getInt((gpu.getInt(40) + plane * SectionVoxelSnapshot.PLANE_WORDS
                            + (i >>> 5)) * 4);
                    flags |= ((word >>> (i & 31)) & 1) << plane;
                }
                int expectedFlags = SectionVoxelSnapshot.CPU_REQUIRED | (i & 7);
                if ((i & 1) == 0) expectedFlags |= SectionVoxelSnapshot.GPU_FULL_CUBE;
                require(flags == expectedFlags, "Shader flag decode across word boundaries/planes");
                require(snapshot.stateId(i) == 100000 + i % paletteSize && snapshot.flags(i) == flags,
                        "CPU reference decode");

                int x = i & 15;
                int y = (i >>> 4) & 15;
                int z = (i >>> 8) & 15;
                if (y == 0) verifyHalo(gpu, snapshot, i, 0);
                if (y == 15) verifyHalo(gpu, snapshot, i, 1);
                if (z == 0) verifyHalo(gpu, snapshot, i, 2);
                if (z == 15) verifyHalo(gpu, snapshot, i, 3);
                if (x == 0) verifyHalo(gpu, snapshot, i, 4);
                if (x == 15) verifyHalo(gpu, snapshot, i, 5);
            }
            reject(() -> builder.add(0, SectionVoxelSnapshot.CPU_REQUIRED));
            reject(() -> builder.setBoundaryNeighborSolidRender(0, 0, true));
            reject(builder::finish);
            reject(() -> snapshot.boundaryNeighborSolidRender(SectionVoxelSnapshot.blockIndex(1, 1, 1), 0));
            reject(() -> snapshot.boundaryNeighborSolidRender(0, 6));
            reject(() -> snapshot.writeTo(ByteBuffer.allocate(snapshot.byteSize() - 1)));
            reject(() -> snapshot.writeTo(ByteBuffer.allocate(snapshot.byteSize() + 1).position(1)));
            reject(() -> snapshot.writeTo(ByteBuffer.allocate(snapshot.byteSize()).asReadOnlyBuffer()));
        }
        reject(() -> new SectionVoxelSnapshot.Builder(1, 0, 0));
        reject(() -> new SectionVoxelSnapshot.Builder(0, 0, 0).finish());
        reject(() -> new SectionVoxelSnapshot.Builder(0, 0, 0).add(-1, SectionVoxelSnapshot.CPU_REQUIRED));
        reject(() -> new SectionVoxelSnapshot.Builder(0, 0, 0).add(1, 0));
        reject(() -> new SectionVoxelSnapshot.Builder(0, 0, 0).add(1, 32 | SectionVoxelSnapshot.CPU_REQUIRED));
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
        for (int i = 0; i < 4096; i++) largeBuilder.add(i, SectionVoxelSnapshot.CPU_REQUIRED);
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

        testPageAllocator();
        testGpuPageBudget();
        StorageBufferUsageTest.verify();
        System.out.println("Terrain voxel snapshot tests passed");
    }

    private static void verifyHalo(ByteBuffer gpu, SectionVoxelSnapshot snapshot, int index, int face) {
        int bit = haloBit(index, face);
        int haloOffset = gpu.getInt(48);
        int word = gpu.getInt((haloOffset + face * SectionVoxelSnapshot.HALO_FACE_WORDS + (bit >>> 5)) * 4);
        boolean decoded = ((word >>> (bit & 31)) & 1) != 0;
        require(decoded == expectedHalo(index, face), "Shader solid-render halo decode");
        require(snapshot.boundaryNeighborSolidRender(index, face) == decoded,
                "CPU solid-render halo reference decode");
    }

    private static int haloBit(int index, int face) {
        int x = index & 15;
        int y = (index >>> 4) & 15;
        int z = (index >>> 8) & 15;
        if (face <= 1) return x | (z << 4);
        if (face <= 3) return x | (y << 4);
        return z | (y << 4);
    }

    private static boolean expectedHalo(int index, int face) {
        return ((index * 31 + face * 17) & 3) == 0;
    }

    private static void testPageAllocator() {
        var allocator = new RegionVoxelPageAllocator(128, 16);
        var a = allocator.allocate(17);
        var b = allocator.allocate(33);
        var c = allocator.allocate(16);
        require(a != null && a.offset == 0 && a.byteLength == 17 && a.reservedBytes == 32,
                "Voxel page allocation aligns without changing payload length");
        require(b != null && b.offset == 32 && b.reservedBytes == 48,
                "Voxel page allocation remains contiguous");
        require(c != null && c.offset == 80 && allocator.usedBytes() == 96,
                "Voxel page accounting tracks aligned reservations");
        require(allocator.allocate(48) == null, "Voxel page refuses exhaustion instead of growing");

        allocator.free(b);
        allocator.free(a);
        var reused = allocator.allocate(64);
        require(reused != null && reused.offset == 0,
                "Coalesced ranges satisfy a larger replacement without page growth");
        allocator.free(reused);
        allocator.free(c);
        require(allocator.isEmpty() && allocator.freeBytes() == 128 && allocator.freeRangeCount() == 1,
                "Voxel page free ranges fully coalesce");
        reject(() -> allocator.free(c));
        require(allocator.allocate(129) == null, "Oversized voxel payload falls back cleanly");
        reject(() -> new RegionVoxelPageAllocator(127, 16));
        reject(() -> new RegionVoxelPageAllocator(128, 3));
    }

    private static void testGpuPageBudget() {
        var budget = new RegionVoxelGpuStore.PageBudget(1024);
        require(budget.tryReserve(512) && budget.usedBytes() == 512,
                "GPU page budget reserves fixed capacity");
        require(!budget.tryReserve(513) && budget.usedBytes() == 512 && budget.rejectedPages() == 1,
                "GPU page budget refuses oversubscription without changing usage");
        require(budget.tryReserve(512) && budget.usedBytes() == 1024,
                "GPU page budget accepts exact remaining capacity");
        require(!budget.tryReserve(1) && budget.rejectedPages() == 2,
                "GPU page budget counts pressure fallback");
        budget.release(512);
        require(budget.usedBytes() == 512, "GPU page budget releases one retired page");
        budget.release(512);
        require(budget.usedBytes() == 0, "GPU page budget returns to zero exactly");
        reject(() -> budget.release(1));
        reject(() -> new RegionVoxelGpuStore.PageBudget(0));
    }

    private static SectionVoxelSnapshot uniform() {
        var builder = new SectionVoxelSnapshot.Builder(0, 0, 0);
        for (int i = 0; i < 4096; i++) builder.add(0, SectionVoxelSnapshot.CPU_REQUIRED);
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
