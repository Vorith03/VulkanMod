package net.vulkanmod.render.chunk.voxel;

import net.vulkanmod.Initializer;
import net.vulkanmod.render.chunk.AreaUploadManager;
import net.vulkanmod.render.chunk.ChunkArea;
import net.vulkanmod.render.chunk.GpuTerrainOutputStore;
import net.vulkanmod.render.vertex.TerrainRenderType;
import net.vulkanmod.vulkan.Vulkan;
import org.joml.Vector3i;

import java.util.HashMap;
import java.util.Map;

/**
 * Real Vulkan oracle for bounded section-wide complete terrain generation.
 *
 * <p>The smoke now exercises the same reusable {@link GpuTerrainSectionMesher}
 * dispatcher intended for the production bridge. Four isolated qualified voxels,
 * including a section corner, share one exact sparse-lighting record. Compact ordering
 * is deliberately not trusted: every result is joined through its encoded voxel/face
 * descriptor and compared with the CPU-validated single-block complete-vertex probe.</p>
 */
public final class GpuTerrainSectionMesherSmokeTest {
    private static final int WORDS_PER_VERTEX = GpuTerrainSectionMesher.WORDS_PER_VERTEX;
    private static final int WORDS_PER_FACE = GpuTerrainSectionMesher.WORDS_PER_FACE;
    private static final int EXPECTED_VOXELS = 4;
    private static final int EXPECTED_FACES = EXPECTED_VOXELS * 6;
    private static final int OVERFLOW_CAPACITY = 7;
    private static final long GENERATION = 811L;

    private static final int[] QUALIFIED_BLOCKS = {
            SectionVoxelSnapshot.blockIndex(1, 1, 1),
            SectionVoxelSnapshot.blockIndex(8, 8, 8),
            SectionVoxelSnapshot.blockIndex(14, 3, 12),
            SectionVoxelSnapshot.blockIndex(15, 15, 15)
    };

    private GpuTerrainSectionMesherSmokeTest() {}

