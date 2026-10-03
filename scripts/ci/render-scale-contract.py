#!/usr/bin/env python3
"""Exercise production resolution policy at invalid/odd/tiny extents."""
from pathlib import Path
import shutil
import subprocess
import tempfile
root = Path(__file__).resolve().parents[2]
package = Path('net/vulkanmod/render/scale')
harness = '''package net.vulkanmod.render.scale;
public class ScaleContract {
 static void check(boolean value) { if(!value) throw new AssertionError(); }
 public static void main(String[] args) {
  check(RenderScaleDimensions.extent(1920,0.5)==960);
  check(RenderScaleDimensions.extent(1080,0.75)==810);
  check(RenderScaleDimensions.extent(853,0.5)==427);
  check(RenderScaleDimensions.extent(853,0.75)==640);
  check(RenderScaleDimensions.extent(1,0.5)==1);
  check(RenderScaleDimensions.extent(1920,-10)==960);
  check(RenderScaleDimensions.extent(1920,100)==1920);
  check(RenderScaleDimensions.extent(1920,Double.NaN)==1920);
  check(RenderScaleDimensions.extent(1920,Double.POSITIVE_INFINITY)==1920);
  try { RenderScaleDimensions.extent(0,0.5); throw new AssertionError(); }
  catch(IllegalArgumentException expected) {}
  System.out.println("Render scale dimensions contract passed: clamp, invalid values, odd/tiny extents");
 }
}'''
with tempfile.TemporaryDirectory(prefix='vulkanmod-scale-') as folder:
    sources = Path(folder)/package
    sources.mkdir(parents=True)
    (sources/'RenderScaleDimensions.java').write_text((root/'src/main/java'/package/'RenderScaleDimensions.java').read_text())
    (sources/'ScaleContract.java').write_text(harness)
    compiler = [shutil.which('javac')] if shutil.which('javac') else ['java','com.sun.tools.javac.Main']
    subprocess.run([*compiler,'--release','17','-d',folder,*map(str,sources.glob('*.java'))],check=True,timeout=30)
    subprocess.run(['java','-cp',folder,'net.vulkanmod.render.scale.ScaleContract'],check=True,timeout=30)
