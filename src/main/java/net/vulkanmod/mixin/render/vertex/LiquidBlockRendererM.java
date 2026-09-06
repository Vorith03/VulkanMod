package net.vulkanmod.mixin.render.vertex;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.block.LiquidBlockRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LiquidBlockRenderer.class)
public class LiquidBlockRendererM {

    /**
     * Forge 47.3.0 patches LiquidBlockRenderer#vertex with an alpha argument.
     * Its runtime descriptor places that sixth float before the packed-light int:
     * (VertexConsumer, double, double, double, float, float, float, float, float,
     * float, int). The patched descriptor is not present in Mojang's original
     * mapping table, so target both development and production names without
     * asking Mixin's annotation processor to remap it.
     */
    @Inject(method = {"vertex", "m_110984_"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void vulkanmod$vertex(VertexConsumer vertexConsumer, double d, double e, double f,
                                  float red, float green, float blue, float u, float v,
                                  float alpha, int light, CallbackInfo ci) {
        vertexConsumer.vertex((float) d, (float) e, (float) f,
                red, green, blue, alpha, u, v, 0, light, 0.0F, 1.0F, 0.0F);
        ci.cancel();
    }
}
