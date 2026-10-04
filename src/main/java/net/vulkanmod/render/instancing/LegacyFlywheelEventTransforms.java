package net.vulkanmod.render.instancing;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.core.BlockPos;
import net.vulkanmod.interfaces.LegacyFlywheelEventMatrices;

/** Preserve fractional camera coordinates before composing the CPU's origin-relative pose. */
public final class LegacyFlywheelEventTransforms {
    private LegacyFlywheelEventTransforms() {}

    public static PoseStack cpuStack(Object event,double cameraX,double cameraY,double cameraZ,BlockPos origin) {
        if(!(event instanceof LegacyFlywheelEventMatrices matrices) || !matrices.vulkanmod$hasPreCameraPose())
            throw new UnsupportedOperationException("Flywheel event has no pre-camera pose snapshot");
        double x=origin.getX()-cameraX,y=origin.getY()-cameraY,z=origin.getZ()-cameraZ;
        if(!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Float.isFinite((float)x) || !Float.isFinite((float)y) || !Float.isFinite((float)z))
            throw new IllegalArgumentException("Invalid Flywheel camera/origin translation");
        PoseStack copied=new PoseStack();
        copied.last().pose().set(matrices.vulkanmod$copyPreCameraPose());
        copied.last().normal().set(matrices.vulkanmod$copyPreCameraNormal());
        if(!copied.last().pose().isFinite() || !copied.last().normal().isFinite())
            throw new IllegalArgumentException("Nonfinite Flywheel event pose");
        copied.translate(x,y,z);
        return copied;
    }
}
