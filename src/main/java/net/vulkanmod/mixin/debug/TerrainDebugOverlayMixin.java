package net.vulkanmod.mixin.debug;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.vulkanmod.render.chunk.WorldRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Terrain diagnostics must not depend on VulkanMod's optional GUI-optimization
 * mixins. Keep the vanilla chunk summary short and expose profiling data as
 * purpose-specific F3 rows that remain readable at normal window widths.
 */
@Mixin(DebugScreenOverlay.class)
public abstract class TerrainDebugOverlayMixin {
    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "getGameInformation", at = @At("RETURN"), cancellable = true)
    private void vulkanmod$splitTerrainDebugRows(CallbackInfoReturnable<List<String>> cir) {
        List<String> original = cir.getReturnValue();
        if(original == null || this.minecraft.level == null)
            return;

        ArrayList<String> lines = new ArrayList<>(original);
        int chunkLineIndex = -1;
        String chunkLine = null;
        for(int i = 0; i < lines.size(); ++i) {
            String line = lines.get(i);
            if(line != null && line.startsWith("Chunks:")) {
                chunkLineIndex = i;
                chunkLine = line;
                break;
            }
        }
        if(chunkLineIndex < 0 || chunkLine == null)
            return;

        int taskStart = chunkLine.indexOf(", Terrain workers:");
        if(taskStart < 0)
            taskStart = chunkLine.indexOf(", iT:");
        int regionStart = chunkLine.indexOf(" R:");

        // If another mixin has already split this row, do not duplicate it.
        if(taskStart < 0 && regionStart < 0)
            return;

        int summaryEnd = chunkLine.length();
        if(taskStart >= 0)
            summaryEnd = Math.min(summaryEnd, taskStart);
        if(regionStart >= 0)
            summaryEnd = Math.min(summaryEnd, regionStart);
        lines.set(chunkLineIndex, chunkLine.substring(0, summaryEnd));

        ArrayList<String> terrainLines = new ArrayList<>();
        WorldRenderer renderer = WorldRenderer.getInstance();
        if(renderer != null && renderer.getTaskDispatcher() != null) {
            for(String taskLine : renderer.getTaskDispatcher().getDebugLines()) {
                int syncStart = taskLine.indexOf(" sync ");
                if(syncStart >= 0) {
                    terrainLines.add(taskLine.substring(0, syncStart));
                    terrainLines.add("Terrain " + taskLine.substring(syncStart + 1));
                } else {
                    terrainLines.add(taskLine);
                }
            }
        }

        if(regionStart >= 0) {
            String region = chunkLine.substring(regionStart + 1).trim();
            int memoryStart = region.indexOf(" rm:");
            if(memoryStart >= 0) {
                terrainLines.add("Terrain draw: " + region.substring(0, memoryStart));
                terrainLines.add("Terrain memory: " + region.substring(memoryStart + 1));
            } else if(!region.isEmpty()) {
                terrainLines.add("Terrain draw: " + region);
            }
        }

        lines.addAll(chunkLineIndex + 1, terrainLines);
        cir.setReturnValue(lines);
    }
}
