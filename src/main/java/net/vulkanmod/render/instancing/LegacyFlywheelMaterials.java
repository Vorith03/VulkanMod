package net.vulkanmod.render.instancing;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.core.BlockPos;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.function.BiConsumer;

/**
 * Optional legacy MaterialManager ownership, scoped to one world/reload generation.
 * Not installed into Flywheel: the engine must qualify shaders and a rendering fallback.
 * All calls occur on the render thread after instance update tasks finish.
 */
public final class LegacyFlywheelMaterials implements AutoCloseable {
    public interface Fallback extends AutoCloseable {
        /** Own the supplied model if invoked, and return a real legacy Instancer. */
        Object model(Object layer, Object renderType, Object spec, Object key, Supplier<?> supplier);
        /** Clear all fallback membership before recreation listeners run. */
        void clearForOriginShift(BlockPos origin);
        @Override void close();
    }
    private static final int MAX_MODELS = 4096;
    private static final long MAX_GEOMETRY_BYTES = 256L * 1024 * 1024;
    private final Object worldIdentity;
    private final long generation;
    private final ClassLoader loader;
    private final Class<?> groupApi, materialApi, instancerApi, modelType;
    private final Method getProgram;
    private final Object transformedProgram;
    private final Fallback fallback;
    private final boolean transformedEnabled;
    private final Object manager;
    private final Map<Object, IdentityHashMap<Object, Group>> layers = new IdentityHashMap<>();
    private final List<WeakReference<Runnable>> listeners = new ArrayList<>();
    private BlockPos origin = BlockPos.ZERO;
    private long geometryBytes;
    private int modelCount;
    private int groupCount, materialCount;
    private boolean shifting, originPending;
    private boolean closed;

    public LegacyFlywheelMaterials(ClassLoader loader, Object worldIdentity, long generation, Fallback fallback)
            throws ReflectiveOperationException {
        this(loader, worldIdentity, generation, fallback, true);
    }
    public LegacyFlywheelMaterials(ClassLoader loader, Object worldIdentity, long generation, Fallback fallback,
                                   boolean transformedEnabled) throws ReflectiveOperationException {
        this.loader = loader;
        this.worldIdentity = Objects.requireNonNull(worldIdentity);
        this.generation = generation;
        this.fallback = Objects.requireNonNull(fallback);
        this.transformedEnabled = transformedEnabled;
        Class<?> managerApi = Class.forName("com.jozufozu.flywheel.api.MaterialManager", false, loader);
        groupApi = Class.forName("com.jozufozu.flywheel.api.MaterialGroup", false, loader);
        materialApi = Class.forName("com.jozufozu.flywheel.api.Material", false, loader);
        instancerApi = Class.forName("com.jozufozu.flywheel.api.Instancer", false, loader);
        modelType = Class.forName("com.jozufozu.flywheel.core.materials.model.ModelType", false, loader);
        getProgram = modelType.getMethod("getProgramSpec");
        transformedProgram = Class.forName("com.jozufozu.flywheel.core.Programs", false, loader)
                .getField("TRANSFORMED").get(null);
        manager = proxy(managerApi, (self, method, args) -> switch(method.getName()) {
            case "state" -> state(args[0], args[1]);
            case "getOriginCoordinate" -> origin;
            default -> defaultMethod(self, method, args);
        });
    }

    public Object manager() { requireOpen(); return manager; }
    public BlockPos origin() { requireOpen(); return origin; }
    public Object worldIdentity() { return worldIdentity; }
    public long generation() { return generation; }

    private Object state(Object layer, Object renderType) {
        requireOpen(); Objects.requireNonNull(layer); Objects.requireNonNull(renderType);
        // RenderType identity matters even when names or implementations compare equal.
        var groups = layers.computeIfAbsent(layer, ignored -> new IdentityHashMap<>());
        Group group = groups.get(renderType);
        if(group == null) {
            if(groupCount >= MAX_MODELS) throw new IllegalStateException("Flywheel group cache limit reached");
            group = new Group(layer, renderType); groups.put(renderType, group); groupCount++;
        }
        return group.proxy;
    }

