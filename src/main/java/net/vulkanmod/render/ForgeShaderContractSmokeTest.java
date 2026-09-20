package net.vulkanmod.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.vulkanmod.Initializer;
import net.vulkanmod.compatibility.ImmersivePortalsShaderCompat;
import net.vulkanmod.interfaces.ShaderMixed;

import java.io.IOException;
import java.util.Map;

/**
 * Real Forge shader-registration/reload oracle, active only in the opt-in CI
 * startup smoke. It intentionally uses Forge's namespaced ShaderInstance API.
 */
@Mod.EventBusSubscriber(modid = Initializer.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ForgeShaderContractSmokeTest {
    private static final ResourceLocation TEST_SHADER =
            new ResourceLocation(Initializer.MOD_ID, "ci_namespaced");
    private static final ResourceLocation IP_ALIASED_TERRAIN_SHADER =
            new ResourceLocation(Initializer.MOD_ID, "ci_ip_alias");
    private static final ResourceLocation IP_ALIASED_MODEL_VIEW_SHADER =
            new ResourceLocation(Initializer.MOD_ID, "ci_ip_model_view_alias");
    private static final String STALE_KEY = Initializer.MOD_ID + ":ci_stale_reload_entry";

    private static ShaderInstance registeredShader;
    private static ShaderInstance loadedShader;
    private static ShaderInstance registeredIpAliasedTerrainShader;
    private static ShaderInstance loadedIpAliasedTerrainShader;
    private static ShaderInstance registeredIpAliasedModelViewShader;
    private static ShaderInstance loadedIpAliasedModelViewShader;
    private static boolean sawEvent;

    private ForgeShaderContractSmokeTest() {
    }

    public static void prepareReload(Map<String, ShaderInstance> shaders) {
        if(!Boolean.getBoolean("vulkanmod.smokeTest")) {
            return;
        }

        require(!shaders.isEmpty(),
                "Shader reload smoke expected the preloaded shader map to be non-empty");
        registeredShader = null;
        loadedShader = null;
        registeredIpAliasedTerrainShader = null;
        loadedIpAliasedTerrainShader = null;
        registeredIpAliasedModelViewShader = null;
        loadedIpAliasedModelViewShader = null;
        sawEvent = false;

        // A correct reload replaces the map. Keeping this alias would reproduce
        // the audited stale-entry bug even if every new shader happened to load.
        shaders.put(STALE_KEY, shaders.values().iterator().next());
    }

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) {
        if(!Boolean.getBoolean("vulkanmod.smokeTest")) {
            return;
        }

        sawEvent = true;
        try {
            ShaderInstance shader = new ShaderInstance(
                    event.getResourceProvider(), TEST_SHADER, DefaultVertexFormat.POSITION_COLOR);
            registeredShader = shader;
            event.registerShader(shader, loaded -> {
                require(loaded == registeredShader,
                        "RegisterShadersEvent callback received a different shader instance");
                loadedShader = loaded;
            });

            if(Boolean.getBoolean("vulkanmod.ciImmersivePortalsSmoke")) {
                ShaderInstance ipAliasedTerrainShader = new ShaderInstance(
                        event.getResourceProvider(),
                        IP_ALIASED_TERRAIN_SHADER,
                        DefaultVertexFormat.BLOCK
                );
                registeredIpAliasedTerrainShader = ipAliasedTerrainShader;
                event.registerShader(ipAliasedTerrainShader, loaded -> {
                    require(loaded == registeredIpAliasedTerrainShader,
                            "IP aliased-terrain callback received a different shader instance");
                    loadedIpAliasedTerrainShader = loaded;
                });

                ShaderInstance ipAliasedModelViewShader = new ShaderInstance(
                        event.getResourceProvider(),
                        IP_ALIASED_MODEL_VIEW_SHADER,
                        DefaultVertexFormat.NEW_ENTITY
                );
                registeredIpAliasedModelViewShader = ipAliasedModelViewShader;
                event.registerShader(ipAliasedModelViewShader, loaded -> {
                    require(loaded == registeredIpAliasedModelViewShader,
                            "IP aliased-model-view callback received a different shader instance");
                    loadedIpAliasedModelViewShader = loaded;
                });
            }
        } catch(IOException e) {
            throw new IllegalStateException("Could not create namespaced Forge shader smoke fixture", e);
        }
    }

    public static void verifyReload(Map<String, ShaderInstance> shaders) {
        if(!Boolean.getBoolean("vulkanmod.smokeTest")) {
            return;
        }

        require(sawEvent, "Forge RegisterShadersEvent was not posted");
        require(registeredShader != null, "Forge shader listener did not register its shader");
        require(loadedShader == registeredShader, "Forge registered-shader callback did not run");
        require(!shaders.containsKey(STALE_KEY), "Shader reload retained a stale map entry");
        require(shaders.get(TEST_SHADER.toString()) == loadedShader,
                "Namespaced Forge shader was not installed under its authoritative name");
        require(((ShaderMixed)(Object)loadedShader).getPipeline() != null,
                "Namespaced Forge shader did not create a Vulkan pipeline");

        if(Boolean.getBoolean("vulkanmod.ciImmersivePortalsSmoke")) {
            require(registeredIpAliasedTerrainShader != null,
                    "IP aliased-terrain shader was not registered");
            require(loadedIpAliasedTerrainShader == registeredIpAliasedTerrainShader,
                    "IP aliased-terrain registered-shader callback did not run");
            require(shaders.get(IP_ALIASED_TERRAIN_SHADER.toString()) == loadedIpAliasedTerrainShader,
                    "IP aliased-terrain shader was not installed under its authoritative name");
            require(((ShaderMixed)(Object)loadedIpAliasedTerrainShader).getPipeline() != null,
                    "IP aliased-terrain shader did not create a Vulkan pipeline");

            require(registeredIpAliasedModelViewShader != null,
                    "IP aliased-model-view shader was not registered");
            require(loadedIpAliasedModelViewShader == registeredIpAliasedModelViewShader,
                    "IP aliased-model-view registered-shader callback did not run");
            require(shaders.get(IP_ALIASED_MODEL_VIEW_SHADER.toString()) == loadedIpAliasedModelViewShader,
                    "IP aliased-model-view shader was not installed under its authoritative name");
            require(((ShaderMixed)(Object)loadedIpAliasedModelViewShader).getPipeline() != null,
                    "IP aliased-model-view shader did not create a Vulkan pipeline");

            // Force initialization of the exact IP 3.0.7 entity/weather clipping
            // reflection bridge. Clipping is inactive in this startup smoke, so the
            // authoritative disabled value must remain (0, 0, 0, 1).
            ImmersivePortalsShaderCompat.refreshModelViewClipPlane();
            require(ImmersivePortalsShaderCompat.getModelViewClipPlane().getFloat(0) == 0.0f
                            && ImmersivePortalsShaderCompat.getModelViewClipPlane().getFloat(Float.BYTES) == 0.0f
                            && ImmersivePortalsShaderCompat.getModelViewClipPlane().getFloat(2 * Float.BYTES) == 0.0f
                            && ImmersivePortalsShaderCompat.getModelViewClipPlane().getFloat(3 * Float.BYTES) == 1.0f,
                    "IP aliased-model-view disabled clip plane was not authoritative");
        }

        Initializer.LOGGER.info("Forge shader registration/reload contract smoke passed");
    }

    private static void require(boolean condition, String message) {
        if(!condition) {
            throw new AssertionError(message);
        }
    }
}
