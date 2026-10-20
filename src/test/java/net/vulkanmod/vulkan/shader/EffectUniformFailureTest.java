package net.vulkanmod.vulkan.shader;

import net.vulkanmod.vulkan.shader.layout.AlignedStruct;
import net.vulkanmod.vulkan.shader.layout.Field;
import net.vulkanmod.vulkan.shader.descriptor.UBO;
import org.lwjgl.system.MemoryUtil;
import java.nio.ByteBuffer;
import java.util.IdentityHashMap;
import java.util.Map;

/** Inject failure after one fallback allocation and count actual releases. */
public final class EffectUniformFailureTest {
    public static void main(String[] args) {
        var live = new IdentityHashMap<ByteBuffer, Boolean>();
        int[] counts = new int[2];
        RuntimeException failure = new RuntimeException("injected allocation failure");
        EffectUniformBindings bindings = new EffectUniformBindings(new EffectUniformBindings.FallbackAllocator() {
            public ByteBuffer allocate(int size) {
                if (++counts[0] == 2) throw failure;
                ByteBuffer buffer = MemoryUtil.memCalloc(size); live.put(buffer, true); return buffer;
            }
            public void release(ByteBuffer buffer) {
                if (live.remove(buffer) == null) throw new AssertionError("Double/foreign release");
                counts[1]++; MemoryUtil.memFree(buffer);
            }
        });
        UBO ubo;
        try (var ignored = Field.deferDefaultSupplierBinding()) {
            var builder = new AlignedStruct.Builder();
            builder.addFieldInfo("vec4", "first"); builder.addFieldInfo("vec4", "second");
            ubo = builder.buildUBO(0, 0);
        }
        try { bindings.bind(ubo, Map.of()); throw new AssertionError("Expected failure"); }
        catch (RuntimeException actual) { if (actual != failure) throw new AssertionError("Cause lost", actual); }
        bindings.close(); bindings.close();
        if (!live.isEmpty() || counts[1] != 1 || bindings.fallbackBufferCount() != 0)
            throw new AssertionError("Partial bind leaked or double-released");
        ByteBuffer destination = MemoryUtil.memAlloc(ubo.getSize());
        try {
            for (int i=0;i<destination.capacity();i++) destination.put(i,(byte)0x5a);
            try { ubo.update(MemoryUtil.memAddress(destination)); throw new AssertionError("Stale supplier accepted"); }
            catch (IllegalStateException expected) { }
            for (int i=0;i<destination.capacity();i++)
                if (destination.get(i)!=(byte)0x5a) throw new AssertionError("Partial stale update");
        } finally { MemoryUtil.memFree(destination); }
        System.out.println("Uniform failure/release accounting passed");
    }
}
