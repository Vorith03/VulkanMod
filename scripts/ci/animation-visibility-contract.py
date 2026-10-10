#!/usr/bin/env python3
"""Exercise visibility timing/lifetime decisions using the production Java helper."""
from pathlib import Path
import shutil
import subprocess
import tempfile

root = Path(__file__).resolve().parents[2]
package = Path('net/vulkanmod/render/texture')
harness = '''package net.vulkanmod.render.texture;
public class VisibilityContract {
 static void check(boolean value) { if(!value) throw new AssertionError(); }
 public static void main(String[] args) {
  AnimationVisibility v = new AnimationVisibility(100);
  check(v.materialize(110, 20, true));
  check(!v.materialize(121, 20, true));
  check(v.materialize(1000, 20, false));
  v.skipped(); check(v.needsRefresh());
  v.use(2000); check(v.materialize(2000, 0, true)); check(v.needsRefresh());
  v.refreshed(); check(!v.needsRefresh());
  // A second hidden interval still requires exactly one first-use refresh.
  v.skipped(); v.use(3000); check(v.needsRefresh()); v.refreshed();
  v.close(); v.use(4000); v.skipped();
  check(!v.materialize(4000, 100, false)); check(!v.needsRefresh());
  // nanoTime wraps: subtracting an elapsed interval retains correct semantics.
  v = new AnimationVisibility(Long.MAX_VALUE - 5);
  check(v.materialize(Long.MIN_VALUE + 4, 10, true));
  check(!v.materialize(Long.MIN_VALUE + 5, 10, true));
  System.out.println("Animation visibility contract passed: grace, refresh, bypass, close, nanoTime wrap");
 }
}'''
with tempfile.TemporaryDirectory(prefix='vulkanmod-animation-') as folder:
    dest = Path(folder)
    sources = dest / package
    sources.mkdir(parents=True)
    (sources/'AnimationVisibility.java').write_text((root/'src/main/java'/package/'AnimationVisibility.java').read_text())
    (sources/'VisibilityContract.java').write_text(harness)
    compiler = [shutil.which('javac')] if shutil.which('javac') else ['java', 'com.sun.tools.javac.Main']
    subprocess.run([*compiler, '--release', '17', '-d', folder, *map(str,sources.glob('*.java'))], check=True, timeout=30)
    subprocess.run(['java', '-cp', folder, 'net.vulkanmod.render.texture.VisibilityContract'], check=True, timeout=30)


# Compile the production once-per-setup gate without Gradle or a JAR and
# enforce that terrain draw layers only claim it when sprite usage is enabled.
world_renderer = (root/'src/main/java/net/vulkanmod/render/chunk/WorldRenderer.java').read_text()
assert '&& this.spriteUsageGate.claim())' in world_renderer
assert 'this.spriteUsageGate.reset();' in world_renderer
assert world_renderer.count('this.spriteUsageGate.reset();') >= 2
assert 'vulkanmod$markAllAnimatedSpritesUsed()' in world_renderer
gate = root/'src/main/java/net/vulkanmod/render/texture/TerrainSpriteUsageGate.java'
gate_contract = '''package net.vulkanmod.render.texture;
public final class TerrainSpriteUsageGateContract {
    static void check(boolean ok, String why) { if(!ok) throw new AssertionError(why); }
    public static void main(String[] args) {
        TerrainSpriteUsageGate gate = new TerrainSpriteUsageGate();
        check(gate.claim(), "first terrain layer must mark usage");
        for(int i = 0; i < 6; ++i)
            check(!gate.claim(), "redundant terrain layer usage traversal");
        gate.reset();
        check(gate.claim(), "next renderer setup lost sprite usage");
        gate.reset();
        gate.reset();
        check(gate.claim(), "portal/reload setup lost its first usage");
        System.out.println("Terrain sprite usage gate contract passed");
    }
}
'''
with tempfile.TemporaryDirectory(prefix='vulkanmod-sprite-gate-') as folder:
    path = Path(folder)/'TerrainSpriteUsageGateContract.java'
    path.write_text(gate_contract)
    subprocess.run(['javac', '--release', '17', '-d', folder,
                    str(gate), str(path)], check=True, timeout=30)
    subprocess.run(['java', '-cp', folder,
                    'net.vulkanmod.render.texture.TerrainSpriteUsageGateContract'], check=True, timeout=30)
