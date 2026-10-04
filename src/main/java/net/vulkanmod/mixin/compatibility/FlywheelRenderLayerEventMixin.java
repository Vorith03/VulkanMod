package net.vulkanmod.mixin.compatibility;

import com.mojang.blaze3d.vertex.PoseStack;
import net.vulkanmod.interfaces.LegacyFlywheelEventMatrices;
import net.vulkanmod.render.instancing.LegacyFlywheelEventCapture;
import net.minecraft.client.multiplayer.ClientLevel;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Numeric event snapshot only; this does not activate Flywheel or change its event stack. */
@Pseudo
@Mixin(targets="com.jozufozu.flywheel.event.RenderLayerEvent",remap=false)
public abstract class FlywheelRenderLayerEventMixin implements LegacyFlywheelEventMatrices {
    @Shadow @Final public PoseStack stack;
    @Shadow @Final private ClientLevel world;
    @Unique private Matrix4f vulkanmod$preCameraPose;
    @Unique private Matrix3f vulkanmod$preCameraNormal;

    @Inject(method="<init>",at=@At("RETURN"),remap=false)
    private void vulkanmod$capturePose(CallbackInfo ci) {
        if(!LegacyFlywheelEventCapture.requested(world)) return;
        vulkanmod$preCameraPose=new Matrix4f(stack.last().pose());
        vulkanmod$preCameraNormal=new Matrix3f(stack.last().normal());
    }
    public boolean vulkanmod$hasPreCameraPose() { return vulkanmod$preCameraPose!=null; }
    public Matrix4f vulkanmod$copyPreCameraPose() { return new Matrix4f(vulkanmod$preCameraPose); }
    public Matrix3f vulkanmod$copyPreCameraNormal() { return new Matrix3f(vulkanmod$preCameraNormal); }
}
