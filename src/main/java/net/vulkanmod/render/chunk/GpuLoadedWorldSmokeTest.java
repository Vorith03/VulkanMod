package net.vulkanmod.render.chunk;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.vulkanmod.Initializer;
import net.vulkanmod.render.chunk.voxel.GpuTerrainModelRegistry;
import net.vulkanmod.render.chunk.voxel.GpuTerrainModelTable;
import net.minecraft.world.level.block.Block;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.texture.ScreenshotReadback;

import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Disposable CI world, real workers and original world renderer; never touches an owner world. */
@Mod.EventBusSubscriber(modid=Initializer.MOD_ID, value=Dist.CLIENT)
public final class GpuLoadedWorldSmokeTest {
    private static final boolean ENABLED = Boolean.getBoolean("vulkanmod.ciGpuWorldSmoke");
    private static final boolean GPU = Boolean.getBoolean("vulkanmod.ciGpuWorldExpectedGpu");
    private static final boolean PORTAL = Boolean.getBoolean("vulkanmod.ciGpuWorldPortal");
    private static java.lang.reflect.Method portalRenderCount;
    private static final boolean PREPARE = Boolean.getBoolean("vulkanmod.ciGpuWorldPrepare");
    private static final String WORLD = System.getProperty("vulkanmod.ciGpuWorldLoad",
            "VulkanMod-CI-GPU-" + UUID.randomUUID());
    private static final String[] PHASES = {"initial", "dirty", "recovery", "readopt", "camera", "return", "reload", "reentry"};
    private static int phase;
    private static int state;
    private static long deadline, stableSince, previousModelGeneration, previousSectionGeneration;
    private static int stableFrames;
    private static boolean scenePlaced;
    private static long nextDiagnostic;
    private static CompletableFuture<Void> work;
    private static CompletableFuture<NativeImage> capture;

