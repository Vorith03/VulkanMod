package net.vulkanmod.render.profiling;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/** Bounded post-capture stack census. Sample counts are not elapsed CPU/GPU time. */
final class CpuGraphicsSamples {
    static final int MAX_STACKS = 2048;
    static final int MAX_FRAMES = 48;
    static final int TOP_STACKS = 40;
    private final long renderThreadId;
    private final Map<String, StackCount> stacks = new LinkedHashMap<>();
    private final Map<String, long[]> categories = new LinkedHashMap<>();
    private final long[] relevant = new long[2];
    private final long[] all = new long[2];
    private long background, missingStack, truncatedStack, overflow, profilerSamples;

    CpuGraphicsSamples(long renderThreadId) { this.renderThreadId = renderThreadId; }

    void accept(boolean nativeBoundary, long threadId, String threadName,
                List<String> frames, boolean truncated) {
        int kind = nativeBoundary ? 1 : 0;
        all[kind]++;
        if (frames.isEmpty()) { missingStack++; return; }
        if (truncated || frames.size() > MAX_FRAMES) truncatedStack++;
        // JFR setup/output and profiler summary formatting are observer work,
        // not graphics targets. Keep their count visible instead of ranking it.
        if (frames.stream().anyMatch(f -> f.startsWith("net.vulkanmod.render.profiling."))) {
            profilerSamples++;
            return;
        }
        String category = category(frames);
        boolean render = threadId == renderThreadId;
        if (!render && category == null) { background++; return; }
        if (category == null) category = "render_thread_unclassified";
        String role = render ? "render" : "graphics_worker";
        String group = role + "/" + category;
        relevant[kind]++;
        categories.computeIfAbsent(group, ignored -> new long[2])[kind]++;
        List<String> bounded = frames.subList(0, Math.min(MAX_FRAMES, frames.size()));
        String path = String.join(";", bounded);
        String key = kind + "/" + group + "/" + path;
        StackCount count = stacks.get(key);
        if (count != null) { count.samples++; return; }
        if (stacks.size() >= MAX_STACKS) { overflow++; return; }
        stacks.put(key, new StackCount(nativeBoundary, group, token(threadName), path));
    }

    /** First relevant leaf-to-root owner wins; raw frames retain callers/mod ownership. */
    static String category(List<String> frames) {
        for (String frame : frames) {
            if (frame.startsWith("net.vulkanmod.render.chunk.build.")
                    || frame.startsWith("net.minecraft.client.renderer.block.")
                    || frame.startsWith("net.minecraft.client.renderer.chunk.")) return "terrain_mesh";
            if (frame.startsWith("net.vulkanmod.render.chunk.")) return "terrain_visibility_submission";
            if (frame.startsWith("net.vulkanmod.render.texture.")
                    || frame.startsWith("net.vulkanmod.vulkan.texture.")
                    || frame.startsWith("net.minecraft.client.renderer.texture.")
                    || frame.startsWith("com.mojang.blaze3d.platform.NativeImage.")) return "texture_pixels_transfers";
            if (frame.startsWith("net.minecraft.client.particle.")) return "particles";
            if (frame.startsWith("net.minecraft.client.renderer.blockentity.")) return "block_entities";
            if (frame.startsWith("net.minecraft.client.renderer.entity.")) return "entities_models";
            if (frame.startsWith("net.minecraft.client.gui.")) return "gui";
            if (frame.startsWith("net.minecraft.client.renderer.Post")
                    || frame.startsWith("net.minecraft.client.renderer.Effect")
                    || frame.startsWith("net.vulkanmod.vulkan.framebuffer.")) return "post_effects_targets";
            if (frame.startsWith("com.jozufozu.flywheel.")
                    || frame.startsWith("dev.engine_room.flywheel.")
                    || frame.startsWith("net.vulkanmod.render.flywheel.")) return "flywheel";
            if (frame.startsWith("com.mojang.blaze3d.vertex.")
                    || frame.startsWith("net.vulkanmod.render.vertex.")) return "vertex_geometry";
            if (frame.startsWith("org.lwjgl.vulkan.")) return "vulkan_native_boundary";
            if (frame.startsWith("net.vulkanmod.vulkan.")) return "vulkan_preparation";
            if (frame.startsWith("net.minecraft.client.renderer.")
                    || frame.startsWith("net.vulkanmod.render.")
                    || frame.startsWith("com.mojang.blaze3d.")) return "other_graphics";
        }
        return null;
    }

    void emit(Consumer<String> output) {
        output.accept("cpu_graphics_samples status=complete java_execution_samples=" + all[0]
                + " native_boundary_samples=" + all[1]
                + " relevant_java_samples=" + relevant[0] + " relevant_native_samples=" + relevant[1]
                + " excluded_background_samples=" + background + " missing_stack_samples=" + missingStack
                + " truncated_stack_samples=" + truncatedStack + " profiler_observer_samples=" + profilerSamples
                + " unique_stacks=" + stacks.size() + " stack_cap=" + MAX_STACKS
                + " stack_overflow_samples=" + overflow
                + " retained_stack_policy=first_distinct"
                + " semantics=statistical_samples_not_milliseconds native_samples_are_not_cpu_compute_time"
                + " unknown_worker_graphics=not_identifiable_without_known_graphics_caller");
        categories.forEach((name, counts) -> output.accept("cpu_graphics_category owner=" + name
                + " java_samples=" + counts[0] + " native_samples=" + counts[1]));
        // Rank each sample kind separately; their sampling denominators differ.
        for (int kind = 0; kind < 2; kind++) {
            final boolean nativeBoundary = kind == 1;
            List<StackCount> ranked = new ArrayList<>(stacks.values());
            ranked.removeIf(s -> s.nativeBoundary != nativeBoundary);
            ranked.sort(Comparator.comparingLong((StackCount s) -> s.samples).reversed()
                    .thenComparing(s -> s.path));
            long reported = 0L;
            for (int i = 0; i < Math.min(TOP_STACKS, ranked.size()); i++) {
                StackCount stack = ranked.get(i);
                reported += stack.samples;
                output.accept(String.format(Locale.ROOT,
                        "cpu_graphics_hotspot kind=%s rank=%d owner=%s samples=%d pct_relevant_kind=%.3f example_thread=%s stack=%s",
                        nativeBoundary ? "native_boundary" : "java_execution", i + 1, stack.group,
                        stack.samples, relevant[kind] == 0 ? 0D : 100D * stack.samples / relevant[kind],
                        stack.exampleThread, stack.path));
            }
            output.accept("cpu_graphics_hotspots kind=" + (nativeBoundary ? "native_boundary" : "java_execution")
                    + " reported_samples=" + reported + " unreported_samples=" + (relevant[kind] - reported)
                    + " top_stack_cap=" + TOP_STACKS + " frame_cap=" + MAX_FRAMES);
        }
    }

    private static String token(String value) {
        if (value == null) return "unknown";
        return value.replaceAll("[^a-zA-Z0-9_.$/:;<>#()-]", "_");
    }

    private static final class StackCount {
        final boolean nativeBoundary;
        final String group, exampleThread, path;
        long samples = 1L;
        StackCount(boolean nativeBoundary, String group, String exampleThread, String path) {
            this.nativeBoundary = nativeBoundary;
            this.group = group;
            this.exampleThread = exampleThread;
            this.path = path;
        }
    }
}
