package net.vulkanmod.render.instancing;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraftforge.client.model.lighting.QuadLighter;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.function.Supplier;

/**
 * Actual CPU vertex-consumer fallback for Batched structs and owned built-in quad models.
 * Uses the pinned type's numeric Params transform, never its GL writer or model.delete.
 * Unqualified types/models are handed to a separately owned fallback, which must render them.
 */
public final class LegacyFlywheelCpuFallback implements LegacyFlywheelMaterials.Fallback {
    private static final long MAX_BYTES = 256L*1024*1024;
    private final ClassLoader loader;
    private final Class<?> batched, struct, dataApi, instancerApi, paramsType;
    private final Method create, transform, defaults, owner, setOwner, removed, dirty, markDirty, notifyRemoval;
    private final LegacyFlywheelMaterials.Fallback unsupported;
    private final ArrayList<Entry> entries = new ArrayList<>();
    private long geometryBytes;
    private boolean closed;

    public LegacyFlywheelCpuFallback(ClassLoader loader, LegacyFlywheelMaterials.Fallback unsupported)
            throws ReflectiveOperationException {
        this.loader = loader; this.unsupported = java.util.Objects.requireNonNull(unsupported);
        batched = Class.forName("com.jozufozu.flywheel.api.struct.Batched",false,loader);
        struct = Class.forName("com.jozufozu.flywheel.api.struct.StructType",false,loader);
        dataApi = Class.forName("com.jozufozu.flywheel.api.InstanceData",false,loader);
        instancerApi = Class.forName("com.jozufozu.flywheel.api.Instancer",false,loader);
        paramsType = Class.forName("com.jozufozu.flywheel.core.model.ModelTransformer$Params",false,loader);
        create = struct.getMethod("create"); transform = batched.getMethod("transform",Object.class,paramsType);
        defaults = paramsType.getMethod("loadDefault");
        owner = dataApi.getMethod("getOwner"); setOwner = dataApi.getMethod("setOwner",instancerApi);
        removed = dataApi.getMethod("isRemoved"); dirty = dataApi.getMethod("checkDirtyAndClear");
        markDirty = dataApi.getMethod("markDirty"); notifyRemoval = instancerApi.getMethod("notifyRemoval");
    }

    @Override public Object model(Object layer, Object state, Object spec, Object key, Supplier<?> supplier) {
        requireOpen();
        if(!batched.isInstance(spec) || !(state instanceof RenderType type) || type.mode()!=VertexFormat.Mode.QUADS
                || (type.format()!=DefaultVertexFormat.BLOCK && type.format()!=DefaultVertexFormat.NEW_ENTITY))
            return unsupported.model(layer,state,spec,key,supplier);
        Object source = java.util.Objects.requireNonNull(supplier.get());
        var imported = LegacyFlywheelModel.takeOwned(source, geometry -> {
            if(entries.size() >= 4096 || (long)geometry.vertexCount()*32+(long)geometry.indexCount()*4 > MAX_BYTES-geometryBytes)
                return false;
            if(geometry.vertexCount()%4 != 0) return false;
            int[] sequential=ModelGeometry.quadIndices(geometry.vertexCount());
            if(geometry.indexCount()!=sequential.length) return false;
            ByteBuffer indices=geometry.indices();
            for(int index : sequential)
                if(index != (geometry.indexBytes()==2 ? Short.toUnsignedInt(indices.getShort()) : indices.getInt())) return false;
            return true;
        });
        if(imported.isEmpty()) {
            boolean[] consumed = {false};
            return unsupported.model(layer,state,spec,key,() -> {
                if(consumed[0]) throw new IllegalStateException("Unsupported source consumed twice");
                consumed[0] = true; return source;
            });
        }
        ModelGeometry geometry = imported.get();
        long bytes = (long)geometry.vertexCount()*32 + (long)geometry.indexCount()*4;
        try {
            Entry entry = new Entry(layer,state,spec,geometry); entries.add(entry); geometryBytes += bytes; return entry.ownerProxy;
        } catch(ReflectiveOperationException failure) { throw new IllegalStateException("Legacy CPU transform API changed",failure); }
    }

