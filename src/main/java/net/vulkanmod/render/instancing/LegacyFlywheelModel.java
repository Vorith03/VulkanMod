package net.vulkanmod.render.instancing;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.BitSet;
import java.util.Optional;
import org.lwjgl.system.MemoryUtil;

/** Optional pinned 0.6 BlockModel CPU reader. Never calls createEBO, GL pools, writers or deletion. */
public final class LegacyFlywheelModel {
    private static final String BLOCK_MODEL = "com.jozufozu.flywheel.core.model.BlockModel";
    private LegacyFlywheelModel() {}

    /**
     * Consume a newly supplied, live CPU-only BlockModel. Unsupported ownership is
     * left untouched for the fallback owner. Qualified allocations are released on
     * both successful import and invalid geometry, without model.delete()/GL calls.
     */
    public static Optional<ModelGeometry> takeOwned(Object model) {
        return takeOwned(model, geometry -> true);
    }

    /** Declined geometry keeps its source allocations live for another renderer. */
    public static Optional<ModelGeometry> takeOwned(Object model, java.util.function.Predicate<ModelGeometry> admission) {
        java.util.Objects.requireNonNull(admission);
        if(model == null || !model.getClass().getName().equals(BLOCK_MODEL)) return Optional.empty();
        final ByteBuffer vertices, indices;
        try {
            Object reader = inheritedField(model, "reader");
            String name = reader.getClass().getName();
            String known = "com.jozufozu.flywheel.core.vertex.BlockVertexListUnsafe";
            if(!name.equals(known) && !name.equals(known + "$Shaded")) return Optional.empty();
            vertices = (ByteBuffer)inheritedField(reader, "contents");
            Object supplier = field(model, "eboSupplier");
            String supplierName = supplier.getClass().getName();
            if(supplier.getClass().isSynthetic() && supplierName.startsWith(BLOCK_MODEL + "$$Lambda$")) {
                indices = null;
            } else if(supplierName.equals(BLOCK_MODEL + "$BufferEBOSupplier")
                    && (int)field(supplier, "eboName") == -1) {
                indices = (ByteBuffer)field(supplier, "indexBuffer");
            } else return Optional.empty();
            if(!vertices.isDirect() || (indices != null && !indices.isDirect())) return Optional.empty();
        } catch(ReflectiveOperationException failure) {
            return Optional.empty(); // API ownership cannot be qualified; fallback retains the source.
        }
        boolean release = true;
        try {
            ModelGeometry geometry = importModel(model);
            if(!admission.test(geometry)) { release = false; return Optional.empty(); }
            return Optional.of(geometry);
        }
        finally {
            if(release) { MemoryUtil.memFree(vertices); MemoryUtil.memFree(indices); }
        }
    }

