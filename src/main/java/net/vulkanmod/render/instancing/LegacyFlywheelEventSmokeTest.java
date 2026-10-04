package net.vulkanmod.render.instancing;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.vulkanmod.Initializer;
import net.vulkanmod.interfaces.LegacyFlywheelEventMatrices;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Real pinned event/mixin, with a null-world numeric fixture; not a loaded ClientLevel qualification. */
public final class LegacyFlywheelEventSmokeTest {
    private LegacyFlywheelEventSmokeTest() {}
    public static void verify(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> eventApi=Class.forName("com.jozufozu.flywheel.event.RenderLayerEvent",false,loader);
        var constructor=eventApi.getConstructor(ClientLevel.class,RenderType.class,PoseStack.class,RenderBuffers.class,
                double.class,double.class,double.class);
        Object disabled=constructor.newInstance(null,RenderType.solid(),new PoseStack(),Minecraft.getInstance().renderBuffers(),0,0,0);
        if(!(disabled instanceof LegacyFlywheelEventMatrices initial) || initial.vulkanmod$hasPreCameraPose())
            throw new AssertionError("Disabled Flywheel allocated an event snapshot or optional mixin is missing");
        BlockPos origin=new BlockPos(30_000_000,-300,-30_000_000);
        double x=origin.getX()+0.25,y=origin.getY()+0.5,z=origin.getZ()+0.125;
        var stack=new PoseStack(); stack.translate(3,4,5); stack.mulPose(com.mojang.math.Axis.YP.rotation(0.37f));
        Matrix4f before=new Matrix4f(stack.last().pose()); Matrix3f normal=new Matrix3f(stack.last().normal());
        var first=LegacyFlywheelEventCapture.retain(null); var second=LegacyFlywheelEventCapture.retain(null);
        try {
            first.close(); first.close();
            Object event=constructor.newInstance(null,RenderType.solid(),stack,Minecraft.getInstance().renderBuffers(),x,y,z);
            var snapshot=(LegacyFlywheelEventMatrices)event;
            if(!snapshot.vulkanmod$hasPreCameraPose()) throw new AssertionError("Shared capture owner retired early");
            snapshot.vulkanmod$copyPreCameraPose().zero(); snapshot.vulkanmod$copyPreCameraNormal().zero();
            if(!snapshot.vulkanmod$copyPreCameraPose().equals(before) || !snapshot.vulkanmod$copyPreCameraNormal().equals(normal))
                throw new AssertionError("Snapshot copies mutated stored event data");
            // InstanceWorld's real pattern loses distant fractions here; the adapter must use its earlier snapshot.
            stack.translate(-x,-y,-z);
            Matrix4f translated=new Matrix4f(stack.last().pose());
            var repaired=LegacyFlywheelEventTransforms.cpuStack(event,x,y,z,origin);
            Matrix4f expected=new Matrix4f(before).translate(-0.25f,-0.5f,-0.125f);
            if(!repaired.last().pose().equals(expected,0.00001f) || !repaired.last().normal().equals(normal))
                throw new AssertionError("Pre-camera event pose/origin/normal composition differs");
            Matrix4f lossy=new Matrix4f(translated).translate(origin.getX(),origin.getY(),origin.getZ());
            if(lossy.equals(expected,0.01f)) throw new AssertionError("Distant-coordinate fixture did not expose the old precision loss");
            if(!stack.last().pose().equals(translated)) throw new AssertionError("Adapter mutated caller stack");
            repaired.last().pose().zero();
            if(!LegacyFlywheelEventTransforms.cpuStack(event,x,y,z,origin).last().pose().equals(expected,0.00001f))
                throw new AssertionError("CPU transform result aliases event snapshot");
            reject(() -> LegacyFlywheelEventTransforms.cpuStack(disabled,0,0,0,BlockPos.ZERO));
            reject(() -> LegacyFlywheelEventTransforms.cpuStack(event,Double.NaN,y,z,origin));
            reject(() -> LegacyFlywheelEventTransforms.cpuStack(event,Double.MAX_VALUE,y,z,origin));
            if(LegacyFlywheelEventCapture.requested(new Object())) throw new AssertionError("Capture leaked across world identities");
        } finally { first.close(); second.close(); }
        Object retired=constructor.newInstance(null,RenderType.solid(),new PoseStack(),Minecraft.getInstance().renderBuffers(),0,0,0);
        if(((LegacyFlywheelEventMatrices)retired).vulkanmod$hasPreCameraPose()) throw new AssertionError("Retired world retained capture request");
        Initializer.LOGGER.info("Flywheel event transform smoke passed: real optional event constructor/mixin, rotated pre-camera pose/normal ownership, distant fractional camera precision, caller isolation, invalid-camera rejection, world-scoped shared capture and last-owner retirement; loaded-world qualification remains open");
    }
    private static void reject(Runnable call) {
        try { call.run(); } catch(IllegalArgumentException | UnsupportedOperationException expected) { return; }
        throw new AssertionError("Invalid/missing event snapshot accepted");
    }
}
