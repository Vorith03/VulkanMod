package net.vulkanmod.mixin.compatibility;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * DH's Forge lightmap listener uploads a second OpenGL texture during vanilla's
 * LightTexture update, even when its LOD draw calls are suppressed. The upload
 * queries GL_ACTIVE_TEXTURE and aborts the JVM under VulkanMod's NO_API window.
 * Vanilla's own lightmap update still runs; only DH's OpenGL copy is skipped.
 */
@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.common.wrappers.minecraft.MinecraftRenderWrapper_forge", remap = false)
public abstract class DistantHorizonsLightmapMixin {
    @Inject(method = "updateLightmap", at = @At("HEAD"), cancellable = true, require = 1)
    private void vulkanmod$skipUnsupportedLightmapUpload(CallbackInfo ci) {
        ci.cancel();
    }
}
