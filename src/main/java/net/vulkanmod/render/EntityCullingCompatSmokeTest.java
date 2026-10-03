package net.vulkanmod.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.vulkanmod.Initializer;
import net.vulkanmod.compatibility.EntityCullingCompat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/** Actual optional-mod flags and transformed game hooks, without a loaded world. */
public final class EntityCullingCompatSmokeTest {
    private EntityCullingCompatSmokeTest() {}

    public static void verifyIfRequested() {
        if(!Boolean.getBoolean("vulkanmod.ciEntityCullingSmoke")) return;
        if(!EntityCullingCompat.available()) throw new AssertionError("EntityCulling fixture is missing");
        boolean oldBatching = Initializer.CONFIG.entityCulling;
        Map<?, ?> queued = null;
        boolean ownsQueue = false;
        try {
            Class<?> base = Class.forName("dev.tr7zw.entityculling.EntityCullingModBase");
            Object instance = base.getField("instance").get(null);
            if(instance == null) throw new AssertionError("EntityCulling did not initialize");
            ItemEntity entity = new ItemEntity(EntityType.ITEM, null);
            ChestBlockEntity block = new ChestBlockEntity(BlockPos.ZERO, Blocks.CHEST.defaultBlockState());
            Method culled = entity.getClass().getMethod("setCulled", boolean.class);
            Method forced = entity.getClass().getMethod("isForcedVisible");
            culled.invoke(entity, true);
            block.getClass().getMethod("setCulled", boolean.class).invoke(block, true);
            if((boolean)forced.invoke(entity)) throw new AssertionError("Fresh fixture is forced visible");

            Method render = null;
            for(Method candidate : LevelRenderer.class.getDeclaredMethods()) {
                if(java.util.Arrays.equals(candidate.getParameterTypes(), new Class<?>[]{Entity.class,
                        double.class, double.class, double.class, float.class, PoseStack.class, MultiBufferSource.class})) {
                    render = candidate; break;
                }
            }
            if(render == null) throw new AssertionError("Transformed renderEntity hook not found");
            render.setAccessible(true);
            var minecraft = Minecraft.getInstance();
            for(Field field : LevelRenderer.class.getDeclaredFields()) {
                if(field.getType().getName().equals("it.unimi.dsi.fastutil.objects.Object2ReferenceOpenHashMap")) {
                    field.setAccessible(true);
                    queued = (Map<?, ?>)field.get(minecraft.levelRenderer);
                    break;
                }
            }
            if(queued == null || !queued.isEmpty()) throw new AssertionError("Entity batching fixture is not isolated");
            ownsQueue = true;
            Initializer.CONFIG.entityCulling = true;
            int skippedEntities = base.getField("skippedEntities").getInt(instance);
            int skippedBlocks = base.getField("skippedBlockEntities").getInt(instance);
            render.invoke(minecraft.levelRenderer, entity, 0D, 0D, 0D, 0F, null, null);
            minecraft.getBlockEntityRenderDispatcher().render(block, 0F, null, null);
            if(base.getField("skippedEntities").getInt(instance) != skippedEntities + 1
                    || base.getField("skippedBlockEntities").getInt(instance) != skippedBlocks + 1
                    || !queued.isEmpty()) throw new AssertionError("External occlusion hooks did not cancel hidden draws");

            // An uncertain/portal view must override those exact same culled flags.
            EntityCullingCompat.preserveForView(entity, true);
            EntityCullingCompat.preserveForView(block, true);
            if(!(boolean)forced.invoke(entity)
                    || !(boolean)block.getClass().getMethod("isForcedVisible").invoke(block))
                throw new AssertionError("Portal visibility timeout was not applied");
            int renderedEntities = base.getField("renderedEntities").getInt(instance);
            int renderedBlocks = base.getField("renderedBlockEntities").getInt(instance);
            render.invoke(minecraft.levelRenderer, entity, 0D, 0D, 0D, 0F, null, null);
            minecraft.getBlockEntityRenderDispatcher().render(block, 0F, null, null);
            if(base.getField("renderedEntities").getInt(instance) != renderedEntities + 1
                    || base.getField("renderedBlockEntities").getInt(instance) != renderedBlocks + 1
                    || queued.size() != 1) throw new AssertionError("Preserved draws did not reach normal renderer dispatch");
            Initializer.LOGGER.info("EntityCulling native compatibility smoke passed: hidden hooks and uncertain-view timeout");
        } catch(ReflectiveOperationException error) {
            throw new IllegalStateException("EntityCulling native compatibility contract failed", error);
        } finally {
            if(ownsQueue) queued.clear();
            Initializer.CONFIG.entityCulling = oldBatching;
        }
    }
}