    private final class Group {
        final Object layer, renderType, proxy;
        final Map<Object, Material> materials = new IdentityHashMap<>();
        Group(Object layer, Object renderType) {
            this.layer = layer; this.renderType = renderType;
            proxy = proxy(groupApi, (self, method, args) -> {
                if(method.getName().equals("material")) {
                    Object spec = Objects.requireNonNull(args[0]);
                    Material material = materials.get(spec);
                    if(material == null) {
                        if(materialCount >= MAX_MODELS) throw new IllegalStateException("Flywheel material cache limit reached");
                        material = new Material(this, spec); materials.put(spec, material); materialCount++;
                    }
                    return material.proxy;
                }
                return defaultMethod(self, method, args);
            });
        }
    }

    private final class Material {
        final Group group;
        final Object spec, proxy;
        final Map<Object, Entry> models = new HashMap<>(); // Legacy Material.model uses key equality.
        final boolean qualified;
        boolean creating;
        Material(Group group, Object spec) {
            this.group = group; this.spec = spec;
            qualified = transformedEnabled && spec.getClass() == modelType && transformedProgram.equals(invoke(getProgram, spec));
            proxy = proxy(materialApi, (self, method, args) -> {
                if(method.getName().equals("model")) {
                    Object key = args[0];
                    Supplier<?> supplier = (Supplier<?>)Objects.requireNonNull(args[1]);
                    Entry cached = models.get(key);
                    if(cached != null) return cached.owner;
                    if(creating) throw new IllegalStateException("Reentrant model creation");
                    if(modelCount >= MAX_MODELS) throw new IllegalStateException("Flywheel model cache limit reached");
                    creating = true;
                    try {
                        Entry created = create(key, supplier);
                        models.put(key, created); modelCount++;
                        return created.owner;
                    } finally { creating = false; }
                }
                return defaultMethod(self, method, args);
            });
        }
        Entry create(Object key, Supplier<?> supplier) throws ReflectiveOperationException {
            if(!qualified) return delegated(key, supplier);
            Object source = Objects.requireNonNull(supplier.get(), "Model supplier returned null");
            var imported = LegacyFlywheelModel.takeOwned(source);
            if(imported.isEmpty()) {
                boolean[] consumed = {false};
                return delegated(key, () -> {
                    if(consumed[0]) throw new IllegalStateException("Fallback model supplier consumed twice");
                    consumed[0] = true; return source;
                });
            }
            ModelGeometry geometry = imported.get();
            long bytes = (long)geometry.vertexCount()*ModelGeometry.STRIDE + (long)geometry.indexCount()*4;
            if(bytes > MAX_GEOMETRY_BYTES - geometryBytes)
                throw new IllegalStateException("Flywheel geometry cache limit reached");
            var instances = new LegacyFlywheelInstances(loader);
            geometryBytes += bytes;
            return new Entry(instances.owner(), instances, geometry);
        }
        Entry delegated(Object key, Supplier<?> supplier) {
            Object owner = fallback.model(group.layer, group.renderType, spec, key, supplier);
            if(!instancerApi.isInstance(owner)) throw new IllegalStateException("Fallback did not supply a legacy Instancer");
            return new Entry(owner, null, null);
        }
    }

    /** Iterate qualified geometry/data only; fallback remains independently owned by the engine. */
    public void visitModels(Object layer, Object renderType, BiConsumer<ModelGeometry, LegacyFlywheelInstances> visitor) {
        requireOpen(); Objects.requireNonNull(visitor);
        var groups = layers.get(layer);
        Group group = groups == null ? null : groups.get(renderType);
        if(group == null) return;
        var entries = new ArrayList<Entry>();
        for(Material material : group.materials.values()) entries.addAll(material.models.values());
        for(Entry entry : entries) if(entry.instances != null) visitor.accept(entry.geometry, entry.instances);
    }

    private static final class Entry {
        final Object owner;
        final LegacyFlywheelInstances instances;
        final ModelGeometry geometry;
        SharedModelBuffer mesh;
        Entry(Object owner, LegacyFlywheelInstances instances, ModelGeometry geometry) {
            this.owner = owner; this.instances = instances; this.geometry = geometry;
        }
        void close() {
            if(instances != null) instances.close();
            if(mesh != null) { mesh.close(); mesh = null; }
        }
    }

