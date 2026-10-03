package net.vulkanmod.vulkan.shader;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Immutable binding-1 layout; shader locations are explicit, including each matrix column. */
public record InstanceVertexFormat(int stride, List<Attribute> attributes) {
    public enum Format {
        FLOAT(4, 4), FLOAT2(8, 4), FLOAT3(12, 4), FLOAT4(16, 4),
        UBYTE4_NORMALIZED(4, 1), BYTE4_NORMALIZED(4, 1), USHORT2(4, 2), SHORT2(4, 2);

        public final int bytes;
        public final int alignment;
        Format(int bytes, int alignment) { this.bytes = bytes; this.alignment = alignment; }
    }

    public record Attribute(int location, Format format, int offset) {
        public Attribute {
            Objects.requireNonNull(format, "format");
            if(location < 0 || offset < 0 || offset % format.alignment != 0)
                throw new IllegalArgumentException("Invalid instance attribute location/offset/alignment");
        }
    }

    public InstanceVertexFormat {
        if(stride <= 0 || stride % 4 != 0)
            throw new IllegalArgumentException("Instance stride must be positive and four-byte aligned");
        attributes = List.copyOf(attributes);
        if(attributes.isEmpty()) throw new IllegalArgumentException("Instance attributes are empty");
        var locations = new HashSet<Integer>();
        for(int i = 0; i < attributes.size(); i++) {
            Attribute a = attributes.get(i);
            if(!locations.add(a.location) || (long)a.offset + a.format.bytes > stride)
                throw new IllegalArgumentException("Duplicate location or attribute outside instance stride");
            for(int j = 0; j < i; j++) {
                Attribute b = attributes.get(j);
                if(a.offset < (long)b.offset + b.format.bytes && b.offset < (long)a.offset + a.format.bytes)
                    throw new IllegalArgumentException("Overlapping instance attributes");
            }
        }
    }

    public void validateLimits(int vertexAttributes, int maxAttributes, int maxStride, int maxOffset) {
        if(vertexAttributes < 0 || (long)vertexAttributes + attributes.size() > maxAttributes || stride > maxStride)
            throw new IllegalArgumentException("Instance layout exceeds vertex input limits");
        for(Attribute a : attributes) {
            if(a.location < vertexAttributes || a.location >= maxAttributes || a.offset > maxOffset)
                throw new IllegalArgumentException("Instance location collides with model or exceeds device limits");
        }
    }

    /** Checks the fetch span relative to the bound slice, without overflowing int arithmetic. */
    public void validateRange(long availableBytes, int firstInstance, int instanceCount) {
        if(availableBytes < 0 || firstInstance < 0 || instanceCount < 0
                || instanceCount > 0 && ((long)firstInstance + instanceCount) * stride > availableBytes)
            throw new IllegalArgumentException("Instance draw exceeds uploaded slice");
    }
}
