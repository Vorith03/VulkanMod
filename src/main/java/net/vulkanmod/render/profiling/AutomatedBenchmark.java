package net.vulkanmod.render.profiling;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.vulkanmod.Initializer;
import net.vulkanmod.render.chunk.WorldRenderer;
import net.vulkanmod.render.chunk.voxel.RegionVoxelStore;

import java.util.Locale;
import java.util.UUID;

/** Optional, single-player-only stationary capture of one explicitly named world. */
public final class AutomatedBenchmark {
    private static final boolean ENABLED = Boolean.getBoolean("vulkanmod.performanceProfiler")
            && Boolean.getBoolean("vulkanmod.performanceProfiler.autoBenchmark");
    private static final String WORLD_NAME = System.getProperty(
            "vulkanmod.performanceProfiler.benchmarkWorld", "VulkanMod Benchmark");
    private static final double X = coordinate("benchmarkX", 0.0D);
    private static final double Y = coordinate("benchmarkY", 192.0D);
    private static final double Z = coordinate("benchmarkZ", 0.0D);
    private static final float YAW = angle("benchmarkYaw", -90.0F, -360.0F, 360.0F);
    private static final float PITCH = angle("benchmarkPitch", 30.0F, -90.0F, 90.0F);
    private static final long SETTLE_NANOS = seconds("benchmarkSettleSeconds", 60.0D, 0.0D, 600.0D);
    private static final long CAPTURE_NANOS = seconds("durationSeconds", 180.0D, 10.0D, 3600.0D);
    private static final long AFTER_CAP_NANOS = seconds("benchmarkAfterStagingCapSeconds", 60.0D, 0.0D, 600.0D);
    private static final long TELEPORT_TIMEOUT_NANOS = 30_000_000_000L;

    private enum State { WAITING, TELEPORTING, WAIT_TERRAIN, SETTLING, CAPTURING, SAVING, STOPPING, FINISHED, ABORTED }
    private static State state = State.WAITING;
    private static volatile boolean teleportApplied;
    private static volatile boolean teleportFailed;
    private static long teleportRequestedAt;
    private static long terrainAppearedAt;
    private static long captureStartedAt;
    private static long capReachedAt;
    private static long nextStagingSampleAt;

    private AutomatedBenchmark() {}

    public static boolean enabled() { return ENABLED; }

    /** Runs on the render thread, before the profiler's runTick frame starts. */
    public static void onFrameStart(Minecraft minecraft) {
        if (!ENABLED || minecraft == null || state == State.FINISHED || state == State.ABORTED) return;
        if (state == State.STOPPING) {
            IntegratedServer server = minecraft.getSingleplayerServer();
            if (minecraft.level == null && (server == null || server.isStopped())) {
                state = State.FINISHED;
                minecraft.stop();
            }
            return;
        }
        if (state == State.SAVING) return;

        IntegratedServer server = minecraft.getSingleplayerServer();
        if (state == State.WAITING) {
            if (!readyInTargetWorld(minecraft, server)) return;
            scheduleTeleport(minecraft, server);
            return;
        }
        if (!readyInTargetWorld(minecraft, server)) {
            abort("left target world or opened a screen before benchmark completion");
            return;
        }

        long now = System.nanoTime();
        if (teleportFailed || state == State.TELEPORTING && now - teleportRequestedAt > TELEPORT_TIMEOUT_NANOS) {
            abort("server teleport did not complete within 30 seconds");
            return;
        }
        if (state == State.TELEPORTING) {
            if (teleportApplied && atTargetPose(minecraft)) state = State.WAIT_TERRAIN;
            return;
        }
        if (!atTargetPose(minecraft)) {
            abort("player moved or changed camera after benchmark teleport");
            return;
        }
        sampleStaging(now);
        if (state == State.WAIT_TERRAIN) {
            WorldRenderer renderer = WorldRenderer.getInstance();
            if (renderer != null && renderer.getLevel() == minecraft.level
                    && renderer.performanceCounters().nonEmptySections() > 0) {
                terrainAppearedAt = now;
                state = State.SETTLING;
                Initializer.LOGGER.info("VulkanMod benchmark terrain visible; settling for {} seconds",
                        SETTLE_NANOS / 1_000_000_000.0D);
            } else if (now - teleportRequestedAt > 120_000_000_000L) {
                abort("no terrain appeared at the target view within 120 seconds of teleport");
            }
            return;
        }
        if (state == State.SETTLING && now - terrainAppearedAt >= SETTLE_NANOS) {
            captureStartedAt = now;
            state = State.CAPTURING;
            PerformanceProfiler.armAutomatedCapture();
            Initializer.LOGGER.info("VulkanMod benchmark capture starting in world '{}' at {}, {}, {} yaw {} pitch {}",
                    WORLD_NAME, X, Y, Z, YAW, PITCH);
        }
    }

