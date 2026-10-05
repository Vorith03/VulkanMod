#!/usr/bin/env python3
"""Independent boundary vectors for the numeric reference used by native readback."""
from pathlib import Path
import shutil
import subprocess
import tempfile

root = Path(__file__).resolve().parents[2]
package = Path('net/vulkanmod/render/texture')
harness = '''package net.vulkanmod.render.texture;
public class AnimationOracleContract {
 static void check(boolean value) { if(!value) throw new AssertionError(); }
 public static void main(String[] args) {
  // Distinct alpha, ascending and descending odd-valued channels expose both
  // alpha interpolation and round-to-nearest mistakes.
  check(SpriteAnimationOracle.mix(0x01ff0001, 0xfe00ff00, 1, 2) == 0x017f7f00);
  check(SpriteAnimationOracle.mix(0x00112233, 0xff334455, 0, 3) == 0x00112233);
  check(SpriteAnimationOracle.mix(0x7f000000, 0xff030609, 1, 3) == 0x7f000102);
  var oracle = new SpriteAnimationOracle(new SpriteAnimationOracle.Frame[] {
   new SpriteAnimationOracle.Frame(3, 2), new SpriteAnimationOracle.Frame(3, 1),
   new SpriteAnimationOracle.Frame(0, 1)}, true);
  check(oracle.clock() == 0 && oracle.currentIndex() == 3);
  check(oracle.tick() == SpriteAnimationOracle.Update.NONE && oracle.clock() == 1);
  check(oracle.tick() == SpriteAnimationOracle.Update.NONE && oracle.clock() == (1L << 32));
  check(oracle.tick() == SpriteAnimationOracle.Update.FRAME && oracle.currentIndex() == 0);
  check(oracle.tick() == SpriteAnimationOracle.Update.FRAME && oracle.clock() == 0);
  // Mip offsets use (base frame offset >> level), including odd tile dimensions.
  int[] mip = {10,11,12,13,14,15};
  check(oracle.pixel(mip, 3, 2, 3, 2, 1, 0, 0) == 14);
  boolean invalid = false;
  try { SpriteAnimationOracle.mix(0,0,2,2); } catch(IllegalArgumentException e) { invalid = true; }
  check(invalid);
  System.out.println("Animation numeric oracle contract passed: ABGR/alpha/truncation, repeated frame clocks, odd mip coordinates");
 }
}'''
with tempfile.TemporaryDirectory(prefix='vulkanmod-animation-oracle-') as folder:
    sources = Path(folder)/package
    sources.mkdir(parents=True)
    (sources/'SpriteAnimationOracle.java').write_text((root/'src/main/java'/package/'SpriteAnimationOracle.java').read_text())
    (sources/'AnimationOracleContract.java').write_text(harness)
    compiler = [shutil.which('javac')] if shutil.which('javac') else ['java', 'com.sun.tools.javac.Main']
    subprocess.run([*compiler, '--release', '17', '-d', folder, *map(str,sources.glob('*.java'))], check=True, timeout=30)
    subprocess.run(['java', '-cp', folder, 'net.vulkanmod.render.texture.AnimationOracleContract'], check=True, timeout=30)