    public static void verify() {
        if(AreaUploadManager.INSTANCE == null)
            throw new AssertionError("Section mesher smoke requires the terrain upload manager");

        GpuTerrainModelTable table = GpuTerrainModelTable.captureCurrent();
        require(table.templateCount() > 0,
                "Section mesher smoke requires at least one qualified model template");
        int qualifiedState = table.stateIdForTemplate(0);
        require(table.templateIndexForStateId(qualifiedState) == 0,
                "Section mesher model state must round-trip through the current table");

        Fixture fixture = fixture(qualifiedState);
        GpuTerrainModelGpuStore modelStore = new GpuTerrainModelGpuStore();
        RegionVoxelGpuStore inputStore = new RegionVoxelGpuStore();
        ChunkArea area = new ChunkArea(79,
                new Vector3i(fixture.voxel.x(), fixture.voxel.y(), fixture.voxel.z()));
        try {
            require(modelStore.upload(table), "Section mesher model table must upload");
            GpuTerrainModelGpuStore.Residency model = modelStore.getResidency();
            require(model.valid() && model.generation() == table.generation(),
                    "Section mesher model table must be current");

            require(inputStore.upload(0, fixture.voxel, GENERATION),
                    "Section mesher voxel input must queue");
            require(inputStore.uploadLighting(0, fixture.lighting, GENERATION),
                    "Section mesher lighting input must queue");
            AreaUploadManager.INSTANCE.submitUploads();

            RegionVoxelGpuStore.Residency voxel = inputStore.getResidency(0);
            RegionVoxelGpuStore.Residency lighting = inputStore.getLightingResidency(0);
            require(voxel.valid() && lighting.valid()
                            && voxel.generation() == GENERATION
                            && lighting.generation() == GENERATION,
                    "Section mesher inputs must publish under one generation");

            Map<Integer, int[]> oracle = buildOracle(inputStore, voxel, lighting,
                    model, table.templateCount());

            GpuTerrainOutputStore.Reservation reservation = area.reserveGpuTerrainOutput(
                    fixture.voxel.x(), fixture.voxel.y(), fixture.voxel.z(),
                    TerrainRenderType.SOLID, GENERATION, EXPECTED_FACES);
            require(reservation != null,
                    "Section mesher exact output reservation must fit without growth");

            GpuTerrainSectionMesher.ValidationResult exact;
            try(GpuTerrainSectionMesher mesher = new GpuTerrainSectionMesher()) {
                exact = reservation.withTarget(target -> mesher.dispatchForValidation(
                        inputStore.getPageBuffer(voxel.pageIndex()), voxel,
                        inputStore.getPageBuffer(lighting.pageIndex()), lighting,
                        model, table.templateCount(), target, EXPECTED_FACES));
            }
            require(exact != null, "Section mesher exact target must remain live through submission");
            verifyExact(exact, oracle);

            require(area.publishGpuTerrainOutput(reservation,
                            exact.dispatch().writtenFaces(), false),
                    "Exact section mesher output must publish");
            GpuTerrainOutputStore.Residency resident = area.getGpuTerrainOutputResidency(
                    fixture.voxel.x(), fixture.voxel.y(), fixture.voxel.z(),
                    TerrainRenderType.SOLID);
            require(resident != null && resident.valid()
                            && resident.generation() == GENERATION
                            && resident.faceCount() == EXPECTED_FACES
                            && resident.byteLength() == EXPECTED_FACES
                            * GpuTerrainOutputStore.BYTES_PER_FACE,
                    "Exact section mesher residency must expose the complete compact output");

            int residentOffset = resident.byteOffset();
            GpuTerrainOutputStore.Reservation overflowReservation =
                    area.reserveGpuTerrainOutput(fixture.voxel.x(), fixture.voxel.y(),
                            fixture.voxel.z(), TerrainRenderType.SOLID,
                            GENERATION, OVERFLOW_CAPACITY);
            require(overflowReservation != null,
                    "Same-generation bounded retry reservation must fit");

            GpuTerrainSectionMesher.ValidationResult overflow;
            try(GpuTerrainSectionMesher mesher = new GpuTerrainSectionMesher()) {
                overflow = overflowReservation.withTarget(target -> mesher.dispatchForValidation(
                        inputStore.getPageBuffer(voxel.pageIndex()), voxel,
                        inputStore.getPageBuffer(lighting.pageIndex()), lighting,
                        model, table.templateCount(), target, OVERFLOW_CAPACITY));
            }
            require(overflow != null, "Overflow target must remain live through submission");
            GpuTerrainSectionMesher.DispatchResult overflowDispatch = overflow.dispatch();
            require(overflowDispatch.requestedFaces() == EXPECTED_FACES,
                    "Overflow dispatch must retain the exact requested face count");
            require(overflowDispatch.writtenFaces() == OVERFLOW_CAPACITY,
                    "Overflow dispatch must write exactly its declared capacity");
            require(overflowDispatch.overflow() && overflowDispatch.errorFlags() == 0,
                    "Overflow dispatch must fail only through the bounded-capacity flag");
            verifyDescriptors(overflow.descriptors(), OVERFLOW_CAPACITY);

            require(!area.publishGpuTerrainOutput(overflowReservation,
                            overflowDispatch.writtenFaces(), true),
                    "Overflow output must fail closed instead of replacing residency");
            GpuTerrainOutputStore.Residency afterOverflow = area.getGpuTerrainOutputResidency(
                    fixture.voxel.x(), fixture.voxel.y(), fixture.voxel.z(),
                    TerrainRenderType.SOLID);
            require(afterOverflow != null && afterOverflow.valid()
                            && afterOverflow.generation() == GENERATION
                            && afterOverflow.faceCount() == EXPECTED_FACES
                            && afterOverflow.byteOffset() == residentOffset,
                    "Failed same-generation retry must preserve the previous valid GPU mesh");

            require(area.reserveGpuTerrainOutput(fixture.voxel.x(), fixture.voxel.y(),
                            fixture.voxel.z(), TerrainRenderType.SOLID,
                            GENERATION - 1L, EXPECTED_FACES) == null,
                    "Stale section generation must not obtain an output reservation");

            Initializer.LOGGER.info(
                    "VULKANMOD_GPU_TERRAIN_SECTION_MESHER_OK: {} voxels, {} complete faces, shared production dispatcher, descriptor-keyed exact vertex joins, boundary lighting, forced {}-face overflow preserves resident fallback",
                    EXPECTED_VOXELS, EXPECTED_FACES, OVERFLOW_CAPACITY);
        } finally {
            Vulkan.waitIdle();
            area.releaseBuffers();
            inputStore.close();
            modelStore.close();
        }
    }

    private static Fixture fixture(int stateId) {
        SectionVoxelSnapshot.Builder builder = new SectionVoxelSnapshot.Builder(0, 64, 0);
        for(int index = 0; index < SectionVoxelSnapshot.BLOCK_COUNT; ++index) {
            boolean qualified = containsQualified(index);
            builder.add(qualified ? stateId : 0,
                    SectionVoxelSnapshot.CPU_REQUIRED
                            | (qualified ? SectionVoxelSnapshot.GPU_FULL_CUBE : 0));
        }
        SectionVoxelSnapshot voxel = builder.finish();

        int[] packedLight = new int[GpuLightingDemandMap.SAMPLE_COUNT];
        int[] shadeBits = new int[GpuLightingDemandMap.SAMPLE_COUNT];
        boolean[] passes = new boolean[GpuLightingDemandMap.SAMPLE_COUNT];
        for(int i = 0; i < packedLight.length; ++i) {
            int block = (i * 3 & 15) << 4;
            int sky = (15 - (i * 5 & 15)) << 4;
            packedLight[i] = block | (sky << 16);
            shadeBits[i] = Float.floatToRawIntBits(0.55F + (i % 5) * 0.08F);
            passes[i] = i % 7 != 0;
        }
        int[] directional = {
                Float.floatToRawIntBits(0.50F), Float.floatToRawIntBits(1.00F),
                Float.floatToRawIntBits(0.80F), Float.floatToRawIntBits(0.80F),
                Float.floatToRawIntBits(0.60F), Float.floatToRawIntBits(0.60F)
        };
        GpuSparseLightingSnapshot lighting = GpuSparseLightingSnapshot.fromDenseReference(
                voxel, packedLight, shadeBits, passes, directional);
        require(lighting != null,
                "Four isolated section-mesher cubes must fit the sparse-lighting cap");
        return new Fixture(voxel, lighting);
    }