    /** Runs after the sampled frame has ended, so save/quit cannot enter its timing. */
    public static void onFrameEnd(Minecraft minecraft) {
        if (!ENABLED || state != State.CAPTURING || minecraft == null) return;
        if (!readyInTargetWorld(minecraft, minecraft.getSingleplayerServer()) || !atTargetPose(minecraft)) {
            abort("target view changed during the measured frame");
            return;
        }
        long now = System.nanoTime();
        if (now < captureEndAt(captureStartedAt, capReachedAt, CAPTURE_NANOS, AFTER_CAP_NANOS)) return;

        state = State.SAVING;
        PerformanceProfiler.benchmarkEvent(String.format(Locale.ROOT,
                "complete world=%s measured_s=%.3f staging_cap_seen=%s after_cap_s=%.3f",
                WORLD_NAME.replace(' ', '_'), (now - captureStartedAt) / 1_000_000_000.0D,
                capReachedAt != 0L, capReachedAt == 0L ? -1.0D : (now - capReachedAt) / 1_000_000_000.0D));
        PerformanceProfiler.finishAutomatedCapture("benchmark_complete");
        try {
            // Match the single-player pause menu's normal world teardown path.
            minecraft.level.disconnect();
            minecraft.clearLevel();
            minecraft.setScreen(new TitleScreen());
            state = State.STOPPING;
        } catch (RuntimeException failure) {
            state = State.ABORTED;
            Initializer.LOGGER.error("VulkanMod benchmark capture saved, but automatic world exit failed", failure);
        }
    }

    public static String statusLine() {
        if (!ENABLED) return null;
        long now = System.nanoTime();
        return switch (state) {
            case WAITING -> "VulkanMod benchmark: waiting for " + WORLD_NAME;
            case TELEPORTING, WAIT_TERRAIN -> "VulkanMod benchmark: placing camera / loading terrain";
            case SETTLING -> "VulkanMod benchmark: settling "
                    + Math.max(0L, (terrainAppearedAt + SETTLE_NANOS - now + 999_999_999L) / 1_000_000_000L) + "s";
            case CAPTURING -> "VulkanMod benchmark: recording "
                    + Math.max(0L, (captureEndAt(captureStartedAt, capReachedAt, CAPTURE_NANOS, AFTER_CAP_NANOS)
                    - now + 999_999_999L)
                    / 1_000_000_000L) + "s";
            case SAVING, STOPPING -> "VulkanMod benchmark: saving and exiting";
            case ABORTED -> "VulkanMod benchmark stopped; see latest.log";
            case FINISHED -> null;
        };
    }

    public static String captureMetadata() {
        return String.format(Locale.ROOT,
                "[VulkanModPerf] benchmark_config world=%s dimension=minecraft:overworld xyz=%.3f,%.3f,%.3f yaw=%.2f pitch=%.2f spectator=true settle_s=%.3f capture_s=%.3f after_cap_s=%.3f auto_save_exit=true",
                WORLD_NAME.replace(' ', '_'), X, Y, Z, YAW, PITCH,
                SETTLE_NANOS / 1_000_000_000.0D, CAPTURE_NANOS / 1_000_000_000.0D,
                AFTER_CAP_NANOS / 1_000_000_000.0D);
    }

    private static boolean readyInTargetWorld(Minecraft minecraft, IntegratedServer server) {
        return server != null && minecraft.level != null && minecraft.player != null
                && minecraft.screen == null && minecraft.level.dimension() == Level.OVERWORLD
                && WORLD_NAME.equals(server.getWorldData().getLevelName());
    }

