package net.vulkanmod.mixin.screen;

import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.vulkanmod.config.OptionScreenV;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.function.Supplier;

@Mixin(OptionsScreen.class)
public class OptionsScreenM extends Screen {

    @Shadow @Final private Screen lastScreen;

    @Shadow @Final private Options options;

    protected OptionsScreenM(Component title) {
        super(title);
    }

    @ModifyVariable(method = "openScreenButton", at = @At("HEAD"), argsOnly = true)
    private Supplier<Screen> replaceVideoScreenSupplier(Supplier<Screen> screenSupplier, Component message) {
        if (message.equals(Component.translatable("options.video"))) {
            return () -> new OptionScreenV(Component.literal("Video Setting"), this);
        }

        return screenSupplier;
    }
}
