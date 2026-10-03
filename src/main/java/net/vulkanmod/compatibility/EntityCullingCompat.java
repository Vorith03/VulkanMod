package net.vulkanmod.compatibility;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.vulkanmod.Initializer;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Preserve external occlusion hooks; uncertain views/bounds use its visibility timeout. */
public final class EntityCullingCompat {
    private static volatile boolean initialized;
    private static Class<?> cullable;
    private static Method setTimeout;
    private EntityCullingCompat() {}

    public static boolean available() {
        if(!initialized) initialize();
        return setTimeout != null;
    }

    public static void prepareEntityRender(Entity entity) {
        if(!enabled() || !available()) return;
        preserveForView(entity, ImmersivePortalsLevelRendererCompat.shouldCancelWorldRendererReload()
                || excluded(Initializer.CONFIG.occlusionAlwaysVisibleEntities,
                        BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString()));
    }

    public static void prepareBlockEntityRender(BlockEntity entity) {
        if(!enabled() || !available()) return;
        preserveForView(entity, ImmersivePortalsLevelRendererCompat.shouldCancelWorldRendererReload()
                || excluded(Initializer.CONFIG.occlusionAlwaysVisibleBlockEntities,
                        BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(entity.getType()).toString()));
    }

    private static boolean enabled() {
        return Initializer.CONFIG != null && Initializer.CONFIG.conservativeOcclusionCompatibility;
    }

    private static boolean excluded(String[] ids, String id) {
        if(ids != null) for(String candidate : ids) if(id.equals(candidate)) return true;
        return false;
    }

    // Shared with the native contract: no fabricated culling result or clock mutation.
    public static void preserveForView(Object object, boolean uncertain) {
        if(!uncertain || !available() || !cullable.isInstance(object)) return;
        try {
            setTimeout.invoke(object);
        } catch(IllegalAccessException error) {
            throw new IllegalStateException("Cannot preserve EntityCulling visibility", error);
        } catch(InvocationTargetException error) {
            throw new IllegalStateException("EntityCulling visibility timeout failed", error.getCause());
        }
    }

    private static synchronized void initialize() {
        if(initialized) return;
        ClassLoader loader = EntityCullingCompat.class.getClassLoader();
        try {
            for(String name : new String[]{"dev.tr7zw.entityculling.versionless.access.Cullable",
                    "dev.tr7zw.entityculling.access.Cullable"}) {
                try {
                    cullable = Class.forName(name, false, loader);
                    setTimeout = cullable.getMethod("setTimeout");
                    return;
                } catch(ClassNotFoundException absent) {
                    // The legacy and versionless interface names are both optional.
                } catch(NoSuchMethodException changed) {
                    Initializer.LOGGER.warn("Unsupported EntityCulling visibility interface: {}", name);
                }
            }
            cullable = null;
        } finally {
            initialized = true;
        }
    }
}
