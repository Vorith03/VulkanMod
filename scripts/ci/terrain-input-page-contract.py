#!/usr/bin/env python3
"""Production page-store ownership under bounded cross-region pressure; no native-performance claim."""
from pathlib import Path
import subprocess
import tempfile
ROOT = Path(__file__).resolve().parents[2]
files={
'net/vulkanmod/render/chunk/TerrainAppearanceEvents.java':'''package net.vulkanmod.render.chunk; public class TerrainAppearanceEvents { public static void pageBudgetEvent(String op,int used,int capacity){} }''',
'org/lwjgl/system/MemoryUtil.java':'''package org.lwjgl.system; import java.nio.ByteBuffer; public class MemoryUtil { public static ByteBuffer memAlloc(int n){return ByteBuffer.allocate(n);} public static void memFree(ByteBuffer b){} }''',
'net/vulkanmod/vulkan/memory/MemoryTypes.java':'''package net.vulkanmod.vulkan.memory; public class MemoryTypes { public static Object GPU_MEM=new Object(); }''',
'net/vulkanmod/render/chunk/AreaUploadManager.java':'''package net.vulkanmod.render.chunk; import java.util.*; import java.nio.*; import net.vulkanmod.vulkan.memory.*; public class AreaUploadManager { public static AreaUploadManager INSTANCE=new AreaUploadManager(); final List<Runnable> ops=new ArrayList<>(); public void uploadStorageAsync(StorageBuffer b,int o,ByteBuffer data,Runnable r){ops.add(r);} public void submitUploads(){var c=List.copyOf(ops);ops.clear();c.forEach(Runnable::run);} }''',
'net/vulkanmod/render/chunk/voxel/GpuSparseLightingSnapshot.java':'''package net.vulkanmod.render.chunk.voxel; import java.nio.*; public class GpuSparseLightingSnapshot {public int byteSize(){return 128;} public void writeTo(ByteBuffer b){b.put(new byte[128]);} }'''
}
# Make logical destruction/bookkeeping observable, including close-before-callback races.
files['net/vulkanmod/vulkan/memory/StorageBuffer.java'] = """package net.vulkanmod.vulkan.memory;
public class StorageBuffer {
 public boolean destroyed;
 public StorageBuffer(int n,Object t){}
 public Object getBufferInfo(){return this;}
 public void freeBuffer(){MemoryManager.getInstance().retired.add(this);}
} """
files['net/vulkanmod/vulkan/memory/MemoryManager.java'] = """package net.vulkanmod.vulkan.memory;
import java.util.*;
public class MemoryManager {
 static MemoryManager M=new MemoryManager();
 public static MemoryManager getInstance(){return M;}
 public final List<Runnable> ops=new ArrayList<>();
 public final List<StorageBuffer> retired=new ArrayList<>();
 public int destroyed; boolean fenced;
 public void addFrameOp(Runnable r){ops.add(r);}
 public void fence(){fenced=true; try{
  var buffers=List.copyOf(retired);retired.clear();buffers.forEach(MemoryManager::freeBufferAfterFence);
  var callbacks=List.copyOf(ops);ops.clear();callbacks.forEach(Runnable::run);
 }finally{fenced=false;}}
 public static void freeBufferAfterFence(Object info){
  if(!M.fenced)throw new AssertionError("unfenced destruction");
  StorageBuffer b=(StorageBuffer)info;
  if(b.destroyed)throw new AssertionError("double destruction");
  b.destroyed=true;M.destroyed++;
 }
} """
files['net/vulkanmod/render/chunk/voxel/SectionVoxelSnapshot.java'] = """package net.vulkanmod.render.chunk.voxel;
import java.nio.*; public class SectionVoxelSnapshot {
 final int size; public SectionVoxelSnapshot(){this(128);} public SectionVoxelSnapshot(int size){this.size=size;}
 public int byteSize(){return size;} public void writeTo(ByteBuffer b){b.put(new byte[size]);}
} """
files['net/vulkanmod/render/chunk/voxel/TurnoverRepro.java'] = """package net.vulkanmod.render.chunk.voxel;
import net.vulkanmod.render.chunk.*; import net.vulkanmod.vulkan.memory.*;
public class TurnoverRepro {
 static void check(boolean v,String m){if(!v)throw new AssertionError(m);}
 public static void main(String[] args){
  var memory=MemoryManager.getInstance();var uploads=AreaUploadManager.INSTANCE;
  var budget=new RegionVoxelGpuStore.PageBudget(RegionVoxelGpuStore.PAGE_BYTES);
  var voxel=new SectionVoxelSnapshot();var light=new GpuSparseLightingSnapshot();
  for(int region=0;region<128;region++){
   var a=new RegionVoxelGpuStore(budget);var b=new RegionVoxelGpuStore(budget);
   check(a.upload(0,voxel,1)&&a.uploadLighting(0,light,1),"first page");
   uploads.submitUploads();a.releaseConsumed(0,1);
   check(budget.usedBytes()==RegionVoxelGpuStore.PAGE_BYTES,"premature capacity");
   check(!b.upload(0,voxel,1),"unfenced admission");
   memory.fence();
   check(budget.usedBytes()==0&&a.pageCount()==0,"idle region pinned quota");
   check(b.upload(0,voxel,2)&&b.uploadLighting(0,light,2),"cross-region starvation");
   uploads.submitUploads();check(b.upload(1,voxel,3),"pending replacement");
   var page=b.getPageBuffer(b.getResidency(0).pageIndex());
   b.releaseConsumed(0,2);memory.fence();
   check(budget.usedBytes()==RegionVoxelGpuStore.PAGE_BYTES&&!page.destroyed,"pending input page freed");
   check(b.hasVoxelInput(1,3)&&!b.getResidency(1).valid(),"pending publication changed");
   uploads.submitUploads();b.releaseConsumed(1,2);
   check(b.getResidency(1).valid(),"stale consumer revoked current input");
   b.releaseConsumed(1,3);memory.fence();
   check(page.destroyed&&budget.usedBytes()==0,"last allocation did not return quota");
   a.close();b.close();memory.fence();
   check(budget.usedBytes()==0,"closed tombstone double release");
  }
  var three=new RegionVoxelGpuStore.PageBudget(3*RegionVoxelGpuStore.PAGE_BYTES);
  var store=new RegionVoxelGpuStore(three);var full=new SectionVoxelSnapshot(RegionVoxelGpuStore.PAGE_BYTES);
  check(store.upload(0,full,1)&&store.upload(1,full,1)&&store.upload(2,full,1),"three pages");
  uploads.submitUploads();var last=store.getPageBuffer(2);store.releaseConsumed(1,1);memory.fence();
  check(store.getPageBuffer(1)==null&&store.getPageBuffer(2)==last&&!last.destroyed,"live page indices shifted");
  check(store.upload(3,full,1),"retired interior index not reusable");uploads.submitUploads();
  check(store.getResidency(3).pageIndex()==1&&store.getPageBuffer(2)==last,"reused index changed another owner");
  check(store.upload(4,voxel,2)==false,"bounded pages grew");
  store.close();uploads.submitUploads();memory.fence();
  check(three.usedBytes()==0&&last.destroyed,"close/pending completion leaked or double freed");
  check(memory.destroyed==260,"exact destruction count");
 }
} """
with tempfile.TemporaryDirectory() as directory:
    tmp=Path(directory)
    for name,data in files.items():
        p=tmp/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(data)
    for name in ['net/vulkanmod/render/chunk/voxel/RegionVoxelGpuStore.java',
                 'net/vulkanmod/render/chunk/voxel/RegionVoxelPageAllocator.java',
                 'net/vulkanmod/render/chunk/RegionBatchLayout.java']:
        p=tmp/name;p.parent.mkdir(parents=True,exist_ok=True)
        p.write_text((ROOT/'src/main/java'/name).read_text())
    classes=tmp/'classes';classes.mkdir()
    subprocess.run(['javac','--release','17','-d',str(classes)]+[str(p) for p in tmp.rglob('*.java')],check=True)
    subprocess.run(['java','-cp',str(classes),'net.vulkanmod.render.chunk.voxel.TurnoverRepro'],check=True)
print('Terrain input pages: bounded cross-region turnover, exact fenced charge/destruction, pending/close and stable index protection passed')
