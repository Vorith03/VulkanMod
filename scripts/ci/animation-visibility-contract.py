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