    private static void scheduleTeleport(Minecraft minecraft, IntegratedServer server) {
        UUID playerId = minecraft.player.getUUID();
        teleportRequestedAt = System.nanoTime();
        state = State.TELEPORTING;
        server.execute(() -> {
            try {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player == null || player.level().dimension() != Level.OVERWORLD) {
                    teleportFailed = true;
                    return;
                }
                CommandSourceStack source = player.createCommandSourceStack().withPermission(4);
                server.getCommands().performPrefixedCommand(source, "gamemode spectator");
                int teleported = server.getCommands().performPrefixedCommand(source, String.format(Locale.ROOT,
                        "tp %.3f %.3f %.3f %.2f %.2f", X, Y, Z, YAW, PITCH));
                teleportApplied = teleported > 0;
                teleportFailed = !teleportApplied;
            } catch (RuntimeException failure) {
                teleportFailed = true;
                Initializer.LOGGER.error("VulkanMod benchmark could not place the player", failure);
            }
        });
    }

    private static boolean atTargetPose(Minecraft minecraft) {
        return minecraft.player.isSpectator()
                && Math.abs(minecraft.player.getX() - X) < 0.1D
                && Math.abs(minecraft.player.getY() - Y) < 0.1D
                && Math.abs(minecraft.player.getZ() - Z) < 0.1D
                && Math.abs(Math.IEEEremainder(minecraft.player.getYRot() - YAW, 360.0D)) < 0.1D
                && Math.abs(minecraft.player.getXRot() - PITCH) < 0.1D;
    }

    private static void sampleStaging(long now) {
        if (capReachedAt != 0L || now < nextStagingSampleAt || !RegionVoxelStore.ENABLED) return;
        nextStagingSampleAt = now + 1_000_000_000L;
        RegionVoxelStore.StagingCounters staging = RegionVoxelStore.stagingCounters();
        if (staging.entries() >= staging.maxEntries()) {
            capReachedAt = now;
            PerformanceProfiler.benchmarkEvent("staging_entry_cap_reached=true");
        }
    }

    private static void abort(String reason) {
        state = State.ABORTED;
        PerformanceProfiler.abortAutomatedCapture();
        Initializer.LOGGER.warn("VulkanMod automated benchmark stopped without exiting: {}", reason);
    }

    private static long captureEndAt(long start, long cap, long duration, long afterCap) {
        return Math.max(start + duration, cap == 0L ? 0L : Math.max(start, cap) + afterCap);
    }

    /** Smoke oracle for the optional staging-cap extension, without a world fixture. */
    public static void verifyForCi() {
        if (captureEndAt(100L, 0L, 200L, 60L) != 300L
                || captureEndAt(100L, 260L, 200L, 60L) != 320L
                || captureEndAt(100L, 150L, 200L, 60L) != 300L
                || captureEndAt(100L, 50L, 10L, 60L) != 160L) {
            throw new IllegalStateException("Automated benchmark capture deadline is invalid");
        }
    }

    private static double coordinate(String suffix, double fallback) {
        try {
            double value = Double.parseDouble(System.getProperty("vulkanmod.performanceProfiler." + suffix,
                    Double.toString(fallback)));
            return Double.isFinite(value) && Math.abs(value) < 30_000_000.0D ? value : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static float angle(String suffix, float fallback, float min, float max) {
        try {
            float value = Float.parseFloat(System.getProperty("vulkanmod.performanceProfiler." + suffix,
                    Float.toString(fallback)));
            return Float.isFinite(value) && value >= min && value <= max ? value : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static long seconds(String suffix, double fallback, double min, double max) {
        try {
            double value = Double.parseDouble(System.getProperty("vulkanmod.performanceProfiler." + suffix,
                    Double.toString(fallback)));
            if (Double.isFinite(value) && value >= min && value <= max)
                return (long) (value * 1_000_000_000.0D);
        } catch (NumberFormatException ignored) {
        }
        return (long) (fallback * 1_000_000_000.0D);
    }
}
