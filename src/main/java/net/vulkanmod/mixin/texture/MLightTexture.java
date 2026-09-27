package net.vulkanmod.mixin.texture;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.vulkanmod.interfaces.VAbstractTextureI;
import net.vulkanmod.vulkan.texture.VTextureSelector;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(LightTexture.class)
public class MLightTexture {

    @Shadow @Final private DynamicTexture lightTexture;

    /**
     * Preserve vanilla's authoritative shader sampler slot while also mirroring
     * the resolved Vulkan image. ShaderTextureState reconciles fixed samplers
     * from RenderSystem immediately before ordinary draws, so leaving slot 2 at
     * zero would erase the valid Vulkan lightmap binding we install here.
     *
     * @author
     */
    @Overwrite
    public void turnOnLightLayer() {
        RenderSystem.setShaderTexture(2, this.lightTexture.getId());
        VTextureSelector.setLightTexture(((VAbstractTextureI)this.lightTexture).getVulkanImage());
    }
}
