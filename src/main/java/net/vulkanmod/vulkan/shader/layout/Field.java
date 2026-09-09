package net.vulkanmod.vulkan.shader.layout;

import net.vulkanmod.vulkan.util.MappedBuffer;
import org.lwjgl.system.MemoryUtil;

import java.util.function.Supplier;

public abstract class Field {
    private static final ThreadLocal<Boolean> DEFER_DEFAULT_SUPPLIER_BINDING = ThreadLocal.withInitial(() -> false);

    protected Supplier<MappedBuffer> values;

    FieldInfo fieldInfo;
    protected long offset;
    protected int size;

    Field(FieldInfo fieldInfo) {
        this.fieldInfo = fieldInfo;
        this.offset = fieldInfo.offset * 4L;
        this.size = fieldInfo.size * 4;
        if(!DEFER_DEFAULT_SUPPLIER_BINDING.get()) {
            this.setSupplier();
        }
    }

    abstract void setSupplier();

    public void setSupplier(Supplier<MappedBuffer> supplier) {
        this.values = supplier;
    }

    public String getName() {
        return this.fieldInfo.name;
    }

    void update(long ptr) {
        if(this.values == null) {
            throw new IllegalStateException("No supplier bound for uniform field: " + this.fieldInfo.name);
        }

        MappedBuffer src = values.get();

        MemoryUtil.memCopy(src.ptr, ptr + this.offset, this.size);
    }

    /**
     * EffectInstance uniforms are owned by Minecraft's EffectInstance and are
     * wired to VulkanMod after GLSL conversion. Defer the normal global-uniform
     * lookup while that UBO is being constructed so mod-defined post-effect
     * uniforms (InSize, OutSize, Time, custom fields, etc.) do not fail before
     * EffectInstance has a chance to bind their backing buffers.
     */
    public static DefaultSupplierBindingScope deferDefaultSupplierBinding() {
        boolean previous = DEFER_DEFAULT_SUPPLIER_BINDING.get();
        DEFER_DEFAULT_SUPPLIER_BINDING.set(true);
        return new DefaultSupplierBindingScope(previous);
    }

    public static final class DefaultSupplierBindingScope implements AutoCloseable {
        private final boolean previous;
        private boolean closed;

        private DefaultSupplierBindingScope(boolean previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if(this.closed) {
                return;
            }

            if(this.previous) {
                DEFER_DEFAULT_SUPPLIER_BINDING.set(true);
            }
            else {
                DEFER_DEFAULT_SUPPLIER_BINDING.remove();
            }
            this.closed = true;
        }
    }

    public static Field createField(FieldInfo info) {
        return switch (info.type) {
            case "mat4" -> new Mat4f(info);
            case "vec4" -> new Vec4f(info);
            case "vec3" -> new Vec3f(info);
            case "vec2" -> new Vec2f(info);
            case "float" -> new Vec1f(info);
            case "int" -> new Vec1i(info);
            default -> throw new RuntimeException("not admitted type: " + info.type);
        };
    }

    public int getOffset() {
        return fieldInfo.offset;
    }

    public int getSize() { return fieldInfo.size; }

    //TODO
    public static FieldInfo createFieldInfo(String type, String name, int count) {
        return switch (type) {
            case "matrix4x4" -> new FieldInfo("mat4", name, 4, 16);
            case "float" -> switch (count) {
                case 4 -> new FieldInfo("vec4", name, 4, 4);
                case 3 -> new FieldInfo("vec3", name, 4, 3);
                // std140 vec2 members have an 8-byte (two-scalar) base alignment.
                case 2 -> new FieldInfo("vec2", name, 2, 2);
                case 1 -> new FieldInfo("float", name, 1, 1);

                default -> throw new IllegalStateException("Unexpected value: " + count);
            };
            case "int" -> new FieldInfo("int", name, 1, 1);
            default -> throw new RuntimeException("not admitted type..");
        };
    }

    public static FieldInfo createFieldInfo(String type, String name) {
        return switch (type) {
            case "mat4" -> new FieldInfo(type, name, 4, 16);
            case "mat3" -> new FieldInfo(type, name, 4, 9);

            case "vec4" -> new FieldInfo(type, name, 4, 4);
            case "vec3" -> new FieldInfo(type, name, 4, 3);
            case "vec2" -> new FieldInfo(type, name, 2, 2);

            case "float", "int" -> new FieldInfo(type, name, 1, 1);

            default -> throw new RuntimeException("not admitted type: " + type);
        };
    }

    public static class FieldInfo {
        final String type;
        final String name;
        final int align;
        final int size;
        int offset;

        FieldInfo(String type, String name, int align, int size) {
            this.type = type;
            this.name = name;
            this.align = align;
            this.size = size;
        }

        int getSizeBytes() { return 4 * this.size; }

        int computeAlignmentOffset(int builderOffset) {
            return this.offset = builderOffset + ((align - (builderOffset % align)) % align);
        }
    }
}
