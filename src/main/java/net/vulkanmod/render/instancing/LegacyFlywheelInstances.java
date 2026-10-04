package net.vulkanmod.render.instancing;

import net.vulkanmod.vulkan.shader.InstanceVertexFormat;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import org.lwjgl.system.MemoryUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.util.ArrayList;

/** Actual optional 0.6 Instancer/ModelData bridge; no GL writer or backend availability change. */
public final class LegacyFlywheelInstances implements AutoCloseable {
    public static final int STRIDE = 108;
    private final Class<?> dataType;
    private final Object owner;
    private final InstanceGroup<Object> group;
    private final Access access;

    public LegacyFlywheelInstances(ClassLoader loader) throws ReflectiveOperationException {
        dataType = Class.forName("com.jozufozu.flywheel.core.materials.model.ModelData", false, loader);
        Class<?> ownerType = Class.forName("com.jozufozu.flywheel.api.Instancer", false, loader);
        access = new Access(dataType, ownerType);
        owner = Proxy.newProxyInstance(loader, new Class<?>[]{ownerType}, (proxy, method, args) -> switch(method.getName()) {
            case "createInstance" -> createInstance();
            case "stealInstance" -> { stealInstance(args[0]); yield null; }
            case "notifyDirty", "notifyRemoval" -> null; // Scan actual owner/removal/dirty state at publication.
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            case "toString" -> "Vulkan legacy transformed instances";
            default -> {
                if(method.isDefault()) yield InvocationHandler.invokeDefault(proxy, method, args);
                throw new UnsupportedOperationException("Unqualified Instancer method: " + method);
            }
        });
        group = new InstanceGroup<>(STRIDE, owner, access);
    }

    public Object createInstance() throws ReflectiveOperationException {
        Object data = dataType.getConstructor().newInstance();
        group.add(data); return data;
    }
    public void stealInstance(Object data) {
        if(data.getClass() != dataType) throw new UnsupportedOperationException("Only exact legacy ModelData is qualified");
        group.add(data);
    }
    public ByteBuffer snapshot() { return group.snapshot(); }
    /** Material/engine caller must qualify shading/lightmap and world ownership before using this draw path. */
    public void draw(SharedModelBuffer mesh, GraphicsPipeline pipeline) {
        RenderSystem.assertOnRenderThread();
        if(pipeline.getVertexStride() != ModelGeometry.STRIDE || !format(5).equals(pipeline.getInstanceFormat()))
            throw new IllegalArgumentException("Pipeline does not match legacy block/transformed data");
        ByteBuffer snapshot = snapshot();
        if(!snapshot.hasRemaining()) return;
        ByteBuffer nativeData = MemoryUtil.memAlloc(snapshot.remaining());
        try {
            nativeData.put(snapshot).flip();
            Renderer.getDrawer().drawIndexedInstanced(pipeline, mesh.vertices(), nativeData, mesh.indices(),
                    mesh.indexType(), mesh.geometry().vertexCount(), mesh.geometry().indexCount(),
                    0, nativeData.remaining()/STRIDE);
        } finally { MemoryUtil.memFree(nativeData); }
    }
    public void draw(SharedModelBuffer mesh, LegacyFlywheelPipeline pipeline) {
        RenderSystem.assertOnRenderThread();
        ByteBuffer snapshot=snapshot();
        if(!snapshot.hasRemaining()) return;
        ByteBuffer nativeData=MemoryUtil.memAlloc(snapshot.remaining());
        try {
            nativeData.put(snapshot).flip();
            pipeline.draw(mesh,nativeData,0,nativeData.remaining()/STRIDE);
        } finally { MemoryUtil.memFree(nativeData); }
    }
    public Object owner() { return owner; }
    public void clearForOriginShift() { group.clear(); }
    @Override public void close() { group.close(); }

