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
     * Forge patches LiquidBlockRenderer#vertex by appending an alpha argument.
     * The patched descriptor is not present in Mojang's original mapping table,
     * so target both the development and production SRG names without asking
     * Mixin's annotation processor to remap the patched descriptor.
     */
    @Inject(method = {"vertex", "m_110984_"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void vulkanmod$vertex(VertexConsumer vertexConsumer, double d, double e, double f,
                                  float red, float green, float blue, float u, float v,
                                  int light, float alpha, CallbackInfo ci) {
        vertexConsumer.vertex((float) d, (float) e, (float) f,
                red, green, blue, alpha, u, v, 0, light, 0.0F, 1.0F, 0.0F);
        ci.cancel();
    }
}
