package net.vulkanmod.mixin.compatibility;

import net.vulkanmod.vulkan.shader.EffectUniformBindings;
import net.vulkanmod.vulkan.shader.descriptor.UBO;
import net.vulkanmod.vulkan.shader.layout.AlignedStruct;
import net.vulkanmod.vulkan.shader.layout.Field;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.Collections;

public final class EffectUniformBindingsTest {
    public static void run() {
        AlignedStruct.Builder builder = new AlignedStruct.Builder();
        UBO ubo;
        try (Field.DefaultSupplierBindingScope ignored = Field.deferDefaultSupplierBinding()) {
            builder.addFieldInfo("vec2", "InSize");
            ubo = builder.buildUBO(0, 0);
        }

        EffectUniformBindings bindings = new EffectUniformBindings();
        try {
            bindings.bind(ubo, Collections.emptyMap());
            require(bindings.fallbackBufferCount() == 1, "Missing effect uniform received fallback storage");

            ByteBuffer destination = MemoryUtil.memAlloc(ubo.getSize());
            try {
                for(int i = 0; i < destination.capacity(); ++i) {
                    destination.put(i, (byte)0x5A);
                }

                ubo.update(MemoryUtil.memAddress(destination));
                for(int i = 0; i < destination.capacity(); ++i) {
                    require(destination.get(i) == 0, "Fallback effect uniform is zero-initialized");
                }
            } finally {
                MemoryUtil.memFree(destination);
            }
        } finally {
            bindings.close();
        }

        require(bindings.fallbackBufferCount() == 0, "Fallback effect uniform storage released");
        bindings.close();
        System.out.println("Post-effect unmanaged uniform tests passed");
    }

    private static void require(boolean condition, String message) {
        if(!condition) {
            throw new AssertionError(message);
        }
    }
}