    public static ModelGeometry importModel(Object model) {
        if(!model.getClass().getName().equals(BLOCK_MODEL))
            throw new UnsupportedOperationException("Unqualified Flywheel model: " + model.getClass().getName());
        try {
            ClassLoader loader = model.getClass().getClassLoader();
            Class<?> api = Class.forName("com.jozufozu.flywheel.core.model.Model", false, loader);
            Class<?> vertexApi = Class.forName("com.jozufozu.flywheel.api.vertex.VertexList", false, loader);
            Object reader = api.getMethod("getReader").invoke(model);
            int count = (int)api.getMethod("vertexCount").invoke(model);
            if(count < 0 || (long)count * ModelGeometry.STRIDE > ModelGeometry.MAX_BYTES
                    || count != (int)vertexApi.getMethod("getVertexCount").invoke(reader))
                throw new IllegalArgumentException("Invalid Flywheel vertex count");
            String readerName = reader.getClass().getName();
            String unsafeReader = "com.jozufozu.flywheel.core.vertex.BlockVertexListUnsafe";
            if(!readerName.equals(unsafeReader) && !readerName.equals(unsafeReader + "$Shaded"))
                throw new UnsupportedOperationException("Unqualified Flywheel vertex reader");
            ByteBuffer source = (ByteBuffer)inheritedField(reader,"contents");
            if((long)count * ModelGeometry.STRIDE > source.remaining())
                throw new IllegalArgumentException("Flywheel unsafe reader exceeds its CPU allocation");
            int[] indices = readIndices(model, count);
            var vertices = ByteBuffer.allocate(count * ModelGeometry.STRIDE).order(ByteOrder.nativeOrder());
            var unshaded = new BitSet(count);
            Class<?> shaded = Class.forName("com.jozufozu.flywheel.api.vertex.ShadedVertexList", false, loader);
            String[] names = {"getX","getY","getZ","getR","getG","getB","getA","getU","getV","getLight","getNX","getNY","getNZ"};
            Method[] read = new Method[names.length];
            for(int i=0; i<names.length; i++) read[i] = vertexApi.getMethod(names[i], int.class);
            for(int v=0; v<count; v++) {
                for(int i=0; i<3; i++) vertices.putFloat(finite((float)read[i].invoke(reader,v)));
                for(int i=3; i<7; i++) vertices.put((byte)read[i].invoke(reader,v));
                for(int i=7; i<9; i++) vertices.putFloat(finite((float)read[i].invoke(reader,v)));
                vertices.putInt((int)read[9].invoke(reader,v));
                for(int i=10; i<13; i++) {
                    float normal = finite((float)read[i].invoke(reader,v));
                    if(normal < -1 || normal > 1) throw new IllegalArgumentException("Invalid model normal");
                    vertices.put((byte)Math.round(normal * 127));
                }
                vertices.put((byte)0);
                if(shaded.isInstance(reader) && !(boolean)shaded.getMethod("isShaded",int.class).invoke(reader,v)) unshaded.set(v);
            }
            return new ModelGeometry(vertices.array(), indices, unshaded);
        } catch(InvocationTargetException failure) {
            throw new IllegalStateException("Flywheel CPU reader failed", failure.getCause());
        } catch(ReflectiveOperationException failure) {
            throw new UnsupportedOperationException("Unqualified Flywheel model API", failure);
        }
    }

    private static int[] readIndices(Object model, int vertices) throws ReflectiveOperationException {
        Object supplier = field(model, "eboSupplier");
        String name = supplier.getClass().getName();
        if(supplier.getClass().isSynthetic() && name.startsWith(BLOCK_MODEL + "$$Lambda$"))
            return ModelGeometry.quadIndices(vertices);
        if(!name.equals(BLOCK_MODEL + "$BufferEBOSupplier"))
            throw new UnsupportedOperationException("Unqualified Flywheel index supplier");
        if((int)field(supplier,"eboName") != -1)
            throw new UnsupportedOperationException("Flywheel CPU indices already released to GL");
        int count = (int)field(supplier,"indexCount");
        String type = ((Enum<?>)field(supplier,"indexType")).name();
        int bytes = switch(type) {
            case "SHORT" -> 2; case "INT" -> 4;
            default -> throw new UnsupportedOperationException("Unqualified Flywheel index type: " + type);
        };
        ByteBuffer input = ((ByteBuffer)field(supplier,"indexBuffer")).duplicate().clear().order(ByteOrder.nativeOrder());
        // BlockModel copies the original index buffer's capacity, independently of its current cursor.
        if(count < 0 || count % 3 != 0 || (long)count * bytes > input.capacity()
                || (long)count * 4 > ModelGeometry.MAX_BYTES)
            throw new IllegalArgumentException("Invalid Flywheel index span");
        int[] indices = new int[count];
        for(int i=0; i<count; i++) indices[i] = bytes == 2 ? Short.toUnsignedInt(input.getShort(i*2)) : input.getInt(i*4);
        return indices;
    }

    private static Object inheritedField(Object object, String name) throws ReflectiveOperationException {
        for(Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                if(!field.trySetAccessible()) throw new UnsupportedOperationException("Flywheel CPU field is inaccessible: " + name);
                return field.get(object);
            } catch(NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }

    private static Object field(Object object, String name) throws ReflectiveOperationException {
        Field field = object.getClass().getDeclaredField(name);
        if(!field.trySetAccessible()) throw new UnsupportedOperationException("Flywheel CPU field is inaccessible: " + name);
        return field.get(object);
    }
    private static float finite(float value) {
        if(!Float.isFinite(value)) throw new IllegalArgumentException("Nonfinite model value");
        return value;
    }
}
