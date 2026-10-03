package net.vulkanmod.render.instancing;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.vulkanmod.Initializer;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/** Actual Batched/ModelType/OrientedType transforms emitted into Minecraft's transformed BufferBuilder. */
public final class LegacyFlywheelCpuSmokeTest {
    private LegacyFlywheelCpuSmokeTest() {}
    public static void verify(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> api=Class.forName("com.jozufozu.flywheel.api.Instancer",false,loader);
        Class<?> dataApi=Class.forName("com.jozufozu.flywheel.api.InstanceData",false,loader);
        Class<?> layerApi=Class.forName("com.jozufozu.flywheel.backend.RenderLayer",false,loader);
        Object layer=layerApi.getField("SOLID").get(null);
        var create=api.getMethod("createInstance"); var steal=api.getMethod("stealInstance",dataApi);
        var unsupported=new Unsupported(loader);
        var fallback=new LegacyFlywheelCpuFallback(loader,unsupported);
        Object spec=Class.forName("com.jozufozu.flywheel.core.materials.model.ModelType",false,loader).getConstructor().newInstance();
        try {
            Object first=fallback.model(layer,RenderType.solid(),spec,"first",() -> block(loader,false));
            Object second=fallback.model(layer,RenderType.solid(),spec,"second",() -> block(loader,false));
            Object data=create.invoke(first); Class<?> type=data.getClass();
            ((Matrix4f)type.getField("model").get(data)).translation(12,34,56);
            setBasic(data);
            // InstanceWorld has already translated event.stack by -camera. Engine adds +origin.
            var stack=new PoseStack(); stack.translate(-10,-30,-50); stack.translate(100,200,300);
            ByteBuffer vertices=render(fallback,layer,stack,false);
            check(vertices.remaining()==128,"CPU quad count changed");
            near(vertices.getFloat(0),102); near(vertices.getFloat(4),204); near(vertices.getFloat(8),306);
            check(Byte.toUnsignedInt(vertices.get(12))==100 && Byte.toUnsignedInt(vertices.get(13))==60
                    && Byte.toUnsignedInt(vertices.get(14))==20 && Byte.toUnsignedInt(vertices.get(15))==128,"Diffuse/alpha output changed");
            check(Byte.toUnsignedInt(vertices.get(2*32+12))==200,"Unshaded model vertex darkened");
            near(vertices.getFloat(16),0.25f); near(vertices.getFloat(20),0.75f);
            check(vertices.getInt(24)==0x00F00070 && vertices.get(29)==-127,"Packed light/normal changed");
            ByteBuffer nether=render(fallback,layer,stack,true);
            check(Byte.toUnsignedInt(nether.get(12))==180 && Byte.toUnsignedInt(nether.get(2*32+12))==180,"Constant ambient light shade changed");
            steal.invoke(second,data); steal.invoke(first,data);
            check(render(fallback,layer,stack,false).remaining()==128,"Transfer-back duplicated CPU instance");
            steal.invoke(second,data);
            check(render(fallback,layer,stack,false).remaining()==128,"Transfer rendered through both owners");
            type.getMethod("delete").invoke(data);
            check(render(fallback,layer,stack,false).remaining()==0,"Removed CPU instance rendered");
            reject(() -> steal.invoke(first,data));

            Object oriented=Class.forName("com.jozufozu.flywheel.core.materials.oriented.OrientedType",false,loader).getConstructor().newInstance();
            Object orientedOwner=fallback.model(layer,RenderType.solid(),oriented,"oriented",() -> block(loader,false));
            Object orientation=create.invoke(orientedOwner); Class<?> orientedData=orientation.getClass();
            orientedData.getField("posX").setFloat(orientation,5); orientedData.getField("posY").setFloat(orientation,6);
            orientedData.getField("posZ").setFloat(orientation,7); setBasic(orientation);
            ByteBuffer orientedVertices=render(fallback,layer,new PoseStack(),false);
            near(orientedVertices.getFloat(0),5); near(orientedVertices.getFloat(4),6); near(orientedVertices.getFloat(8),7);
            reject(() -> steal.invoke(orientedOwner,create.invoke(first)));
            fallback.clearForOriginShift(new BlockPos(1,2,3));
            check(render(fallback,layer,stack,false).remaining()==0,"Origin clear retained CPU membership");

            // Non-sequential custom topology goes to its owner with source allocations still live.
            fallback.model(layer,RenderType.solid(),spec,"custom",() -> block(loader,true));
            check(unsupported.customIndices!=null && unsupported.customIndices.getShort(0)==0
                    && unsupported.customIndices.getShort(2)==3,"Custom source was freed/replaced before handoff");
            fallback.close(); fallback.close(); check(unsupported.closes==1,"CPU fallback closed twice");
            reject(() -> create.invoke(first));
        } finally { fallback.close(); }

        AtomicInteger syncs=new AtomicInteger();
        Class<?> taskApi=Class.forName("com.jozufozu.flywheel.backend.instancing.TaskEngine",false,loader);
        Object tasks=Proxy.newProxyInstance(loader,new Class<?>[]{taskApi},(self,method,args) -> {
            if(method.getName().equals("syncPoint")) { syncs.incrementAndGet(); return null; }
            if(method.getName().equals("submit")) { ((Runnable)args[0]).run(); return null; }
            throw new AssertionError("Unexpected task call");
        });
        var engine=new LegacyFlywheelEngine(loader,new Object(),9,tasks,new Unsupported(loader),false);
        Class<?> engineApi=Class.forName("com.jozufozu.flywheel.backend.instancing.Engine",false,loader);
        Object proxy=engine.engine();
        try {
            check(engineApi.isInstance(proxy),"Engine proxy lost actual API");
            Object solid=engineApi.getMethod("defaultSolid").invoke(proxy); check(solid!=null,"Engine default material route failed");
            var debug=new ArrayList<String>(); engineApi.getMethod("addDebugInfo",java.util.List.class).invoke(proxy,debug);
            check(debug.size()==2,"Engine debug route failed");
            engine.beginFrame(new Camera()); check(syncs.get()==1 && engine.origin().equals(BlockPos.ZERO),"Engine did not sync before beginFrame");
            try { engine.render(new Object(),new Object()); throw new AssertionError("Foreign task/world accepted"); }
            catch(IllegalArgumentException expected) {}
            engineApi.getMethod("delete").invoke(proxy); engineApi.getMethod("delete").invoke(proxy);
            check(syncs.get()==2,"Engine delete synchronized twice");
            reject(() -> engineApi.getMethod("defaultSolid").invoke(proxy));
        } finally { engine.close(); }
        Initializer.LOGGER.info("Flywheel CPU engine smoke passed: actual ModelType/OrientedType transforms, emitted pose/origin/color/alpha/light/UV/normal/shade vertices, owner transfer/back, removal, custom topology handoff, origin clear, actual Engine default/debug/delete and task isolation; backend remains off");
    }
    private static ByteBuffer render(LegacyFlywheelCpuFallback fallback,Object layer,PoseStack stack,boolean ambient) {
        BufferBuilder builder=new BufferBuilder(256); builder.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.BLOCK);
        fallback.render(layer,RenderType.solid(),stack,builder,ambient);
        var result=builder.end();
        try {
            ByteBuffer source=result.vertexBuffer(); ByteBuffer copy=ByteBuffer.allocate(source.remaining()).order(ByteOrder.nativeOrder());
            return copy.put(source).flip();
        } finally { result.release(); }
    }
    private static void setBasic(Object data) throws ReflectiveOperationException {
        Class<?> type=data.getClass();
        type.getField("r").setByte(data,(byte)200); type.getField("g").setByte(data,(byte)120);
        type.getField("b").setByte(data,(byte)40); type.getField("a").setByte(data,(byte)128);
        type.getField("blockLight").setByte(data,(byte)7); type.getField("skyLight").setByte(data,(byte)15);
    }
    private static Object block(ClassLoader loader,boolean custom) {
        ByteBuffer vertices=MemoryUtil.memAlloc(128), indices=MemoryUtil.memAlloc(24);
        try {
            for(int v=0;v<4;v++) {
                int at=v*32; vertices.putFloat(at,v); vertices.putFloat(at+4,0); vertices.putFloat(at+8,0);
                vertices.putInt(at+12,-1); vertices.putFloat(at+16,0.25f); vertices.putFloat(at+20,0.75f);
                vertices.putInt(at+24,0); vertices.putInt(at+28,0); vertices.put(at+29,(byte)-127);
            }
            for(int value : custom ? new int[]{0,3,2,2,1,0} : ModelGeometry.quadIndices(4)) indices.putInt(value);
            indices.flip();
            var state=new BufferBuilder.DrawState(DefaultVertexFormat.BLOCK,4,6,VertexFormat.Mode.QUADS,VertexFormat.IndexType.INT,false,!custom);
            return Class.forName("com.jozufozu.flywheel.core.model.BlockModel",false,loader)
                    .getConstructor(ByteBuffer.class,ByteBuffer.class,BufferBuilder.DrawState.class,int.class,String.class)
                    .newInstance(vertices,indices,state,2,"CPU transform oracle");
        } catch(ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
        finally { MemoryUtil.memFree(vertices); MemoryUtil.memFree(indices); }
    }
    private static final class Unsupported implements LegacyFlywheelEngine.Unsupported {
        final ClassLoader loader; final ArrayList<LegacyFlywheelInstances> owners=new ArrayList<>();
        ByteBuffer customIndices; int closes;
        Unsupported(ClassLoader loader) { this.loader=loader; }
        public Object model(Object layer,Object state,Object spec,Object key,Supplier<?> supplier) {
            Object source=supplier.get();
            ModelGeometry geometry=LegacyFlywheelModel.takeOwned(source).orElseThrow();
            customIndices=geometry.indices();
            try { var owner=new LegacyFlywheelInstances(loader); owners.add(owner); return owner.owner(); }
            catch(ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
        }
        public void clearForOriginShift(BlockPos origin) { owners.forEach(LegacyFlywheelInstances::clearForOriginShift); }
        public void render(Object tasks,Object event,BlockPos origin) { throw new AssertionError("Unqualified custom renderer must not be installed"); }
        public void close() { closes++; owners.forEach(LegacyFlywheelInstances::close); }
    }
    private interface Action { void run() throws ReflectiveOperationException; }
    private static void reject(Action task) throws ReflectiveOperationException {
        try { task.run(); }
        catch(InvocationTargetException failure) {
            if(failure.getCause() instanceof IllegalArgumentException || failure.getCause() instanceof IllegalStateException) return;
            throw failure;
        }
        throw new AssertionError("Invalid/retired CPU operation admitted");
    }
    private static void near(float actual,float expected) { check(Math.abs(actual-expected)<0.0001f,"CPU transform coordinate changed: "+actual+" != "+expected); }
    private static void check(boolean valid,String reason) { if(!valid) throw new AssertionError(reason); }
}
