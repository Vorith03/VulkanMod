#!/usr/bin/env python3
"""Exercise production instance layout admission and fetch bounds without a Vulkan device."""
from pathlib import Path
import shutil
import subprocess
import tempfile

root = Path(__file__).resolve().parents[2]
package = Path('net/vulkanmod/vulkan/shader')
harness = '''package net.vulkanmod.vulkan.shader;
import java.util.*;
import static net.vulkanmod.vulkan.shader.InstanceVertexFormat.Format.*;
public class InstanceContract {
 static InstanceVertexFormat.Attribute a(int loc, InstanceVertexFormat.Format f, int offset) {
  return new InstanceVertexFormat.Attribute(loc,f,offset);
 }
 static void reject(Runnable r) {
  try { r.run(); } catch(IllegalArgumentException expected) { return; }
  throw new AssertionError("Invalid instance input was admitted");
 }
 public static void main(String[] args) {
  var attributes = new ArrayList<InstanceVertexFormat.Attribute>();
  attributes.add(a(8,UBYTE4_NORMALIZED,4)); attributes.add(a(9,USHORT2,0));
  for(int i=0;i<4;i++) attributes.add(a(1+i,FLOAT4,8+i*16));
  for(int i=0;i<3;i++) attributes.add(a(5+i,FLOAT3,72+i*12));
  var format = new InstanceVertexFormat(108,attributes);
  attributes.clear();
  if(format.attributes().size()!=9) throw new AssertionError("Mutable layout ownership");
  try { format.attributes().clear(); throw new AssertionError(); }
  catch(UnsupportedOperationException expected) {}
  format.validateLimits(1,16,2048,2047);
  format.validateRange(324,1,2); format.validateRange(324,2,1);
  format.validateRange(0,Integer.MAX_VALUE,0);
  reject(()->format.validateRange(323,1,2));
  reject(()->format.validateRange(324,Integer.MAX_VALUE,Integer.MAX_VALUE));
  reject(()->format.validateRange(-1,0,0));
  reject(()->format.validateRange(324,-1,1));
  reject(()->format.validateRange(324,0,-1));
  reject(()->format.validateLimits(2,16,2048,2047));
  reject(()->format.validateLimits(1,9,2048,2047));
  reject(()->format.validateLimits(1,16,104,2047));
  reject(()->format.validateLimits(1,16,2048,95));
  reject(()->new InstanceVertexFormat(0,List.of(a(1,FLOAT,0))));
  reject(()->new InstanceVertexFormat(6,List.of(a(1,FLOAT,0))));
  reject(()->new InstanceVertexFormat(16,List.of()));
  reject(()->new InstanceVertexFormat(16,List.of(a(1,FLOAT4,4))));
  reject(()->new InstanceVertexFormat(16,List.of(a(1,FLOAT,0),a(1,FLOAT,4))));
  reject(()->new InstanceVertexFormat(16,List.of(a(1,FLOAT4,0),a(2,FLOAT,12))));
  reject(()->a(1,FLOAT4,2)); reject(()->a(1,USHORT2,1)); reject(()->a(-1,FLOAT,0));
  new InstanceVertexFormat(8,List.of(a(1,BYTE4_NORMALIZED,0),a(2,SHORT2,4)));
  System.out.println("Instance input contract passed: matrix/packed layout, immutable ownership, device limits, overlap/alignment, firstInstance overflow/bounds");
 }
}'''
with tempfile.TemporaryDirectory(prefix='vulkanmod-instance-') as folder:
    sources = Path(folder) / package
    sources.mkdir(parents=True)
    (sources / 'InstanceVertexFormat.java').write_text((root / 'src/main/java' / package / 'InstanceVertexFormat.java').read_text())
    (sources / 'InstanceContract.java').write_text(harness)
    compiler = [shutil.which('javac')] if shutil.which('javac') else ['java', 'com.sun.tools.javac.Main']
    subprocess.run([*compiler, '--release', '17', '-d', folder, *map(str, sources.glob('*.java'))], check=True, timeout=30)
    subprocess.run(['java', '-cp', folder, 'net.vulkanmod.vulkan.shader.InstanceContract'], check=True, timeout=30)
