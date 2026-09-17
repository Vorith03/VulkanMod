package net.vulkanmod.render.chunk;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.vulkanmod.mixin.compatibility.EffectUniformBindingsTest;
import net.vulkanmod.render.vertex.TerrainRenderType;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashSet;

public final class RegionBatchLayoutTest {
    public static void main(String[] args) {
        var ids = new HashSet<Integer>();
        for (int x = 0; x < 128; x += 16) {
            for (int y = 0; y < 128; y += 16) {
                for (int z = 0; z < 128; z += 16) {
                    int id = RegionBatchLayout.packSection(x, y, z);
                    require(ids.add(id), "Unique section ID");
                    // Mirror the GLSL decode, covering all region corners and edges.
                    require((id & 7) * 16 == x, "X shader decode");
                    require(((id >>> 3) & 7) * 16 == y, "Y shader decode");
                    require(((id >>> 6) & 7) * 16 == z, "Z shader decode");
                    for (int origin : new int[]{-30000000, -128, 0, 29999872}) {
                        require(RegionBatchLayout.packSection((origin + x) - origin, y, z) == id,
                                "World-coordinate translation");
                    }
                }
            }
        }
        require(ids.size() == 512, "Full region capacity");
        for (int bad : new int[]{-16, 1, 127, 128, Integer.MAX_VALUE}) {
            reject(bad, 0, 0); reject(0, bad, 0); reject(0, 0, bad);
        }
        ByteBuffer commands = ByteBuffer.allocate(40).order(ByteOrder.nativeOrder());
        RegionBatchLayout.putCommand(commands, 36, 12, -24, 511);
        RegionBatchLayout.putCommand(commands, 6, 0, 8, 0);
        require(commands.position() == 40, "Two 20-byte commands");
        int[] words = {36, 1, 12, -24, 511, 6, 1, 0, 8, 0};
        commands.flip();
        for (int word : words) require(commands.getInt() == word, "Vulkan indexed-command ABI");
        require(RegionBatchLayout.drawLimit(-1) == 512, "Unsigned Vulkan limit");
        require(RegionBatchLayout.drawLimit(128) == 128, "Device batch limit");
        require(RegionBatchLayout.drawLimit(1) == 1, "Single draw limit");

        verifyFrameRetirementBarrier();
        verifyGpuTerrainDrawHandoff();
        EffectUniformBindingsTest.run();
        System.out.println("Terrain region layout tests passed");
    }

    private static void verifyFrameRetirementBarrier() {
        AreaUploadManager manager = new AreaUploadManager();
        manager.createLists(3);
        int[] retired = {0};
        manager.enqueueFrameRetirement(() -> retired[0]++);

        manager.updateFrame(0);
        require(retired[0] == 0,
                "Terrain retirement must wait for every frame slot");
        manager.updateFrame(2);
        require(retired[0] == 0,
                "Terrain retirement must remain pinned until the final frame slot");
        manager.updateFrame(1);
        require(retired[0] == 1,
                "Terrain retirement must run exactly after all frame slots cross their boundary");
        manager.updateFrame(0);
        manager.updateFrame(1);
        manager.updateFrame(2);
        require(retired[0] == 1,
                "Terrain retirement callback must execute only once");
    }

