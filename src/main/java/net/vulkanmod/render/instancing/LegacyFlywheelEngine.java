package net.vulkanmod.render.instancing;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Objects;

/** Callable, world-scoped legacy Engine. CPU-first dispatcher; not registered as a Flywheel backend. */
public final class LegacyFlywheelEngine implements AutoCloseable {
    public interface Unsupported extends LegacyFlywheelMaterials.Fallback {
        /** Render delegated types/models in this exact world/event, with the engine's origin. */
        void render(Object taskEngine, Object event, BlockPos origin);
    }
    private final Object world, tasks, proxy;
    private final LegacyFlywheelMaterials materials;
    private final LegacyFlywheelCpuFallback cpu;
    private final Unsupported unsupported;
    private final Method sync, eventWorld, eventLayer;
    private final Class<?> eventType;
    private final BufferBuilder builder = new BufferBuilder(256);
    private MultiBufferSource.BufferSource buffers = MultiBufferSource.immediate(builder);
    private final boolean ignoreOrigin;
    private boolean closed;

    public LegacyFlywheelEngine(ClassLoader loader, Object world, long generation, Object tasks,
                               Unsupported unsupported, boolean ignoreOrigin) throws ReflectiveOperationException {
        this.world=Objects.requireNonNull(world); this.tasks=Objects.requireNonNull(tasks);
        this.unsupported=Objects.requireNonNull(unsupported); this.ignoreOrigin=ignoreOrigin;
        Class<?> taskApi=Class.forName("com.jozufozu.flywheel.backend.instancing.TaskEngine",false,loader);
        if(!taskApi.isInstance(tasks)) throw new IllegalArgumentException("Wrong Flywheel task engine");
        sync=taskApi.getMethod("syncPoint");
        eventType=Class.forName("com.jozufozu.flywheel.event.RenderLayerEvent",false,loader);
        eventWorld=eventType.getMethod("getWorld"); eventLayer=eventType.getMethod("getLayer");
        Class<?> engineApi=Class.forName("com.jozufozu.flywheel.backend.instancing.Engine",false,loader);
        cpu=new LegacyFlywheelCpuFallback(loader,unsupported);
        materials=new LegacyFlywheelMaterials(loader,world,generation,cpu,false); // Until GPU material pixels qualify.
        proxy=Proxy.newProxyInstance(loader,new Class<?>[]{engineApi},(self,method,args) -> {
            if(method.getName().equals("delete")) { close(); return null; }
            requireOpen();
            return switch(method.getName()) {
                case "render" -> { render(args[0],args[1]); yield null; }
                case "beginFrame" -> { beginFrame((Camera)args[0]); yield null; }
                case "addDebugInfo" -> { addDebugInfo(args[0]); yield null; }
                case "equals" -> self==args[0]; case "hashCode" -> System.identityHashCode(self);
                case "toString" -> "Vulkan CPU Flywheel engine generation " + materials.generation();
                default -> invoke(method,materials.manager(),args);
            };
        });
    }
    public Object engine() { requireOpen(); return proxy; }
    public void addOriginListener(Runnable listener) { requireOpen(); materials.addOriginListener(listener); }
    public void beginFrame(Camera camera) {
        requireOpen(); invoke(sync,tasks); // Synchronize before any membership clear, not after it.
        if(ignoreOrigin) return;
        var position=camera.getPosition();
        if(!Double.isFinite(position.x) || !Double.isFinite(position.y) || !Double.isFinite(position.z))
            throw new IllegalArgumentException("Nonfinite Flywheel camera");
        BlockPos next=BlockPos.containing(position);
        BlockPos previous=origin();
        if(Math.abs((long)next.getX()-previous.getX())>100 || Math.abs((long)next.getY()-previous.getY())>100
                || Math.abs((long)next.getZ()-previous.getZ())>100) materials.shiftOrigin(next);
        else materials.shiftOrigin(previous); // Retry a failed recreation at an unchanged origin.
    }
    public BlockPos origin() {
        requireOpen();
        return materials.origin();
    }
    public void render(Object taskEngine, Object event) {
        requireOpen();
        if(taskEngine!=tasks || !eventType.isInstance(event) || invoke(eventWorld,event)!=world)
            throw new IllegalArgumentException("Flywheel engine task/world ownership mismatch");
        Object layer=invoke(eventLayer,event);
        if(layer==null) throw new UnsupportedOperationException("Crumbling dispatch is not qualified");
        invoke(sync,tasks);
        try {
            PoseStack stack=(PoseStack)eventType.getField("stack").get(event);
            PoseStack copied=new PoseStack();
            copied.last().pose().set(stack.last().pose()); copied.last().normal().set(stack.last().normal());
            BlockPos origin=origin(); copied.translate(origin.getX(),origin.getY(),origin.getZ());
            boolean ambient=((ClientLevel)world).effects().constantAmbientLight();
            cpu.visitStates(layer,state -> {
                RenderType type=(RenderType)state;
                cpu.render(layer,type,copied,buffers.getBuffer(type),ambient);
                buffers.endBatch(type); // Engine owns this BufferSource; no unrelated vanilla batches are drained.
            });
            unsupported.render(tasks,event,origin);
        } catch(ReflectiveOperationException failure) { throw new IllegalStateException("Legacy render event changed",failure); }
        catch(RuntimeException | Error failure) { resetBuffers(); throw failure; }
    }
    @SuppressWarnings("unchecked") private void addDebugInfo(Object list) {
        ((List<String>)list).add("Vulkan Flywheel CPU fallback (experimental, backend disabled)");
        ((List<String>)list).add("Origin: " + origin().toShortString());
    }
    @Override public void close() {
        RenderSystem.assertOnRenderThread(); if(closed) return;
        invoke(sync,tasks); closed=true;
        try { materials.close(); } finally { resetBuffers(); }
    }
    private void resetBuffers() { builder.discard(); buffers=MultiBufferSource.immediate(builder); }
    private void requireOpen() { RenderSystem.assertOnRenderThread(); if(closed) throw new IllegalStateException("Flywheel engine is retired"); }
    private static Object invoke(Method method,Object self,Object... args) {
        try { return method.invoke(self,args); }
        catch(InvocationTargetException failure) {
            Throwable cause=failure.getCause();
            if(cause instanceof RuntimeException problem) throw problem;
            if(cause instanceof Error problem) throw problem;
            throw new IllegalStateException("Legacy engine callback failed",cause);
        } catch(ReflectiveOperationException failure) { throw new IllegalStateException("Legacy engine API changed",failure); }
    }
}