    /** Caller must qualify this pipeline's material semantics, textures and render state. */
    public void draw(Object layer, Object renderType, GraphicsPipeline pipeline) {
        requireOpen();
        var groups = layers.get(layer);
        Group group = groups == null ? null : groups.get(renderType);
        if(group == null) return;
        for(Material material : group.materials.values()) {
            for(Entry entry : material.models.values()) {
                if(entry.instances == null || entry.geometry.indexCount() == 0) continue;
                if(entry.mesh == null) entry.mesh = new SharedModelBuffer(entry.geometry);
                entry.instances.draw(entry.mesh, pipeline);
            }
        }
    }

    public void addOriginListener(Runnable listener) {
        requireOpen(); Objects.requireNonNull(listener);
        listeners.removeIf(reference -> reference.get() == null);
        if(listeners.stream().noneMatch(reference -> reference.get() == listener))
            listeners.add(new WeakReference<>(listener));
    }

    /** Keep shared geometry/instancer identities, clear all members, then recreate at the new origin. */
    public void shiftOrigin(BlockPos next) {
        requireOpen(); Objects.requireNonNull(next);
        if(shifting) throw new IllegalStateException("Reentrant origin shift");
        if(origin.equals(next) && !originPending) return;
        shifting = true; originPending = true;
        try {
            origin = next.immutable();
            for(var groups : layers.values()) for(Group group : groups.values())
                for(Material material : group.materials.values()) for(Entry entry : material.models.values())
                    if(entry.instances != null) entry.instances.clearForOriginShift();
            fallback.clearForOriginShift(origin);
            var callbacks = new ArrayList<Runnable>();
            listeners.removeIf(reference -> reference.get() == null);
            for(var reference : listeners) { Runnable listener = reference.get(); if(listener != null) callbacks.add(listener); }
            callbacks.forEach(Runnable::run); // Snapshot permits listener registration during recreation.
            originPending = false;
        } finally { shifting = false; } // A failed recreation can retry even at the same origin.
    }

    @Override public void close() {
        RenderSystem.assertOnRenderThread();
        if(closed) return;
        closed = true; // Retained manager/group/material proxies must fail after world/reload retirement.
        Throwable failure = null;
        for(var groups : layers.values()) for(Group group : groups.values())
            for(Material material : group.materials.values()) for(Entry entry : material.models.values()) {
                try { entry.close(); }
                catch(RuntimeException | Error problem) { failure = append(failure, problem); }
            }
        layers.clear(); listeners.clear(); geometryBytes = 0; modelCount = 0; groupCount = 0; materialCount = 0;
        try { fallback.close(); }
        catch(RuntimeException | Error problem) { failure = append(failure, problem); }
        if(failure instanceof RuntimeException problem) throw problem;
        if(failure instanceof Error problem) throw problem;
    }
    private static Throwable append(Throwable first, Throwable next) {
        if(first == null) return next;
        if(first != next) first.addSuppressed(next);
        return first;
    }

    private Object proxy(Class<?> api, InvocationHandler handler) {
        return Proxy.newProxyInstance(loader, new Class<?>[]{api}, (self, method, args) -> {
            requireOpen();
            return switch(method.getName()) {
                case "hashCode" -> System.identityHashCode(self);
                case "equals" -> self == args[0];
                case "toString" -> "Vulkan legacy " + api.getSimpleName() + " generation " + generation;
                default -> handler.invoke(self, method, args);
            };
        });
    }
    private static Object defaultMethod(Object self, Method method, Object[] args) throws Throwable {
        if(method.isDefault()) return InvocationHandler.invokeDefault(self, method, args);
        throw new UnsupportedOperationException("Unqualified legacy material method: " + method);
    }
    private static Object invoke(Method method, Object self) {
        try { return method.invoke(self); }
        catch(InvocationTargetException failure) { throw new IllegalStateException("Legacy material API failed", failure.getCause()); }
        catch(ReflectiveOperationException failure) { throw new IllegalStateException("Legacy material API changed", failure); }
    }
    private void requireOpen() { RenderSystem.assertOnRenderThread(); if(closed) throw new IllegalStateException("Flywheel material generation is closed"); }
}
