package net.vulkanmod.render.instancing;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.IdentityHashMap;

/** Only live callable engines request snapshots. Disabled Flywheel incurs no per-event matrix allocations. */
public final class LegacyFlywheelEventCapture {
    private static final IdentityHashMap<Object,Integer> worlds=new IdentityHashMap<>();
    private static volatile boolean active;
    private LegacyFlywheelEventCapture() {}
    public static boolean requested(Object world) {
        if(!active) return false;
        RenderSystem.assertOnRenderThread(); return worlds.containsKey(world);
    }
    public static Lease retain(Object world) {
        RenderSystem.assertOnRenderThread();
        worlds.merge(world,1,Math::addExact); active=true;
        return new Lease(world);
    }
    public static final class Lease implements AutoCloseable {
        private final Object world;
        private boolean closed;
        private Lease(Object world) { this.world=world; }
        public void close() {
            RenderSystem.assertOnRenderThread(); if(closed) return; closed=true;
            int count=worlds.get(world);
            if(count==1) worlds.remove(world); else worlds.put(world,count-1);
            active=!worlds.isEmpty();
        }
    }
}