    private static Map<Integer, int[]> buildOracle(
            RegionVoxelGpuStore inputStore,
            RegionVoxelGpuStore.Residency voxel,
            RegionVoxelGpuStore.Residency lighting,
            GpuTerrainModelGpuStore.Residency model,
            int templateCount) {
        Map<Integer, int[]> result = new HashMap<>();
        try(SparseLightingComputeProbe probe = new SparseLightingComputeProbe()) {
            for(int blockIndex : QUALIFIED_BLOCKS) {
                int[] complete = probe.dispatch(
                        inputStore.getPageBuffer(voxel.pageIndex()), voxel,
                        inputStore.getPageBuffer(lighting.pageIndex()), lighting,
                        blockIndex, model, templateCount);
                require(complete[0] == SparseLightingComputeProbe.RESULT_MAGIC
                                && complete[5] == 0,
                        "Per-block complete-vertex oracle must accept section-mesher fixture");
                result.put(blockIndex, complete);
            }
        }
        return result;
    }

    private static void verifyExact(GpuTerrainSectionMesher.ValidationResult result,
                                    Map<Integer, int[]> oracle) {
        GpuTerrainSectionMesher.DispatchResult dispatch = result.dispatch();
        require(dispatch.requestedFaces() == EXPECTED_FACES,
                "Section mesher must request six faces for every isolated qualified cube");
        require(dispatch.writtenFaces() == EXPECTED_FACES,
                "Section mesher must write every exact-capacity face");
        require(!dispatch.overflow() && dispatch.errorFlags() == 0,
                "Exact-capacity section mesher must complete without overflow/errors");
        require(result.descriptors().length == EXPECTED_FACES
                        && result.vertices().length == EXPECTED_FACES * WORDS_PER_FACE,
                "Exact section mesher readback shape mismatch");

        boolean[] seen = verifyDescriptors(result.descriptors(), EXPECTED_FACES);
        for(int slot = 0; slot < EXPECTED_FACES; ++slot) {
            int descriptor = result.descriptors()[slot];
            int voxel = descriptor & 0xfff;
            int face = descriptor >>> 12 & 7;
            int[] expected = oracle.get(voxel);
            require(expected != null, "Compacted face must belong to a qualified fixture voxel");
            int expectedBase = SparseLightingComputeProbe.VERTEX_RESULT_BASE
                    + face * 4 * WORDS_PER_VERTEX;
            int actualBase = slot * WORDS_PER_FACE;
            for(int word = 0; word < WORDS_PER_FACE; ++word) {
                if(result.vertices()[actualBase + word] != expected[expectedBase + word]) {
                    throw new AssertionError("Section mesher vertex mismatch slot=" + slot
                            + " voxel=" + voxel + " face=" + face + " word=" + word
                            + " expected=" + expected[expectedBase + word]
                            + " actual=" + result.vertices()[actualBase + word]);
                }
            }
        }
        for(int block : QUALIFIED_BLOCKS) {
            for(int face = 0; face < 6; ++face)
                require(seen[block * 6 + face],
                        "Section mesher compact set must contain every isolated cube face");
        }
    }

    private static boolean[] verifyDescriptors(int[] descriptors, int count) {
        require(descriptors.length == count, "Compact descriptor readback count mismatch");
        boolean[] seen = new boolean[SectionVoxelSnapshot.BLOCK_COUNT * 6];
        for(int descriptor : descriptors) {
            require((descriptor & 0x80000000) != 0,
                    "Section mesher compact descriptor must carry the live marker");
            int voxel = descriptor & 0xfff;
            int face = descriptor >>> 12 & 7;
            require(face < 6 && containsQualified(voxel),
                    "Section mesher descriptor must decode to a qualified fixture face");
            int key = voxel * 6 + face;
            require(!seen[key], "Section mesher compact output must not duplicate faces");
            seen[key] = true;
        }
        return seen;
    }

    private static boolean containsQualified(int index) {
        for(int block : QUALIFIED_BLOCKS) {
            if(block == index)
                return true;
        }
        return false;
    }

    private static void require(boolean condition, String message) {
        if(!condition)
            throw new AssertionError(message);
    }

    private record Fixture(SectionVoxelSnapshot voxel,
                           GpuSparseLightingSnapshot lighting) {}
}
