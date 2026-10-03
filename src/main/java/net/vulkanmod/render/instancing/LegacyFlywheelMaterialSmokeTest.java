package net.vulkanmod.render.instancing;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.vulkanmod.Initializer;
import org.lwjgl.system.MemoryUtil;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/** Real optional API/cache/lifecycle tests; deliberately does not claim material shader/fallback pixels. */
public final class LegacyFlywheelMaterialSmokeTest {
    private LegacyFlywheelMaterialSmokeTest() {}
    public static void verify(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> managerApi = Class.forName("com.jozufozu.flywheel.api.MaterialManager", false, loader);
        Class<?> groupApi = Class.forName("com.jozufozu.flywheel.api.MaterialGroup", false, loader);
        Class<?> materialApi = Class.forName("com.jozufozu.flywheel.api.Material", false, loader);
        Class<?> specApi = Class.forName("com.jozufozu.flywheel.api.struct.StructType", false, loader);
        Class<?> instancerApi = Class.forName("com.jozufozu.flywheel.api.Instancer", false, loader);
        Class<?> layerApi = Class.forName("com.jozufozu.flywheel.backend.RenderLayer", false, loader);
        Class<?> modelType = Class.forName("com.jozufozu.flywheel.core.materials.model.ModelType", false, loader);
        Class<?> modelApi = Class.forName("com.jozufozu.flywheel.core.model.Model", false, loader);
        Object solid = layerApi.getField("SOLID").get(null), cutout = layerApi.getField("CUTOUT").get(null);
        Method state = managerApi.getMethod("state", layerApi, RenderType.class);
        Method material = groupApi.getMethod("material", specApi);
        Method model = materialApi.getMethod("model", Object.class, Supplier.class);
        Method create = instancerApi.getMethod("createInstance");
        Object spec = modelType.getConstructor().newInstance();
        Object world = new Object();
        var fallback = new Fallback(loader);
        var owner = new LegacyFlywheelMaterials(loader, world, 7, fallback);
        Object manager = owner.manager();
        Object group = state.invoke(manager, solid, RenderType.solid());
        Object mat = material.invoke(group, spec);
        AtomicInteger builds = new AtomicInteger();
        Supplier<Object> factory = () -> { builds.incrementAndGet(); return block(loader, true, false); };
        try {
            check(owner.worldIdentity() == world && owner.generation() == 7, "World/generation ownership lost");
            check(managerApi.getMethod("defaultSolid").invoke(manager) == group, "Default manager method failed");
            check(material.invoke(group, spec) == mat, "Material identity lost");
            Object instancer = model.invoke(mat, new String("shared"), factory);
            check(model.invoke(mat, new String("shared"), (Supplier<?>)() -> { throw new AssertionError("Cache rebuilt"); }) == instancer,
                    "Equal model key lost its instancer");
            check(builds.get() == 1, "Model factory called twice");
            create.invoke(instancer);
            Object otherSpecMat = material.invoke(group, modelType.getConstructor().newInstance());
            Object otherSpec = model.invoke(otherSpecMat, "shared", factory);
            Object otherState = model.invoke(material.invoke(state.invoke(manager, solid, RenderType.cutout()), spec), "shared", factory);
            Object otherLayer = model.invoke(material.invoke(state.invoke(manager, cutout, RenderType.solid()), spec), "shared", factory);
            check(instancer != otherSpec && instancer != otherState && instancer != otherLayer && builds.get() == 4,
                    "Material/layer/RenderType cache collision");
            create.invoke(otherSpec); create.invoke(otherState); create.invoke(otherLayer);
            AtomicInteger records = new AtomicInteger();
            owner.visitModels(solid, RenderType.solid(), (geometry, instances) -> {
                check(geometry.vertexCount() == 4 && !geometry.isShaded(2), "Owned model/shading lost");
                records.addAndGet(instances.snapshot().remaining()/108);
            });
            check(records.get() == 2, "Instance membership missing");

            // A real StructType proxy is unqualified even if it can describe model-like records.
            Object unsupportedSpec = Proxy.newProxyInstance(loader, new Class<?>[]{specApi}, (self, method, args) -> {
                throw new AssertionError("Unsupported struct/writer must not be probed");
            });
            Object unknown = Proxy.newProxyInstance(loader, new Class<?>[]{modelApi}, (self, method, args) -> {
                throw new AssertionError("Unsupported model must not be read/deleted");
            });
            Supplier<?> unknownFactory = () -> unknown;
            Object delegatedSpec = model.invoke(material.invoke(group, unsupportedSpec), "foreign-program", unknownFactory);
            check(fallback.sources.get(0) == unknown && fallback.spec == unsupportedSpec && fallback.layer == solid
                    && fallback.renderType == RenderType.solid(), "Unsupported material was not delegated intact");
            Object delegatedModel = model.invoke(mat, "foreign-model", unknownFactory);
            check(fallback.sources.get(1) == unknown && fallback.calls == 2, "Unsupported model was not transferred");
            check(model.invoke(mat, "foreign-model", factory) == delegatedModel && fallback.calls == 2,
                    "Fallback cache rebuilt");
            create.invoke(delegatedSpec); create.invoke(delegatedModel);

            // An invalid but owned CPU model is cleaned and not published; the key can retry.
            reject(() -> model.invoke(mat, "invalid", (Supplier<?>)() -> block(loader, false, true)));
            Object retry = model.invoke(mat, "invalid", factory); create.invoke(retry);
            AtomicInteger notices = new AtomicInteger();
            Runnable listener = () -> {
                try {
                    check(managerApi.getMethod("getOriginCoordinate").invoke(manager).equals(new BlockPos(-101, 30, 202)), "Origin not published before listener");
                    owner.visitModels(solid, RenderType.solid(), (geometry, instances) -> check(!instances.snapshot().hasRemaining(), "Local membership not cleared"));
                    check(fallback.clears == 1 && fallback.instances.stream().noneMatch(i -> i.snapshot().hasRemaining()), "Fallback clear must precede listeners");
                    check(model.invoke(mat, "shared", factory) == instancer, "Origin shift rebuilt shared geometry");
                    create.invoke(instancer); notices.incrementAndGet();
                } catch(ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
            };
            owner.addOriginListener(listener); owner.addOriginListener(listener);
            owner.shiftOrigin(new BlockPos(-101, 30, 202)); owner.shiftOrigin(new BlockPos(-101, 30, 202));
            check(notices.get() == 1 && fallback.clears == 1, "Duplicate origin notification");
            try(var second = new LegacyFlywheelMaterials(loader, new Object(), 8, new Fallback(loader))) {
                Object secondMat = material.invoke(state.invoke(second.manager(), solid, RenderType.solid()), spec);
                check(model.invoke(secondMat, "shared", factory) != instancer, "World/reload cache shared");
            }
            owner.close(); owner.close();
            check(fallback.closes == 1, "Fallback retired twice");
            reject(() -> state.invoke(manager, solid, RenderType.solid()));
            reject(() -> material.invoke(group, spec));
            reject(() -> model.invoke(mat, "shared", factory));
            reject(() -> create.invoke(instancer));
        } finally { owner.close(); }
        Initializer.LOGGER.info("Flywheel material ownership smoke passed: actual manager/group/material APIs, key/material/layer/state/world generation isolation, owned CPU release, failed import retry, unsupported delegation, origin clear/recreation, stale handles; engine remains off");
    }

    private static final class Fallback implements LegacyFlywheelMaterials.Fallback {
        final ClassLoader loader;
        final ArrayList<LegacyFlywheelInstances> instances = new ArrayList<>();
        final ArrayList<Object> sources = new ArrayList<>();
        Object layer, renderType, spec;
        int calls, clears, closes;
        Fallback(ClassLoader loader) { this.loader = loader; }
        public Object model(Object layer, Object renderType, Object spec, Object key, Supplier<?> supplier) {
            this.layer = layer; this.renderType = renderType; this.spec = spec; calls++;
            sources.add(supplier.get()); // Unsupported proxy fixtures have no resources; no renderer is installed.
            try {
                var owner = new LegacyFlywheelInstances(loader); instances.add(owner); return owner.owner();
            } catch(ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
        }
        public void clearForOriginShift(BlockPos origin) { clears++; instances.forEach(LegacyFlywheelInstances::clearForOriginShift); }
        public void close() { closes++; instances.forEach(LegacyFlywheelInstances::close); }
    }

    private static Object block(ClassLoader loader, boolean sequential, boolean invalid) {
        ByteBuffer vertices = MemoryUtil.memAlloc(128), indices = MemoryUtil.memAlloc(24);
        try {
            for(int v=0; v<4; v++) {
                int at=v*32;
                vertices.putFloat(at,v); vertices.putFloat(at+4,0); vertices.putFloat(at+8,0);
                vertices.putInt(at+12,-1); vertices.putFloat(at+16,0); vertices.putFloat(at+20,0);
                vertices.putInt(at+24,0); vertices.putInt(at+28,0);
            }
            for(int value : new int[]{0,1,2,2,3,invalid ? 4 : 0}) indices.putInt(value);
            indices.flip();
            var state = new BufferBuilder.DrawState(DefaultVertexFormat.BLOCK,4,6,VertexFormat.Mode.QUADS,
                    VertexFormat.IndexType.INT,false,sequential);
            Class<?> type = Class.forName("com.jozufozu.flywheel.core.model.BlockModel", false, loader);
            return type.getConstructor(ByteBuffer.class, ByteBuffer.class, BufferBuilder.DrawState.class, int.class, String.class)
                    .newInstance(vertices,indices,state,2,"material cache CPU fixture");
        } catch(ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
        finally { MemoryUtil.memFree(vertices); MemoryUtil.memFree(indices); }
    }
    private interface Action { void run() throws ReflectiveOperationException; }
    private static void reject(Action action) throws ReflectiveOperationException {
        try { action.run(); }
        catch(InvocationTargetException failure) {
            if(failure.getCause() instanceof IllegalStateException || failure.getCause() instanceof IllegalArgumentException) return;
            throw failure;
        }
        throw new AssertionError("Retired/invalid operation admitted");
    }
    private static void check(boolean valid, String reason) { if(!valid) throw new AssertionError(reason); }
}
