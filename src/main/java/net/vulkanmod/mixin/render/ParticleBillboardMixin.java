package net.vulkanmod.mixin.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.CampfireSmokeParticle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.util.Mth;
import net.vulkanmod.interfaces.ParticleBillboardWriter;
import net.vulkanmod.render.particle.ParticleBillboardCompute;
import net.vulkanmod.render.particle.ParticleBillboardSmokeTest;
import net.vulkanmod.render.vertex.VertexUtil;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Does not redirect ParticleEngine's invocation (including the IP replacement loop). */
@Mixin(SingleQuadParticle.class)
public abstract class ParticleBillboardMixin extends Particle {
    @Unique private final Quaternionf vulkanmod$rotation = new Quaternionf();
    protected ParticleBillboardMixin(ClientLevel level, double x, double y, double z) { super(level,x,y,z); }
    @Shadow public abstract float getQuadSize(float partialTick);
    @Shadow protected abstract float getU0();
    @Shadow protected abstract float getU1();
    @Shadow protected abstract float getV0();
    @Shadow protected abstract float getV1();

    @Inject(method="render", at=@At("HEAD"), cancellable=true)
    private void vulkanmod$prepareBillboard(VertexConsumer consumer, Camera camera, float tick, CallbackInfo ci) {
        Class<?> type = this.getClass();
        boolean eligible = type == CampfireSmokeParticle.class
                || Boolean.getBoolean("vulkanmod.ciScreenshotSmoke") && type == ParticleBillboardSmokeTest.Probe.class;
        ParticleBillboardCompute.attempt(eligible);
        if(!eligible) return;
        if(!(consumer instanceof ParticleBillboardWriter writer) || !writer.vulkanmod$canWriteBillboard()) {
            ParticleBillboardCompute.layoutFallback();
            ParticleBillboardCompute.originalCpu();
            return;
        }
        if(!ParticleBillboardCompute.ready()) { ParticleBillboardCompute.originalCpu(); return; }
        var position=camera.getPosition();
        float cx=(float)(Mth.lerp((double)tick,this.xo,this.x)-position.x());
        float cy=(float)(Mth.lerp((double)tick,this.yo,this.y)-position.y());
        float cz=(float)(Mth.lerp((double)tick,this.zo,this.z)-position.z());
        Quaternionf q=vulkanmod$rotation.set(camera.rotation());
        if(this.roll!=0.0f) q.rotateZ(Mth.lerp(tick,this.oRoll,this.roll));
        float size=getQuadSize(tick);
        float u0=getU0(), u1=getU1(), v0=getV0(), v1=getV1();
        int light=getLightColor(tick);
        // A Forge/mod callback can change the BufferBuilder after the initial
        // admission check (for example by enabling quad sorting). Never pack
        // into that stale state or run the callbacks a second time. Produce the
        // original CPU vertices from the already captured inputs instead.
        if(!writer.vulkanmod$canWriteBillboard()) {
            ParticleBillboardCompute.callbackStateRecovery();
            Vector3f vertex = new Vector3f();
            for(int i=0;i<4;i++) {
                vertex.set(i<2?-1:1, i==0||i==3?-1:1, 0).rotate(q).mul(size).add(cx,cy,cz);
                consumer.vertex(vertex.x,vertex.y,vertex.z)
                        .uv(i<2?u1:u0, i==0||i==3?v1:v0)
                        .color(this.rCol,this.gCol,this.bCol,this.alpha).uv2(light).endVertex();
            }
            ci.cancel();
            return;
        }
        writer.vulkanmod$billboard(cx,cy,cz,q,size,u0,u1,v0,v1,
                VertexUtil.packColor(this.rCol,this.gCol,this.bCol,this.alpha),light);
        ParticleBillboardCompute.packed();
        ci.cancel();
    }
}
