package net.vulkanmod.mixin.render;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.AABB;
import net.vulkanmod.render.particle.FrustumInfiniteBoundsEquality;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Forge 47 adds a virtual AABB.equals check for its infinite-extent sentinel
 * on every frustum test. Only load this experiment when explicitly requested.
 * The ordinary frustum test and Forge's special sentinel semantics stay intact.
 */
@Mixin(Frustum.class)
public abstract class FrustumInfiniteBoundsFastPathMixin {
    @Redirect(method = "isVisible(Lnet/minecraft/world/phys/AABB;)Z",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/phys/AABB;equals(Ljava/lang/Object;)Z"),
            require = 1)
    private boolean vulkanmod$fastInfiniteBoundsEquality(AABB bounds, Object sentinel) {
        return FrustumInfiniteBoundsEquality.matches(bounds, sentinel);
    }
}
