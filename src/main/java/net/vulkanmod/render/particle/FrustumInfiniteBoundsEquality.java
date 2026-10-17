package net.vulkanmod.render.particle;

import net.minecraft.world.phys.AABB;

/**
 * Equality used only by the opt-in Forge frustum path. Ordinary concrete AABBs
 * cannot equal the infinite sentinel if their first coordinate differs. Keep
 * the virtual equals call for subclasses and all other ambiguous cases.
 */
public final class FrustumInfiniteBoundsEquality {
    private FrustumInfiniteBoundsEquality() {}

    public static boolean matches(AABB bounds, Object sentinel) {
        if (bounds == sentinel) return true;
        if (bounds.getClass() == AABB.class && sentinel instanceof AABB other
                && Double.compare(bounds.minX, other.minX) != 0) {
            return false;
        }
        return bounds.equals(sentinel);
    }
}
