package net.vulkanmod.vulkan.shader.layout;

import net.vulkanmod.vulkan.shader.Uniforms;
import org.apache.commons.lang3.Validate;
import org.lwjgl.system.MemoryUtil;

import java.util.function.Supplier;

public class Vec1i extends Field {
    private Supplier<Integer> intSupplier;

    public Vec1i(FieldInfo fieldInfo) {
        super(fieldInfo);
    }

    void setSupplier() {
        this.intSupplier = Uniforms.vec1i_uniformMap.get(this.fieldInfo.name);

        Validate.notNull(this.intSupplier, "Field name not found: " + this.fieldInfo.name);
    }

    void update(long ptr) {
        // EffectInstance uniforms bind their own mapped storage after GLSL
        // conversion. Scalar fields historically bypassed Field.values and read
        // only the global Uniforms map, leaving this supplier null for effects.
        if(this.values != null) {
            super.update(ptr);
            return;
        }

        if(this.intSupplier == null) {
            throw new IllegalStateException("No supplier bound for int uniform field: " + this.fieldInfo.name);
        }

        int i = this.intSupplier.get();
        MemoryUtil.memPutInt(ptr + this.offset, i);
    }
}