    private final class Entry implements InstanceGroup.Access<Object> {
        final Object layer, state, spec, ownerProxy, params;
        final ModelGeometry geometry;
        final InstanceGroup<Object> members;
        Class<?> instanceType;
        final Field model, normal, useColor, r, g, b, a, useLight, light, overlay, uvShift;
        Entry(Object layer, Object state, Object spec, ModelGeometry geometry) throws ReflectiveOperationException {
            this.layer=layer; this.state=state; this.spec=spec; this.geometry=geometry;
            params=paramsType.getConstructor().newInstance();
            model=paramsType.getField("model"); normal=paramsType.getField("normal");
            useColor=paramsType.getField("useParamColor"); useLight=paramsType.getField("useParamLight");
            r=paramsType.getField("r"); g=paramsType.getField("g"); b=paramsType.getField("b"); a=paramsType.getField("a");
            light=paramsType.getField("packedLightCoords"); overlay=paramsType.getField("overlay"); uvShift=paramsType.getField("spriteShiftFunc");
            ownerProxy=Proxy.newProxyInstance(loader,new Class<?>[]{instancerApi},(self,method,args) -> switch(method.getName()) {
                case "createInstance" -> newInstance();
                case "stealInstance" -> { takeInstance(args[0]); yield null; }
                case "notifyDirty", "notifyRemoval" -> null; // Data update callbacks may run on Flywheel workers.
                case "equals" -> self==args[0]; case "hashCode" -> System.identityHashCode(self);
                case "toString" -> "Vulkan CPU Flywheel instancer";
                default -> { if(method.isDefault()) yield InvocationHandler.invokeDefault(self,method,args); throw new UnsupportedOperationException(method.toString()); }
            });
            members=new InstanceGroup<>(108,ownerProxy,this); // Bounded membership; no serialized CPU fallback records.
            Class<?> declared=spec.getClass().getMethod("create").getReturnType();
            if(dataApi.isAssignableFrom(declared)) instanceType=declared;
        }
        Object newInstance() {
            requireOpen(); Object data=invoke(create,spec);
            if(!dataApi.isInstance(data) || (instanceType!=null && !instanceType.isInstance(data)))
                throw new IllegalArgumentException("Invalid Batched instance data");
            if(instanceType==null) instanceType=data.getClass();
            members.add(data); return data;
        }
        void takeInstance(Object data) {
            requireOpen();
            if(instanceType==null || !instanceType.isInstance(data)) throw new IllegalArgumentException("Unqualified instance transfer");
            members.add(data);
        }
        public Object owner(Object data) { return invoke(owner,data); }
        public void setOwner(Object data,Object value) { invoke(setOwner,data,value); }
        public boolean removed(Object data) { return (boolean)invoke(removed,data); }
        public boolean consumeDirty(Object data) { return (boolean)invoke(dirty,data); }
        public void markDirty(Object data) { invoke(markDirty,data); }
        public void notifyRemoval(Object value) { invoke(notifyRemoval,value); }
        public void write(Object data,ByteBuffer out) { throw new UnsupportedOperationException("CPU fallback has no GPU record encoder"); }
        void render(PoseStack stack, VertexConsumer out, boolean constantAmbientLight) {
            ByteBuffer vertices=geometry.vertices();
            members.forEachLive(data -> {
                invoke(defaults,params); invoke(transform,spec,data,params);
                try {
                    Matrix4f matrix=new Matrix4f(stack.last().pose()).mul((Matrix4f)model.get(params));
                    Matrix3f normals=(Matrix3f)normal.get(params); // Legacy CPU path omits stack normals by default.
                    var position=new Vector4f(); var direction=new Vector3f();
                    Object shift=uvShift.get(params);
                    Method shiftMethod=shift == null ? null : Class.forName("com.jozufozu.flywheel.core.model.ModelTransformer$SpriteShiftFunc",false,loader)
                            .getMethod("shift",VertexConsumer.class,float.class,float.class);
                    for(int v=0; v<geometry.vertexCount(); v++) {
                        int offset=v*32;
                        position.set(vertices.getFloat(offset),vertices.getFloat(offset+4),vertices.getFloat(offset+8),1).mul(matrix);
                        direction.set(vertices.get(offset+28)/127f,vertices.get(offset+29)/127f,vertices.get(offset+30)/127f).mul(normals).normalize();
                        float shade=((RenderType)state).format()!=DefaultVertexFormat.BLOCK ? 1f : geometry.isShaded(v) ? QuadLighter.calculateShade(direction.x,direction.y,direction.z,constantAmbientLight)
                                : constantAmbientLight ? 0.9f : 1f;
                        int cr=useColor.getBoolean(params) ? r.getInt(params) : Byte.toUnsignedInt(vertices.get(offset+12));
                        int cg=useColor.getBoolean(params) ? g.getInt(params) : Byte.toUnsignedInt(vertices.get(offset+13));
                        int cb=useColor.getBoolean(params) ? b.getInt(params) : Byte.toUnsignedInt(vertices.get(offset+14));
                        int ca=useColor.getBoolean(params) ? a.getInt(params) : Byte.toUnsignedInt(vertices.get(offset+15));
                        out.vertex(position.x,position.y,position.z).color(color(cr,shade),color(cg,shade),color(cb,shade),ca);
                        float u=vertices.getFloat(offset+16), vv=vertices.getFloat(offset+20);
                        if(shiftMethod == null) out.uv(u,vv); else invoke(shiftMethod,shift,out,u,vv);
                        out.overlayCoords(overlay.getInt(params));
                        out.uv2(useLight.getBoolean(params) ? light.getInt(params) : vertices.getInt(offset+24));
                        out.normal(direction.x,direction.y,direction.z); out.endVertex();
                    }
                } catch(ReflectiveOperationException failure) { throw new IllegalStateException("Legacy transform Params changed",failure); }
            });
        }
    }
    /** Engine/event owner supplies camera/origin transforms and a consumer for this exact state. */
    public void render(Object layer, Object state, PoseStack stack, VertexConsumer consumer, boolean constantAmbientLight) {
        requireOpen();
        for(Entry entry : entries) if(entry.layer==layer && entry.state==state) entry.render(stack,consumer,constantAmbientLight);
    }
    public void visitStates(Object layer, java.util.function.Consumer<Object> visitor) {
        requireOpen(); var visited=new java.util.IdentityHashMap<Object,Boolean>();
        for(Entry entry : entries) if(entry.layer==layer && visited.put(entry.state,true)==null) visitor.accept(entry.state);
    }
    @Override public void clearForOriginShift(BlockPos origin) {
        requireOpen(); entries.forEach(entry -> entry.members.clear()); unsupported.clearForOriginShift(origin);
    }
    @Override public void close() {
        RenderSystem.assertOnRenderThread(); if(closed) return; closed=true;
        entries.forEach(entry -> entry.members.close()); entries.clear(); geometryBytes=0; unsupported.close();
    }
    private void requireOpen() { RenderSystem.assertOnRenderThread(); if(closed) throw new IllegalStateException("CPU fallback is closed"); }
    private static int color(int value,float shade) { return Math.max(0,Math.min(255,(int)(value*shade))); }
    private static Object invoke(Method method,Object self,Object... args) {
        try { return method.invoke(self,args); }
        catch(InvocationTargetException failure) { throw new IllegalStateException("Legacy CPU callback failed",failure.getCause()); }
        catch(ReflectiveOperationException failure) { throw new IllegalStateException("Legacy CPU API changed",failure); }
    }
}