    /** Matches BasicWriterUnsafe's wrapped shifted light bytes, fetched as normalized bytes by Vulkan. */
    public static InstanceVertexFormat format(int firstLocation) {
        var attributes = new ArrayList<InstanceVertexFormat.Attribute>();
        for(int i=0; i<4; i++) attributes.add(new InstanceVertexFormat.Attribute(firstLocation+i, InstanceVertexFormat.Format.FLOAT4, 8+i*16));
        for(int i=0; i<3; i++) attributes.add(new InstanceVertexFormat.Attribute(firstLocation+4+i, InstanceVertexFormat.Format.FLOAT3, 72+i*12));
        attributes.add(new InstanceVertexFormat.Attribute(firstLocation+7, InstanceVertexFormat.Format.UBYTE4_NORMALIZED, 4));
        attributes.add(new InstanceVertexFormat.Attribute(firstLocation+8, InstanceVertexFormat.Format.UBYTE4_NORMALIZED, 0));
        return new InstanceVertexFormat(STRIDE, attributes);
    }

    private static final class Access implements InstanceGroup.Access<Object> {
        private final Method owner, setOwner, removed, dirty, markDirty, notifyRemoval;
        private final Field block, sky, r, g, b, a, model, normal;
        Access(Class<?> data, Class<?> ownerType) throws ReflectiveOperationException {
            owner = data.getMethod("getOwner"); setOwner = data.getMethod("setOwner",ownerType);
            removed = data.getMethod("isRemoved"); dirty = data.getMethod("checkDirtyAndClear");
            markDirty = data.getMethod("markDirty"); notifyRemoval = ownerType.getMethod("notifyRemoval");
            block = data.getField("blockLight"); sky = data.getField("skyLight");
            r = data.getField("r"); g = data.getField("g"); b = data.getField("b"); a = data.getField("a");
            model = data.getField("model"); normal = data.getField("normal");
        }
        public Object owner(Object data) { return invoke(owner,data); }
        public void setOwner(Object data,Object value) { invoke(setOwner,data,value); }
        public boolean removed(Object data) { return (boolean)invoke(removed,data); }
        public boolean consumeDirty(Object data) { return (boolean)invoke(dirty,data); }
        public void markDirty(Object data) { invoke(markDirty,data); }
        public void notifyRemoval(Object value) { invoke(notifyRemoval,value); }
        public void write(Object data,ByteBuffer out) {
            try {
                out.put(0,(byte)(block.getByte(data)<<4)); out.put(1,(byte)(sky.getByte(data)<<4));
                out.put(2,(byte)0); out.put(3,(byte)0);
                out.put(4,r.getByte(data)); out.put(5,g.getByte(data)); out.put(6,b.getByte(data)); out.put(7,a.getByte(data));
                // JOML's Unsafe ByteBuffer writer assumes a direct buffer. CPU snapshots are heap owned.
                Matrix4f m = (Matrix4f)model.get(data);
                out.position(8);
                out.putFloat(m.m00()).putFloat(m.m01()).putFloat(m.m02()).putFloat(m.m03());
                out.putFloat(m.m10()).putFloat(m.m11()).putFloat(m.m12()).putFloat(m.m13());
                out.putFloat(m.m20()).putFloat(m.m21()).putFloat(m.m22()).putFloat(m.m23());
                out.putFloat(m.m30()).putFloat(m.m31()).putFloat(m.m32()).putFloat(m.m33());
                Matrix3f n = (Matrix3f)normal.get(data);
                out.putFloat(n.m00()).putFloat(n.m01()).putFloat(n.m02());
                out.putFloat(n.m10()).putFloat(n.m11()).putFloat(n.m12());
                out.putFloat(n.m20()).putFloat(n.m21()).putFloat(n.m22());
                for(int offset=8; offset<STRIDE; offset+=4)
                    if(!Float.isFinite(out.getFloat(offset))) throw new IllegalArgumentException("Nonfinite transformed instance");
            } catch(IllegalAccessException failure) { throw new IllegalStateException("Legacy data became inaccessible",failure); }
        }
        private static Object invoke(Method method,Object target,Object... args) {
            try { return method.invoke(target,args); }
            catch(InvocationTargetException failure) { throw new IllegalStateException("Legacy instance callback failed",failure.getCause()); }
            catch(ReflectiveOperationException failure) { throw new IllegalStateException("Legacy instance API changed",failure); }
        }
    }
}
