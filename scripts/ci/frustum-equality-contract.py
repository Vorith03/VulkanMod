#!/usr/bin/env python3
"""Exact equality regression for the opt-in Forge frustum hot path, no game runtime."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[2]
helper = root / "src/main/java/net/vulkanmod/render/particle/FrustumInfiniteBoundsEquality.java"
mixin = root / "src/main/java/net/vulkanmod/mixin/render/FrustumInfiniteBoundsFastPathMixin.java"
plugin = (root / "src/main/java/net/vulkanmod/mixin/MixinPlugin.java").read_text()
mixins = (root / "src/main/resources/vulkanmod.mixins.json").read_text()
smoke = (root / "scripts/ci/vulkan-smoke.sh").read_text()

assert 'Double.compare(bounds.minX, other.minX) != 0' in helper.read_text()
assert '@Redirect' in mixin.read_text() and 'require = 1' in mixin.read_text()
assert 'FrustumInfiniteBoundsFastPathMixin' in plugin and 'FrustumInfiniteBoundsFastPathMixin' in mixins
assert 'vulkanmod.experimentalParticleFrustumFastPath' in plugin
assert '-Dvulkanmod.experimentalParticleFrustumFastPath=true' in smoke

aabb = """package net.minecraft.world.phys;
public class AABB {
    public final double minX,minY,minZ,maxX,maxY,maxZ;
    public AABB(double a,double b,double c,double d,double e,double f) {
        minX=a;minY=b;minZ=c;maxX=d;maxY=e;maxZ=f;
    }
    @Override public boolean equals(Object object) {
        if(this==object)return true;
        if(!(object instanceof AABB o))return false;
        return Double.compare(minX,o.minX)==0&&Double.compare(minY,o.minY)==0
            &&Double.compare(minZ,o.minZ)==0&&Double.compare(maxX,o.maxX)==0
            &&Double.compare(maxY,o.maxY)==0&&Double.compare(maxZ,o.maxZ)==0;
    }
}
"""
harness = """import net.minecraft.world.phys.AABB;
import net.vulkanmod.render.particle.FrustumInfiniteBoundsEquality;
import java.util.Random;
public class FrustumEqualityContract {
  static class ModBox extends AABB {
    ModBox(double x){super(x,0,0,1,1,1);}
    @Override public boolean equals(Object o){return o instanceof AABB;}
  }
  public static void main(String[] args) {
    AABB sentinel=new AABB(Double.NEGATIVE_INFINITY,-1,-1,Double.POSITIVE_INFINITY,1,1);
    AABB[] boxes={sentinel,new AABB(-1,0,0,1,1,1),
      new AABB(Double.NEGATIVE_INFINITY,0,0,1,1,1),
      new AABB(Double.NaN,0,0,1,1,1),new AABB(-0.0,0,0,1,1,1),
      new AABB(+0.0,0,0,1,1,1),new ModBox(0)};
    for(AABB box:boxes)for(Object other:boxes) {
      if(FrustumInfiniteBoundsEquality.matches(box,other)!=box.equals(other))
        throw new AssertionError("special/subclass mismatch");
    }
    Random random=new Random(12345);
    for(int i=0;i<100000;i++) {
      AABB box=new AABB(random.nextDouble()*100-50,0,0,1,1,1);
      if(FrustumInfiniteBoundsEquality.matches(box,sentinel)!=box.equals(sentinel))
        throw new AssertionError("random mismatch at "+i);
    }
    if(!FrustumInfiniteBoundsEquality.matches(new ModBox(0),sentinel))
      throw new AssertionError("modded equals fallback");
    System.out.println("Frustum equality contract passed: 100k random, special values, virtual subclass");
  }
}
"""
with tempfile.TemporaryDirectory(prefix="vulkanmod-frustum-equality-") as temporary:
    output = Path(temporary)
    stub = output / "src/net/minecraft/world/phys/AABB.java"
    stub.parent.mkdir(parents=True)
    stub.write_text(aabb)
    test = output / "src/FrustumEqualityContract.java"
    test.write_text(harness)
    subprocess.run(["javac","--release","17","-d",str(output),
        str(stub),str(helper),str(test)],check=True,timeout=30)
    subprocess.run(["java","-cp",str(output),"FrustumEqualityContract"],
        check=True,timeout=30)