    private static void verifyGpuTerrainDrawHandoff() {
        // TerrainRenderType owns vanilla RenderType instances. Standalone JavaExec tests
        // do not pass through Minecraft's normal version/bootstrap path, so establish
        // the game version before bootstrapping registries and touching that enum.
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();

        var resident = new GpuTerrainOutputStore.Residency(7L, 160, 480, 6, 8, true);
        var gpu = GpuTerrainDrawHandoff.select(true, TerrainRenderType.SOLID, 7L,
                resident, 12, 4, 99);
        require(gpu.gpuResident(), "Exact-generation opaque residency should be selectable");
        require(gpu.indexCount() == 36 && gpu.firstIndex() == 0 && gpu.vertexOffset() == 8,
                "GPU handoff must derive an auto-quad command from persistent residency");

        var gpuOnly = GpuTerrainDrawHandoff.select(true, TerrainRenderType.SOLID, 7L,
                resident, 0, 0, 0);
        require(gpuOnly.gpuResident(),
                "Exact-generation GPU residency must be drawable without a CPU mesh");
        require(gpuOnly.indexCount() == 36 && gpuOnly.firstIndex() == 0
                        && gpuOnly.vertexOffset() == 8,
                "GPU-only fresh sections must derive the same persistent draw command");

        var missingGpuOnly = GpuTerrainDrawHandoff.select(true, TerrainRenderType.SOLID, 7L,
                null, 0, 0, 0);
        require(!missingGpuOnly.gpuResident() && missingGpuOnly.indexCount() == 0,
                "Fresh sections without published GPU residency must remain non-drawable until recovery/publication");

        var staleGpuOnly = GpuTerrainDrawHandoff.select(true, TerrainRenderType.SOLID, 8L,
                resident, 0, 0, 0);
        require(!staleGpuOnly.gpuResident() && staleGpuOnly.indexCount() == 0,
                "Fresh sections must not draw stale GPU residency");

        int maxFaces = GpuTerrainDrawHandoff.MAX_AUTO_INDEX_FACES;
        var maxResident = new GpuTerrainOutputStore.Residency(7L, 160,
                maxFaces * GpuTerrainOutputStore.BYTES_PER_FACE, maxFaces, 8, true);
        var maxGpu = GpuTerrainDrawHandoff.select(true, TerrainRenderType.SOLID, 7L,
                maxResident, 12, 4, 99);
        require(maxGpu.gpuResident() && maxGpu.indexCount() == maxFaces * 6,
                "Largest uint16-addressable GPU quad set should remain selectable");

        var tooManyFaces = new GpuTerrainOutputStore.Residency(7L, 160,
                (maxFaces + 1) * GpuTerrainOutputStore.BYTES_PER_FACE,
                maxFaces + 1, 8, true);
        requireCpuFallback(GpuTerrainDrawHandoff.select(true, TerrainRenderType.SOLID, 7L,
                tooManyFaces, 12, 4, 99), "uint16 index overflow");

        requireCpuFallback(GpuTerrainDrawHandoff.select(false, TerrainRenderType.SOLID, 7L,
                resident, 12, 4, 99), "Disabled handoff");
        requireCpuFallback(GpuTerrainDrawHandoff.select(true, TerrainRenderType.SOLID, 8L,
                resident, 12, 4, 99), "Stale generation");
        requireCpuFallback(GpuTerrainDrawHandoff.select(true, TerrainRenderType.TRANSLUCENT, 7L,
                resident, 12, 4, 99), "Translucent layer");
        requireCpuFallback(GpuTerrainDrawHandoff.select(true, TerrainRenderType.TRIPWIRE, 7L,
                resident, 12, 4, 99), "Tripwire layer");
        requireCpuFallback(GpuTerrainDrawHandoff.select(true, TerrainRenderType.SOLID, 7L,
                null, 12, 4, 99), "Missing residency");
        var invalid = new GpuTerrainOutputStore.Residency(7L, -1, 0, 0, 0, false);
        requireCpuFallback(GpuTerrainDrawHandoff.select(true, TerrainRenderType.SOLID, 7L,
                invalid, 12, 4, 99), "Invalid/overflow publication");
    }

    private static void requireCpuFallback(GpuTerrainDrawHandoff.DrawCommand command, String caseName) {
        require(!command.gpuResident(), caseName + " must retain CPU ownership");
        require(command.indexCount() == 12 && command.firstIndex() == 4 && command.vertexOffset() == 99,
                caseName + " must preserve the CPU command unchanged");
    }

    private static void reject(int x, int y, int z) {
        try { RegionBatchLayout.packSection(x, y, z); }
        catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Invalid section accepted");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
