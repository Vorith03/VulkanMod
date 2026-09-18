package net.vulkanmod.mixin.compatibility;

import com.mojang.blaze3d.preprocessor.GlslPreprocessor;
import com.mojang.blaze3d.shaders.Program;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.InputStream;

@Mixin(Program.class)
public class ProgramM {

    /**
     * Keep vanilla's compileShaderInternal bytecode structurally available to
     * third-party shader mixins while preventing an unsupported OpenGL compile
     * from running under VulkanMod.
     *
     * VulkanMod's ShaderInstance and EffectInstance mixins own the real Vulkan
     * shader compilation paths. Returning the same synthetic program id used by
     * the former overwrite therefore preserves VulkanMod's runtime contract,
     * but unlike an overwrite leaves source-read call sites available for mods
     * such as Immersive Portals to transform safely during mixin application.
     */
    @Inject(method = "compileShaderInternal", at = @At("HEAD"), cancellable = true)
    private static void vulkanmod$skipOpenGlShaderCompile(
            Program.Type type,
            String name,
            InputStream shaderData,
            String sourceName,
            GlslPreprocessor preprocessor,
            CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(0);
    }
}
