package net.vulkanmod.interfaces;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Copies captured by the optional event constructor, before InstanceWorld adds its -camera translation. */
public interface LegacyFlywheelEventMatrices {
    boolean vulkanmod$hasPreCameraPose();
    Matrix4f vulkanmod$copyPreCameraPose();
    Matrix3f vulkanmod$copyPreCameraNormal();
}
