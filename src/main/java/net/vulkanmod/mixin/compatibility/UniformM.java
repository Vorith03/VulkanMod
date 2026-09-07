package net.vulkanmod.mixin.compatibility;

import com.mojang.blaze3d.shaders.Uniform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Uniform.class)
public class UniformM {

    /**
     * @author
     * @reason
     */
    @Overwrite
    public static int glGetUniformLocation(int i, CharSequence charSequence) {
        //TODO
        return 1;
    }

    /**
     * @author
     * @reason
     */
    @Overwrite
    public static int glGetAttribLocation(int i, CharSequence charSequence) {
        return 0;
    }

    /**
     * Vanilla ShaderInstance binds vertex attributes into an OpenGL program
     * during construction. VulkanMod supplies its own graphics pipeline and does
     * not use that OpenGL program state, so this must remain a no-op when no GL
     * context exists (for example Forge with its early splash disabled).
     *
     * @author
     * @reason Vulkan does not use OpenGL attribute bindings.
     */
    @Overwrite
    public static void glBindAttribLocation(int program, int index, CharSequence name) {
    }

    /**
     * EffectInstance.apply uploads each sampler's texture-unit index through this
     * static OpenGL helper before it uploads the ordinary Uniform instances. The
     * Vulkan effect path binds samplers through EffectRenderState instead and has
     * no OpenGL program/context, so the vanilla sampler upload must be a no-op too.
     *
     * @author
     * @reason Vulkan descriptor bindings replace OpenGL sampler uniforms.
     */
    @Overwrite
    public static void uploadInteger(int location, int value) {
    }

    @Inject(method = "upload", at = @At("HEAD"), cancellable = true)
    public void cancelUpload(CallbackInfo ci) {
        ci.cancel();
    }
}
