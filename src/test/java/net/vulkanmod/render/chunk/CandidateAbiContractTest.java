package net.vulkanmod.render.chunk;

import net.vulkanmod.render.chunk.voxel.GpuRegionCandidateTable;
import org.lwjgl.vulkan.VkDrawIndexedIndirectCommand;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Pattern;

/** Cross-language shape guard supplements, never replaces, native selector round trips. */
public final class CandidateAbiContractTest {
    public static void main(String[] args) throws Exception {
        String shader;
        try (var in = CandidateAbiContractTest.class.getResourceAsStream(
                "/assets/vulkanmod/shaders/terrain/section_select_probe.comp")) {
            if (in == null) throw new AssertionError("Missing packaged selector shader");
            shader = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        var expected = Map.ofEntries(Map.entry("MAGIC", 0x47525331), Map.entry("VERSION", 1),
                Map.entry("HEADER_WORDS",8), Map.entry("RECORD_WORDS",8), Map.entry("COMMAND_WORDS",5),
                Map.entry("OUTPUT_HEADER_WORDS",4), Map.entry("READY",1), Map.entry("GRAPH_VISIBLE",2),
                Map.entry("DIRECT_SEED",4), Map.entry("PAIR_FIRST",8), Map.entry("PAIR_SECOND",16),
                Map.entry("LAYER_SHIFT",8), Map.entry("LAYER_MASK",15));
        for (var entry : expected.entrySet()) {
            var matcher = Pattern.compile("const uint " + entry.getKey() + " = (0x[0-9a-fA-F]+|[0-9]+)u;").matcher(shader);
            require(matcher.find() && Integer.decode(matcher.group(1)).equals(entry.getValue()), "GLSL " + entry.getKey());
        }
        require(GpuRegionCandidateTable.MAGIC==0x47525331 && GpuRegionCandidateTable.VERSION==1, "Java version");
        require(GpuRegionCandidateTable.HEADER_WORDS==8 && GpuRegionCandidateTable.RECORD_WORDS==8, "Java words");
        require(GpuRegionCandidateTable.READY==1 && GpuRegionCandidateTable.GRAPH_VISIBLE==2
                && GpuRegionCandidateTable.DIRECT_SEED==4 && GpuRegionCandidateTable.PAIR_FIRST==8
                && GpuRegionCandidateTable.PAIR_SECOND==16 && GpuRegionCandidateTable.LAYER_SHIFT==8
                && GpuRegionCandidateTable.LAYER_MASK==(15<<8), "Shifted Java flag representation");
        require(RegionBatchLayout.MAX_SECTIONS==512 && RegionBatchLayout.MAX_DRAW_COMMANDS==1024, "Capacity");
        require(shader.contains("inputTable.words[4] <= 1024u"), "Shader capacity");
        require(RegionBatchLayout.STRIDE==20 && VkDrawIndexedIndirectCommand.SIZEOF==20, "Native stride");
        require(VkDrawIndexedIndirectCommand.INDEXCOUNT==0 && VkDrawIndexedIndirectCommand.INSTANCECOUNT==4
                && VkDrawIndexedIndirectCommand.FIRSTINDEX==8 && VkDrawIndexedIndirectCommand.VERTEXOFFSET==12
                && VkDrawIndexedIndirectCommand.FIRSTINSTANCE==16, "Native field offsets");
        var builder = new GpuRegionCandidateTable.Builder(0x1234567887654321L, -16, -64, 32);
        builder.add(36,1,12,-24,511,GpuRegionCandidateTable.flags(true,true,false,2));
        var table = builder.finish();
        ByteBuffer bytes = ByteBuffer.allocate(table.byteSize()+8).order(ByteOrder.nativeOrder());
        bytes.position(8); table.writeTo(bytes); bytes.flip().position(8);
        int[] words = {0x47525331,1,0x87654321,0x12345678,1,-16,-64,32,36,1,12,-24,511,515,0,0};
        for (int word : words) require(bytes.getInt()==word,"Serialized header/record offset");
        var full = new GpuRegionCandidateTable.Builder(1,0,0,0);
        for(int i=0;i<1023;i++) full.add(6,1,0,0,i%512,3);
        try { full.addPair(6,0,0,6,0,0,0,3); throw new AssertionError("Accepted over-capacity pair"); }
        catch(IllegalArgumentException expectedFailure) { }
        require(full.finish().candidateCount()==1023, "No partial pair mutation");
        full.add(6,1,0,0,511,3);
        require(full.finish().candidateCount()==1024,"Last single slot remains usable");
        System.out.println("Candidate ABI constants/offsets/atomic-capacity checks passed");
    }
    private static void require(boolean ok,String message) { if(!ok) throw new AssertionError(message); }
}
