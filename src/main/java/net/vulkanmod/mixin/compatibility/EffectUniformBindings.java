package net.vulkanmod.mixin.compatibility;

import com.mojang.blaze3d.shaders.Uniform;
import net.vulkanmod.vulkan.shader.descriptor.UBO;
import net.vulkanmod.vulkan.shader.layout.Field;
import net.vulkanmod.vulkan.util.MappedBuffer;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Binds Minecraft Uniform storage to fields produced by VulkanMod's GLSL-to-UBO
 * conversion. Source-level conversion may retain uniforms that an OpenGL linker
 * would optimize away and that therefore have no JSON-managed Uniform object;
 * those fields receive zero-initialized backing storage instead of an invalid
 * native pointer.
 */
public final class EffectUniformBindings implements AutoCloseable {
    private final List<ByteBuffer> fallbackUniformBuffers = new ArrayList<>();

    public void bind(UBO ubo, Map<String, Uniform> uniformMap) {
        this.close();

        try {
            for(Field field : ubo.getFields()) {
                Uniform uniform = uniformMap.get(field.getName());
                ByteBuffer byteBuffer;

                if(uniform == null) {
                    // Minecraft creates Uniform objects for entries declared in
                    // shader/program JSON. Keep additional GLSL fields in the Vulkan
                    // UBO so source layout stays compatible, but give them OpenGL's
                    // linked-program default value: all zeroes.
                    byteBuffer = MemoryUtil.memCalloc(field.getSize() * Integer.BYTES);
                    this.fallbackUniformBuffers.add(byteBuffer);
                }
                else if(uniform.getType() <= 3) {
                    byteBuffer = MemoryUtil.memByteBuffer(uniform.getIntBuffer());
                }
                else if(uniform.getType() <= 10) {
                    byteBuffer = MemoryUtil.memByteBuffer(uniform.getFloatBuffer());
                }
                else {
                    throw new RuntimeException("out of bounds value for uniform " + uniform);
                }

                MappedBuffer mappedBuffer = MappedBuffer.createFromBuffer(byteBuffer);
                field.setSupplier(() -> mappedBuffer);
            }
        } catch (Throwable throwable) {
            this.close();
            throw throwable;
        }
    }

    public int fallbackBufferCount() {
        return this.fallbackUniformBuffers.size();
    }

    @Override
    public void close() {
        for(ByteBuffer buffer : this.fallbackUniformBuffers) {
            MemoryUtil.memFree(buffer);
        }
        this.fallbackUniformBuffers.clear();
    }
}
