package net.vulkanmod.render.chunk;

/** World-coordinate ownership guard for wrapped client terrain section slots. */
final class SectionRingOwnership {
    private SectionRingOwnership() {}

    static boolean matches(int sectionX, int sectionY, int sectionZ,
                           int originX, int originY, int originZ) {
        // Exact aligned origins; long arithmetic rejects malicious/out-of-range
        // section coordinates rather than accepting overflow aliases.
        return originX == (long) sectionX * 16L
                && originY == (long) sectionY * 16L
                && originZ == (long) sectionZ * 16L;
    }
}
