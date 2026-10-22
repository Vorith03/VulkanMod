package net.vulkanmod.render.chunk;

import java.util.List;

/** Numeric, recording-time geometry identities. Never consult live sections after a fence. */
public final class TerrainDrawProvenance {
    public record Geometry(int slot, int indexCount, int firstIndex, int vertexOffset,
                           long generation, long inputGeneration, String producer) {}
    private TerrainDrawProvenance() {}

    /** All geometry words must match. Ambiguity is unknown, never the current input generation. */
    public static Geometry match(List<Geometry> snapshot, int[] words, int offset) {
        if(offset < 0 || offset > words.length - 5 || words[offset] == 0 || words[offset + 1] != 1)
            return null;
        Geometry result = null;
        for(Geometry geometry : snapshot) {
            if(geometry.slot != words[offset + 4] || geometry.indexCount != words[offset]
                    || geometry.firstIndex != words[offset + 2] || geometry.vertexOffset != words[offset + 3]) continue;
            if(result != null) return null;
            result = geometry;
        }
        return result;
    }
}
