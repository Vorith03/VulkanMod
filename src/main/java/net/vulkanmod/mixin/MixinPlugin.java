package net.vulkanmod.mixin;

import net.vulkanmod.config.Config;
import net.vulkanmod.config.VideoResolution;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MixinPlugin implements IMixinConfigPlugin {

    private static final String POST_CHAIN_SMOKE_MIXIN =
            "net.vulkanmod.mixin.render.GameRendererPostChainSmokeMixin";
    private static final String GPU_TERRAIN_ASYNC_SMOKE_MIXIN =
            "net.vulkanmod.mixin.debug.GpuTerrainAsyncCompletionSmokeMixin";
    private static final String PARTICLE_ATTRIBUTION_MIXIN =
            "net.vulkanmod.mixin.profiling.ParticleEngineAttributionMixin";
    private static final String IMMERSIVE_PORTALS_LEVEL_RENDERER_MIXIN =
            "net.vulkanmod.mixin.compatibility.ImmersivePortalsLevelRendererMixin";
    private static final String LEVEL_RENDERER_TARGET = "net.minecraft.client.renderer.LevelRenderer";
    private static final String IP_TERRAIN_OVERRIDE_METHOD = "ip_allowOverrideTerrainSetup";
    private static final String CHUNK_RENDER_DISPATCHER_OWNER =
            "net/minecraft/client/renderer/chunk/ChunkRenderDispatcher";
    private static final String CHUNK_RENDER_DISPATCHER_VEC3_VOID_DESC =
            "(Lnet/minecraft/world/phys/Vec3;)V";
    private static final String IP_DISPATCHER_CAMERA_PATCH_PROPERTY =
            "vulkanmod.immersivePortalsDispatcherCameraPatched";
    private static final String POST_CHAIN_SMOKE_PROPERTY = "vulkanmod.ciPostChainSmoke";
    private static final String DEPTH_POST_CHAIN_SMOKE_PROPERTY = "vulkanmod.ciDepthPostChainSmoke";
    private static final String VULKAN_SMOKE_PROPERTY = "vulkanmod.smokeTest";
    private static Config config;

    @Override
    public void onLoad(String mixinPackage) {
        config = Config.load(new File("./config/vulkanmod_settings.json").toPath().toAbsolutePath());
        if(config == null) config = new Config();
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {

        if(("net.vulkanmod.mixin.render.WorldRenderScaleMixin".equals(mixinClassName)
                || "net.vulkanmod.mixin.render.WorldRenderScaleCleanupMixin".equals(mixinClassName))
                && immersivePortalsClassesPresent()) {
            // IP redirects the same renderLevel invocation. Scaling already
            // retains native resolution for IP; preserve its original hook at
            // transformation time instead of competing with a disabled wrapper.
            return false;
        }

        if(POST_CHAIN_SMOKE_MIXIN.equals(mixinClassName)
                && !Boolean.getBoolean(POST_CHAIN_SMOKE_PROPERTY)
                && !Boolean.getBoolean(DEPTH_POST_CHAIN_SMOKE_PROPERTY)
                && !Boolean.getBoolean("vulkanmod.ciScreenshotSmoke")) {
            return false;
        }

        if(GPU_TERRAIN_ASYNC_SMOKE_MIXIN.equals(mixinClassName)
                && !Boolean.getBoolean(VULKAN_SMOKE_PROPERTY)) {
            return false;
        }

        // Drop the per-particle Mixin callbacks entirely in normal gameplay.
        // Broad client tick attribution remains available independently.
        if(PARTICLE_ATTRIBUTION_MIXIN.equals(mixinClassName)
                && !(Boolean.getBoolean("vulkanmod.performanceProfiler")
                        && Boolean.getBoolean("vulkanmod.performanceProfiler.autoBenchmark"))) {
            return false;
        }

        if(mixinClassName.startsWith("net.vulkanmod.mixin.gui") && !config.guiOptimizations) {
            return false;
        }

        return true;
    }

    private static boolean immersivePortalsClassesPresent() {
        try {
            MixinService.getService().getBytecodeProvider()
                    .getClassNode("qouteall.imm_ptl.core.IPGlobal");
            return true;
        } catch(ClassNotFoundException | IOException absent) {
            return false;
        }
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {

    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        if(!LEVEL_RENDERER_TARGET.equals(targetClassName)
                || !IMMERSIVE_PORTALS_LEVEL_RENDERER_MIXIN.equals(mixinClassName)) {
            return;
        }

        boolean immersivePortalsPresent = false;
        for(MethodNode method : targetClass.methods) {
            if(IP_TERRAIN_OVERRIDE_METHOD.equals(method.name) && "()Z".equals(method.desc)) {
                immersivePortalsPresent = true;
                break;
            }
        }
        if(!immersivePortalsPresent) {
            return;
        }

        MethodNode globalMatchedMethod = null;
        MethodInsnNode globalMatchedInvocation = null;
        MethodNode contextualMatchedMethod = null;
        MethodInsnNode contextualMatchedInvocation = null;
        int globalMatches = 0;
        int contextualMatches = 0;

        for(MethodNode method : targetClass.methods) {
            boolean invokesIpTerrainOverride = invokesIpTerrainOverride(method);

            for(AbstractInsnNode instruction = method.instructions.getFirst();
                instruction != null;
                instruction = instruction.getNext()) {
                if(!isChunkDispatcherCameraInvocation(instruction)) {
                    continue;
                }

                globalMatches++;
                globalMatchedMethod = method;
                globalMatchedInvocation = (MethodInsnNode) instruction;

                if(invokesIpTerrainOverride) {
                    contextualMatches++;
                    contextualMatchedMethod = method;
                    contextualMatchedInvocation = (MethodInsnNode) instruction;
                }
            }
        }

        MethodNode matchedMethod;
        MethodInsnNode matchedInvocation;
        if(globalMatches == 1) {
            // Keep the original exact-one contract for older IP shapes where no
            // additional LevelRenderer dispatcher call makes the target ambiguous.
            matchedMethod = globalMatchedMethod;
            matchedInvocation = globalMatchedInvocation;
        } else if(globalMatches > 1 && contextualMatches == 1) {
            // IP 3.0.7 composes a second, unrelated dispatcher camera call into
            // LevelRenderer. Its portal terrain callback is distinguishable by
            // the call to IP's merged terrain-override helper; select only the
            // dispatcher invocation in that structural context.
            matchedMethod = contextualMatchedMethod;
            matchedInvocation = contextualMatchedInvocation;
        } else {
            throw new IllegalStateException(
                    "Expected exactly one selectable Immersive Portals ChunkRenderDispatcher(Vec3) call in LevelRenderer; found "
                            + globalMatches + " total and " + contextualMatches
                            + " in methods invoking " + IP_TERRAIN_OVERRIDE_METHOD + "()Z");
        }

        if(matchedMethod == null || matchedInvocation == null) {
            throw new IllegalStateException(
                    "Immersive Portals dispatcher camera selector produced no rewrite target");
        }

        // Immersive Portals updates the vanilla chunk dispatcher before asking
        // whether it may replace terrain setup. VulkanMod intentionally has no
        // vanilla dispatcher and consumes the same transformed Camera/Frustum in
        // its WorldRenderer setup path. Discard the dispatcher + Vec3 operands.
        // This runs from postApply, after all mixin injection passes, because a
        // normal redirect cannot reliably select another mixin's injected handler.
        matchedMethod.instructions.set(matchedInvocation, new InsnNode(Opcodes.POP2));
        System.setProperty(IP_DISPATCHER_CAMERA_PATCH_PROPERTY, "true");
    }

    private static boolean invokesIpTerrainOverride(MethodNode method) {
        for(AbstractInsnNode instruction = method.instructions.getFirst();
            instruction != null;
            instruction = instruction.getNext()) {
            if(instruction instanceof MethodInsnNode invocation
                    && IP_TERRAIN_OVERRIDE_METHOD.equals(invocation.name)
                    && "()Z".equals(invocation.desc)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isChunkDispatcherCameraInvocation(AbstractInsnNode instruction) {
        return instruction instanceof MethodInsnNode invocation
                && invocation.getOpcode() == Opcodes.INVOKEVIRTUAL
                && CHUNK_RENDER_DISPATCHER_OWNER.equals(invocation.owner)
                && CHUNK_RENDER_DISPATCHER_VEC3_VOID_DESC.equals(invocation.desc);
    }
}
