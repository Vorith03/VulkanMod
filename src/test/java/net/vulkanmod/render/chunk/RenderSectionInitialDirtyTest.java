package net.vulkanmod.render.chunk;

import net.vulkanmod.render.chunk.build.CompiledSection;

/**
 * Initial/ring-repositioned dirty notices are coalescible only before a region
 * is captured. A compiled or formerly clean owner must still advance generation.
 */
public final class RenderSectionInitialDirtyTest {
    public static void main(String[] args) {
        // Standalone JavaExec has no Minecraft startup lifecycle. RenderSection
        // constructs TerrainRenderType/RenderType, which require vanilla registries.
        // Vanilla initializes its version before bootstrap/data fixers.
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        RenderSection section = new RenderSection(0, 0, 0, 0);
        long initialGeneration = section.getVoxelGeneration();
        require(section.isDirty() && !section.isCompiled(), "new section starts uncompiled and dirty");

        for(int i = 0; i < 4096; i++)
            section.setDirty(i == 7);
        require(section.getVoxelGeneration() == initialGeneration,
                "repeated initial notices must not churn a generation with no captured work");
        require(section.isDirtyFromPlayer(), "coalescing must preserve player-change intent");

        // A queued completion without a CompileTask must revoke coalescing,
        // just like a captured region. The native publication smoke exercises
        // the actual queue and stale-generation rejection.
        RenderSection queued = new RenderSection(1, 0, 0, 0);
        long queuedGeneration = queued.getVoxelGeneration();
        queued.preventInitialDirtyCoalescing();
        queued.setDirty(false);
        require(queued.getVoxelGeneration() == queuedGeneration + 1,
                "queued publication must invalidate even an initially uncompiled owner");

        section.setNotDirty();
        section.setDirty(false);
        require(section.getVoxelGeneration() == initialGeneration + 1,
                "first post-admission dirty must invalidate");
        require(!section.isDirtyFromPlayer(), "clean transition resets player-change intent");

        section.setCompiledSection(new CompiledSection());
        long compiledGeneration = section.getVoxelGeneration();
        section.setDirty(true);
        require(section.getVoxelGeneration() == compiledGeneration + 1,
                "dirty compiled section must never coalesce subsequent invalidation");
        require(section.isDirtyFromPlayer(), "compiled invalidation retains player intent");

        section.setOrigin(16, 0, 0);
        long ringGeneration = section.getVoxelGeneration();
        require(!section.isCompiled() && section.isDirty(),
                "ring reassignment resets compilation and starts dirty");
        for(int i = 0; i < 4096; i++)
            section.setDirty(false);
        require(section.getVoxelGeneration() == ringGeneration,
                "ring-reassigned, uncaptured owner must coalesce repeated notices");
        section.preventInitialDirtyCoalescing();
        section.setDirty(false);
        require(section.getVoxelGeneration() == ringGeneration + 1,
                "new publication after ring reuse must close the coalescing window");

        System.out.println("Initial section invalidation coalescing contract passed");
    }

    private static void require(boolean condition, String label) {
        if(!condition) throw new AssertionError(label);
    }
}
