#!/usr/bin/env python3
"""Exercise production CPU geometry admission and instance lifecycle without optional mods."""
from pathlib import Path
import shutil
import subprocess
import tempfile

root = Path(__file__).resolve().parents[2]
package = Path('net/vulkanmod/render/instancing')
harness = r'''package net.vulkanmod.render.instancing;
import java.nio.*;
import java.util.*;
public class OwnershipContract {
 static void check(boolean condition) { if(!condition) throw new AssertionError(); }
 static void reject(Runnable task) {
  try { task.run(); } catch(IllegalArgumentException | IllegalStateException expected) { return; }
  throw new AssertionError("Invalid operation admitted");
 }
 static class Data { Object owner; boolean dirty=true, removed; int value, writes; }
 static class Access implements InstanceGroup.Access<Data> {
  boolean fail; int notices;
  public Object owner(Data d) { return d.owner; }
  public void setOwner(Data d,Object o) { d.owner=o; }
  public boolean removed(Data d) { return d.removed; }
  public boolean consumeDirty(Data d) { boolean value=d.dirty; d.dirty=false; return value; }
  public void markDirty(Data d) { d.dirty=true; }
  public void notifyRemoval(Object o) { notices++; }
  public void write(Data d,ByteBuffer out) { d.writes++; if(fail) throw new IllegalStateException(); out.putInt(d.value); }
 }
 public static void main(String[] args) {
  int[] quads=ModelGeometry.quadIndices(8);
  check(Arrays.equals(quads,new int[]{0,1,2,2,3,0,4,5,6,6,7,4}));
  var shade=new BitSet(); shade.set(3);
  byte[] vertices=new byte[8*32]; vertices[0]=42;
  var geometry=new ModelGeometry(vertices,quads,shade);
  vertices[0]=0; quads[0]=7; shade.clear();
  check(geometry.vertices().get(0)==42 && !geometry.isShaded(3) && geometry.isShaded(2));
  check(geometry.indices().getShort(0)==0 && geometry.indexBytes()==2);
  check(geometry.vertices().isReadOnly() && geometry.indices().isReadOnly());
  var large=new ModelGeometry(new byte[65540*32],ModelGeometry.quadIndices(65540),new BitSet());
  check(large.indexBytes()==4 && large.indices().getInt((large.indexCount()-2)*4)==65539);
  reject(()->ModelGeometry.quadIndices(3)); reject(()->ModelGeometry.quadIndices(Integer.MAX_VALUE));
  reject(()->new ModelGeometry(new byte[32],new int[]{0,1,0},new BitSet()));
  reject(()->new ModelGeometry(new byte[32],new int[]{0,-1,0},new BitSet()));
  reject(()->new ModelGeometry(new byte[31],new int[]{},new BitSet()));
  var access=new Access(); Object a=new Object(), b=new Object();
  var first=new InstanceGroup<Data>(4,a,access); var second=new InstanceGroup<Data>(4,b,access);
  var x=new Data(); x.value=10; var y=new Data(); y.value=20;
  first.add(x); first.add(y); first.add(x);
  var before=first.snapshot(); check(before.remaining()==8 && x.writes==1 && y.writes==1);
  first.snapshot(); check(x.writes==1 && y.writes==1);
  x.value=30; x.dirty=true;
  check(first.snapshot().getInt(0)==30 && before.getInt(0)==10 && y.writes==1);
  second.add(x); first.add(x); // transfer back before either owner compacts
  check(first.snapshot().remaining()==8 && second.snapshot().remaining()==0 && access.notices==2);
  second.add(x); check(first.snapshot().remaining()==4 && first.snapshot().getInt(0)==20);
  check(second.snapshot().getInt(0)==30);
  y.removed=true; check(first.snapshot().remaining()==0); reject(()->first.add(y));
  access.fail=true; x.dirty=true; reject(()->second.snapshot()); access.fail=false;
  check(second.snapshot().getInt(0)==30); // consumed dirty bit is retried after failed packing
  second.clear(); check(second.snapshot().remaining()==0);
  second.add(x); check(second.snapshot().remaining()==4);
  second.close(); second.close(); reject(()->second.snapshot()); reject(()->second.add(new Data()));
  reject(()->new InstanceGroup<Data>(3,a,access));
  System.out.println("Flywheel ownership contract passed: immutable indices, 16/32-bit admission, bounds, dirty reuse, removal, compaction, owner transfer/back, failed packing, origin clear, close");
 }
}'''
with tempfile.TemporaryDirectory(prefix='vulkanmod-flywheel-') as folder:
    sources = Path(folder) / package
    sources.mkdir(parents=True)
    for name in ['ModelGeometry', 'InstanceGroup']:
        (sources / f'{name}.java').write_text((root / 'src/main/java' / package / f'{name}.java').read_text())
    (sources / 'OwnershipContract.java').write_text(harness)
    compiler = [shutil.which('javac')] if shutil.which('javac') else ['java', 'com.sun.tools.javac.Main']
    subprocess.run([*compiler, '--release', '17', '-d', folder, *map(str, sources.glob('*.java'))], check=True, timeout=30)
    subprocess.run(['java', '-cp', folder, 'net.vulkanmod.render.instancing.OwnershipContract'], check=True, timeout=30)