    private GpuLoadedWorldSmokeTest() {}

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(!ENABLED || event.phase != TickEvent.Phase.END || state == 9) return;
        try { advance(Minecraft.getInstance()); }
        catch(Throwable failure) { fail(failure); }
    }

    private static void advance(Minecraft minecraft) throws Exception {
        if(deadline == 0) deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(180);
        if(System.nanoTime() > deadline)
            throw new AssertionError("Loaded-world timeout: phase=" + phase + " state=" + state);
        if(state == 0) {
            if(minecraft.getOverlay() != null || GpuTerrainModelRegistry.qualifiedStateCount() == 0) return;
            if(minecraft.level != null) throw new AssertionError("CI world creation requires an unloaded client");
            if(PORTAL) {
                if(!net.vulkanmod.compatibility.ImmersivePortalsLevelRendererCompat.isAvailable())
                    throw new AssertionError("Portal control requires the pinned public runtime");
                portalRenderCount = Class.forName("qouteall.imm_ptl.core.render.context_management.RenderStates")
                        .getMethod("getRenderedPortalNum");
            }
            minecraft.options.renderDistance().set(2);
            minecraft.options.simulationDistance().set(2);
            minecraft.options.cloudStatus().set(CloudStatus.OFF);
            minecraft.options.bobView().set(false);
            minecraft.options.hideGui = true;
            if(!PREPARE) {
                if(!WORLD.matches("VulkanMod-CI-GPU-[a-zA-Z0-9-]+")
                        || !Files.isDirectory(Path.of("saves", WORLD)))
                    throw new AssertionError("Paired world must be an existing, exclusively owned CI save");
                minecraft.createWorldOpenFlows().loadLevel(new TitleScreen(), WORLD);
                scenePlaced = true;
                state = 1;
                return;
            }
            minecraft.createWorldOpenFlows().createFreshLevel(WORLD,
                    new LevelSettings(WORLD, GameType.SPECTATOR, false, Difficulty.PEACEFUL,
                            true, new GameRules(), WorldDataConfiguration.DEFAULT),
                    new WorldOptions(0L, false, false), access -> access.registryOrThrow(Registries.WORLD_PRESET)
                            .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
            state = 1;
            return;
        }
        if(state == 1) {
            if(minecraft.level == null || minecraft.player == null || minecraft.getSingleplayerServer() == null
                    || minecraft.getOverlay() != null || minecraft.screen != null) return;
            work = onServer(minecraft, () -> {
                var server = minecraft.getSingleplayerServer();
                var level = server.overworld();
                level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
                level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
                level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
                level.setDayTime(6000);
                level.setWeatherParameters(0, 0, false, false);
                var player = server.getPlayerList().getPlayer(minecraft.player.getUUID());
                if(player == null) throw new AssertionError("CI server player missing");
                player.setGameMode(GameType.SPECTATOR);
                player.connection.teleport(8.0, 86.0, cameraZ(), cameraYaw(), 25);
            });
            state = 2;
            return;
        }
        if(state == 11) {
            minecraft.level.disconnect();
            minecraft.clearLevel();
            Files.createDirectories(Path.of("gpu-world-smoke"));
            Files.writeString(Path.of("gpu-world-smoke", "template-world.txt"), WORLD + "\n");
            state = 9;
            Initializer.LOGGER.info("VULKANMOD_GPU_WORLD_TEMPLATE_OK: {} saved before either paired control", WORLD);
            System.exit(0);
        }
        if(state == 10) {
            if(Math.abs(minecraft.player.getX() - 8) > 0.01
                    || Math.abs(minecraft.player.getY() - 86) > 0.01
                    || !minecraft.level.hasChunkAt(new BlockPos(4, 82, 4))
                    || !minecraft.level.hasChunkAt(new BlockPos(20, 83, 4))
                    || minecraft.level.getBrightness(net.minecraft.world.level.LightLayer.SKY, new BlockPos(4, 82, 4)) != 15
                    || minecraft.level.getBrightness(net.minecraft.world.level.LightLayer.SKY, new BlockPos(20, 83, 4)) != 15
                    || minecraft.level.getLightEngine().hasLightWork()) return;
            var qualified = Block.stateById(GpuTerrainModelTable.captureCurrent().stateIdForTemplate(0));
            work = onServer(minecraft, () -> {
                var level = minecraft.getSingleplayerServer().overworld();
                for(int x = 1; x <= 8; x++) for(int z = 1; z <= 8; z++) {
                    level.setBlockAndUpdate(new BlockPos(x, 81, z), qualified);
                    level.setBlockAndUpdate(new BlockPos(x + 16, 81, z), qualified);
                }
                level.setBlockAndUpdate(new BlockPos(20, 82, 4), Blocks.OAK_SLAB.defaultBlockState());
                if(PORTAL) createPortal(level);
            });
            scenePlaced = true;
            state = 2;
            return;
        }
        if(state == 2) {
            if(!work.isDone()) return;
            work.join();
            if(phase == 0 && !scenePlaced) { state = 10; return; }
            state = 3;
            stableSince = 0;
            stableFrames = 0;
        }
        if(state == 3 && System.nanoTime() >= nextDiagnostic) {
            nextDiagnostic = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            Initializer.LOGGER.info("GPU_WORLD_DRAW_DIAGNOSTIC: {}", RegionBatchStats.describe());
            Initializer.LOGGER.info("GPU_WORLD_CLIENT_LIGHT_WAIT: ordinary={} mixed={} pending={}",
                    minecraft.level.getBrightness(net.minecraft.world.level.LightLayer.SKY, new BlockPos(4, 82, 4)),
                    minecraft.level.getBrightness(net.minecraft.world.level.LightLayer.SKY, new BlockPos(20, 83, 4)),
                    minecraft.level.getLightEngine().hasLightWork());
            var diagnosticServer = minecraft.getSingleplayerServer();
            diagnosticServer.execute(() -> Initializer.LOGGER.info(
                    "GPU_WORLD_SERVER_LIGHT_WAIT: ordinary={} mixed={} pending={}",
                    diagnosticServer.overworld().getBrightness(net.minecraft.world.level.LightLayer.SKY, new BlockPos(4, 82, 4)),
                    diagnosticServer.overworld().getBrightness(net.minecraft.world.level.LightLayer.SKY, new BlockPos(20, 83, 4)),
                    diagnosticServer.overworld().getLightEngine().hasLightWork()));
            for(int x : new int[]{0, 16}) {
                var section = section(x);
                if(section == null) continue;
                var output = section.getChunkArea().getGpuTerrainOutputResidency(section.xOffset(),
                        section.yOffset(), section.zOffset(), RenderSection.gpuTerrainOutputLayer());
                Initializer.LOGGER.info("GPU_WORLD_WAIT phase={} x={} dirty={} compiled={} cpuComplete={} recovery={} dispatchRecovery={} generation={} output={} screen={} cpuIndices={} player=({}, {}, {}) block={} owners={} uploads={} stableFrames={} recording={} noRender={}",
                        phase, x, section.isDirty(), section.isCompiled(), section.gpuTerrainCpuMeshComplete(),
                        section.gpuTerrainCpuRecoveryRequired(), section.gpuTerrainDispatchRecoveryRequired(),
                        section.getVoxelGeneration(), output, minecraft.screen,
                        section.getDrawParameters(RenderSection.gpuTerrainOutputLayer()).indexCount,
                        minecraft.player.getX(), minecraft.player.getY(), minecraft.player.getZ(),
                        minecraft.level.getBlockState(new BlockPos(x + 4, 81, 4)),
                        RegionBatchStats.gpuCommandOwnerReasons[0], RegionBatchStats.commandUploads,
                        stableFrames, Renderer.getInstance().isRecordingFrame(), minecraft.noRender);
            }
        }
        if(state == 4 && capture != null && capture.isDone()) {
            Path output = Path.of("gpu-world-smoke", GPU ? "gpu" : "cpu");
            Files.createDirectories(output);
            try(NativeImage image = capture.join();
                DataOutputStream bytes = new DataOutputStream(Files.newOutputStream(output.resolve(PHASES[phase] + ".rgba")))) {
                image.writeToFile(output.resolve(PHASES[phase] + ".png"));
                bytes.writeInt(image.getWidth()); bytes.writeInt(image.getHeight());
                for(int y = 0; y < image.getHeight(); y++) for(int x = 0; x < image.getWidth(); x++)
                    bytes.writeInt(image.getPixelRGBA(x, y));
            }
            capture = null;
            Initializer.LOGGER.info("VULKANMOD_GPU_LOADED_WORLD_CAPTURE: mode={} phase={} real workers/publication/world draw", GPU, PHASES[phase]);
            phase++;
            deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(180);
            if(phase == 1) {
                previousSectionGeneration = section(0).getVoxelGeneration();
                work = onServer(minecraft, () -> minecraft.getSingleplayerServer().overworld()
                        .setBlockAndUpdate(new BlockPos(4, 81, 4), Blocks.GLASS.defaultBlockState()));
                state = 2;
            } else if(phase == 2) {
                previousSectionGeneration = section(0).getVoxelGeneration();
                if(GPU) {
                    var section = section(0);
                    GpuTerrainSectionMesherBridge.recoverCpuFallback(section.getChunkArea(), section,
                            previousSectionGeneration);
                    if(!section.gpuTerrainDispatchRecoveryRequired())
                        throw new AssertionError("Failed producer must request complete CPU recovery");
                } else {
                    minecraft.levelRenderer.setSectionDirty(0, 5, 0);
                }
                work = CompletableFuture.completedFuture(null);
                state = 2;
            } else if(phase == 3) {
                previousSectionGeneration = section(0).getVoxelGeneration();
                work = onServer(minecraft, () -> minecraft.getSingleplayerServer().overworld()
                        .setBlockAndUpdate(new BlockPos(5, 82, 5), Block.stateById(
                                GpuTerrainModelTable.captureCurrent().stateIdForTemplate(0))));
                state = 2;
            } else if(phase == 4 || phase == 5) {
                work = onServer(minecraft, () -> minecraft.getSingleplayerServer().getPlayerList()
                        .getPlayer(minecraft.player.getUUID()).connection
                        .teleport(8.0, 86.0, cameraZ(), cameraYaw(), 25));
                state = 2;
            } else if(phase == 6) {
                previousModelGeneration = GpuTerrainModelRegistry.generation();
                work = minecraft.reloadResourcePacks();
                state = 2;
            } else if(phase == 7) {
                minecraft.level.disconnect();
                minecraft.clearLevel();
                minecraft.createWorldOpenFlows().loadLevel(new TitleScreen(), WORLD);
                state = 1;
            } else {
                minecraft.level.disconnect();
                minecraft.clearLevel();
                state = 9;
                Initializer.LOGGER.info("VULKANMOD_GPU_LOADED_WORLD_OK: mode={} initial/mixed, dirty REPLACE-to-APPEND, failed-producer CPU recovery/readoption, camera volume change, resource generation reload and save/reentry; deterministic flat scene, controlled public portal={}; private-pack/RX performance untested", GPU, PORTAL);
                System.exit(0);
            }
        }
    }

    @SubscribeEvent public static void render(TickEvent.RenderTickEvent event) {
        if(!ENABLED || event.phase != TickEvent.Phase.END || state != 3) return;
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if(minecraft.level == null || minecraft.player == null || minecraft.screen != null
                    || minecraft.getOverlay() != null || !Renderer.getInstance().isRecordingFrame()) return;
            int portalViews = PORTAL ? (int) portalRenderCount.invoke(null) : 0;
            RenderSection ordinary = section(0), mixed = section(16);
            boolean ready = ordinary != null && mixed != null && ordinary.isCompiled() && mixed.isCompiled()
                    && !ordinary.isDirty() && !mixed.isDirty()
                    && Math.abs(minecraft.player.getX() - 8) < 0.01
                    && Math.abs(minecraft.player.getY() - 86) < 0.01
                    && Math.abs(minecraft.player.getZ() - cameraZ()) < 0.01
                    && Math.abs(net.minecraft.util.Mth.wrapDegrees(minecraft.player.getYRot() - cameraYaw())) < 0.01
                    // Initial light packets can lag real block updates. The scene
                    // has no roof: both identical controls must have full skylight
                    // above the platforms before comparing their rendered result.
                    && (PREPARE || minecraft.level.getBrightness(net.minecraft.world.level.LightLayer.SKY,
                            new BlockPos(4, 82, 4)) == 15
                    && minecraft.level.getBrightness(net.minecraft.world.level.LightLayer.SKY,
                            new BlockPos(20, 83, 4)) == 15
                    && !minecraft.level.getLightEngine().hasLightWork());
            if(PORTAL && phase != 4) ready &= portalViews > 0;
            if(phase >= 1 && phase <= 3) ready &= ordinary != null && ordinary.getVoxelGeneration() > previousSectionGeneration;
            if(phase == 6) ready &= GpuTerrainModelRegistry.generation() > previousModelGeneration;
            if(ready && !GPU) ready &= ordinary.getDrawParameters(RenderSection.gpuTerrainOutputLayer()).indexCount > 0
                    && mixed.getDrawParameters(RenderSection.gpuTerrainOutputLayer()).indexCount > 0;
            if(ready && GPU) {
                for(RenderSection section : new RenderSection[]{ordinary, mixed}) {
                    var output = section.getChunkArea().getGpuTerrainOutputResidency(section.xOffset(),
                            section.yOffset(), section.zOffset(), RenderSection.gpuTerrainOutputLayer());
                    if(phase == 2 && section == ordinary) {
                        ready &= section.gpuTerrainCpuMeshComplete()
                                && !section.gpuTerrainCpuRecoveryRequired()
                                && !section.gpuTerrainDispatchRecoveryRequired()
                                && section.getDrawParameters(RenderSection.gpuTerrainOutputLayer()).indexCount > 84;
                    } else {
                        ready &= output != null && output.valid() && output.faceCount() > 0
                                && output.generation() == section.getVoxelGeneration()
                                && !section.gpuTerrainCpuMeshComplete();
                    }
                }
                ready &= RegionBatchStats.gpuCommandOwnerReasons[0] > 0 && RegionBatchStats.commandUploads == 0;
                if(phase == 0) ready &= ordinary.getDrawParameters(RenderSection.gpuTerrainOutputLayer()).indexCount == 0;
                else ready &= ordinary.getDrawParameters(RenderSection.gpuTerrainOutputLayer()).indexCount > 0;
                ready &= mixed.getDrawParameters(RenderSection.gpuTerrainOutputLayer()).indexCount > 0;
            }
            if(!ready) { stableSince = 0; stableFrames = 0; return; }
            if(stableSince == 0) stableSince = System.nanoTime();
            if(++stableFrames < 5 || System.nanoTime() - stableSince < TimeUnit.SECONDS.toNanos(2)) return;
            if(PREPARE) {
                onServer(minecraft, () -> {
                    if(minecraft.getSingleplayerServer().overworld().getLightEngine().hasLightWork())
                        throw new AssertionError("Template must finish authoritative lighting before save");
                }).get(5, TimeUnit.SECONDS);
                state = 11;
                return;
            }
            Initializer.LOGGER.info("VULKANMOD_GPU_LOADED_WORLD_ADOPTION: mode={} phase={} gpuOwners={} cpuCommandUploads={} ordinaryCpuIndices={} mixedCpuIndices={} ordinaryCpuComplete={} mixedCpuComplete={} portal={} portalViews={}",
                    GPU, PHASES[phase], RegionBatchStats.gpuCommandOwnerReasons[0], RegionBatchStats.commandUploads,
                    ordinary.getDrawParameters(RenderSection.gpuTerrainOutputLayer()).indexCount,
                    mixed.getDrawParameters(RenderSection.gpuTerrainOutputLayer()).indexCount,
                    ordinary.gpuTerrainCpuMeshComplete(), mixed.gpuTerrainCpuMeshComplete(), PORTAL, portalViews);
            int[] sky = new int[4];
            BlockPos[] probes = {new BlockPos(4, 82, 4), new BlockPos(20, 83, 4),
                    new BlockPos(4, 80, 4), new BlockPos(20, 80, 4)};
            for(int i = 0; i < probes.length; i++)
                sky[i] = minecraft.level.getBrightness(net.minecraft.world.level.LightLayer.SKY, probes[i]);
            onServer(minecraft, () -> {
                var level = minecraft.getSingleplayerServer().overworld();
                for(int i = 0; i < probes.length; i++) {
                    if(level.getBrightness(net.minecraft.world.level.LightLayer.SKY, probes[i]) != sky[i])
                        throw new AssertionError("Prepared scene client/server light input differs at " + probes[i]);
                }
            }).get(5, TimeUnit.SECONDS);
            Initializer.LOGGER.info("GPU_WORLD_LIGHT_INPUT: phase={} above=({}, {}) below=({}, {}); agrees with saved authoritative scene",
                    PHASES[phase], sky[0], sky[1], sky[2], sky[3]);
            capture = ScreenshotReadback.request(minecraft.getMainRenderTarget());
            state = 4;
        } catch(Throwable failure) { fail(failure); }
    }

    private static void createPortal(net.minecraft.server.level.ServerLevel level) {
        try {
            var type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getOptional(
                    new net.minecraft.resources.ResourceLocation("immersive_portals", "portal")).orElseThrow();
            var helper = Class.forName("qouteall.imm_ptl.core.portal.PortalManipulation");
            var method = helper.getMethod("createOrthodoxPortal", net.minecraft.world.entity.EntityType.class,
                    net.minecraft.server.level.ServerLevel.class, net.minecraft.server.level.ServerLevel.class,
                    net.minecraft.core.Direction.class, net.minecraft.world.phys.AABB.class,
                    net.minecraft.world.phys.Vec3.class);
            var portal = (net.minecraft.world.entity.Entity) method.invoke(null, type, level, level,
                    net.minecraft.core.Direction.NORTH, new net.minecraft.world.phys.AABB(5, 81.5, -4, 11, 86.5, -4),
                    new net.minecraft.world.phys.Vec3(24, 84, -4));
            portal.getClass().getField("teleportable").setBoolean(portal, false);
            if(!level.addFreshEntity(portal)) throw new AssertionError("Controlled portal was not added");
        } catch(ReflectiveOperationException failure) {
            throw new AssertionError("Pinned public portal API changed", failure);
        }
    }

    private static double cameraZ() { return phase == 4 ? 28.0 : -12.0; }
    private static float cameraYaw() { return phase == 4 ? 180.0F : 0.0F; }

    private static RenderSection section(int x) {
        var world = WorldRenderer.getInstance();
        return world == null || world.getSectionGrid() == null ? null
                : world.getSectionGrid().getSectionAtBlockPos(new BlockPos(x, 80, 0));
    }

    private static CompletableFuture<Void> onServer(Minecraft minecraft, Runnable action) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        minecraft.getSingleplayerServer().execute(() -> {
            try { action.run(); result.complete(null); }
            catch(Throwable failure) { result.completeExceptionally(failure); }
        });
        return result;
    }

    private static void fail(Throwable failure) {
        state = 9;
        Initializer.LOGGER.error("GPU loaded-world smoke failed phase=" + phase, failure);
        System.exit(1);
    }
}
