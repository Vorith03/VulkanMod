#!/usr/bin/env python3
"""Exercise production admission/parser and upload error paths without a GPU."""
from pathlib import Path
import shutil
import subprocess
import tempfile

root = Path(__file__).resolve().parents[2]
def method(path, signature):
    text = (root / 'src/main/java' / path).read_text()
    start = text.index(signature)
    opening = text.index('{', start)
    depth = 1
    end = opening + 1
    while depth:
        depth += (text[end] == '{') - (text[end] == '}')
        end += 1
    return text[start:end]

world = method('net/vulkanmod/render/chunk/WorldRenderer.java', 'private void sortTranslucentSections(')
section = method('net/vulkanmod/render/chunk/RenderSection.java', 'public boolean resortTransparency(')
tick_path = 'net/vulkanmod/mixin/texture/MTextureManager.java'
tick = method(tick_path, 'public void tick()')
finish = method(tick_path, 'private void vulkanmod$finishTickUpload(')
selector_path = 'net/vulkanmod/vulkan/texture/VTextureSelector.java'
scope = '\n'.join(method(selector_path, sig) for sig in [
    'public static void beginSpriteUploadBatch()', 'public static int spriteUploadBatchDepth()',
    'public static void endSpriteUploadBatchesTo(', 'public static void endSpriteUploadBatch()',
    'public static void flushSpriteUploadBatch()'])
atlas_close = method('net/vulkanmod/mixin/texture/MSpriteAtlasTexture.java', 'private boolean vulkanmod$closeAtlasUploadBatch()')
sprite_methods = '\n'.join(method('net/vulkanmod/render/texture/SpriteUtil.java', sig) for sig in [
    'public static void addTransitionedLayout(', 'public static void transitionLayouts(', 'public static void clearTransitionedLayouts()'])

sources = {'net/vulkanmod/vulkan/memory/SystemMemorySample.java': (root / 'src/main/java/net/vulkanmod/vulkan/memory/SystemMemorySample.java').read_text(),
'net/vulkanmod/vulkan/memory/SampleContract.java': '''package net.vulkanmod.vulkan.memory;
import java.io.*;
import java.nio.file.*;
public class SampleContract {
 public static void main(String[] args) throws Exception {
  check("Cached: 900000 kB\\nMemTotal: 4194304 kB\\nMemAvailable: 2097152 kB\\n", 2048,4096);
  check("MemAvailable: 1024 kB\\nMemTotal: 8192 kB\\n",1,8);
  check("MemTotal: bad kB\\nMemAvailable: -1 kB\\n",-1,-1);
  check("MemTotal: 99999999999999999999999 kB\\nMemAvailable: 1023 kB\\n",0,-1);
  check("MemAvailable: 2048 kB\\n",2,-1);
  if(SystemMemorySample.read(Path.of(args[0])).availableMiB()!=-1) throw new AssertionError();
  System.out.println("Memory sample contract passed: order, unrelated fields, malformed, missing, overflow");
 }
 static void check(String s,long available,long total) throws Exception {
  var result=SystemMemorySample.parse(new StringReader(s));
  if(result.availableMiB()!=available || result.totalMiB()!=total) throw new AssertionError(result);
 }
}''',
'AuditContract.java': '''import java.util.*;
public class AuditContract {
 static void check(boolean value) { if(!value) throw new AssertionError(); }
 enum TerrainRenderType { TRANSLUCENT }
 static class CompiledSection {
  Set<TerrainRenderType> renderTypes = new HashSet<>(); Object transparencyState;
 }
 static class ChunkTask {
  static class SortTransparencyTask {
   boolean canceled; SortTransparencyTask(RenderSection s,TaskDispatcher t) {}
   void cancel() { canceled=true; }
  }
 }
 static class TaskDispatcher { int scheduled; void schedule(Object t) { scheduled++; } }
 static class RenderSection {
  CompiledSection compiled = new CompiledSection();
  CompileStatus compileStatus = new CompileStatus();
  static class CompileStatus { ChunkTask.SortTransparencyTask sortTask; }
  CompiledSection getCompiledSection() { return compiled; }
  __SECTION__
 }
 static class Profiler { int depth; void push(String s) { depth++; } void pop() { depth--; } }
 static class Minecraft { Profiler p=new Profiler(); Profiler getProfiler() { return p; } }
 static class ChunkQueue { List<RenderSection> sections=new ArrayList<>(); Iterator<RenderSection> iterator(boolean r) { return sections.iterator(); } }
 static class World {
  Minecraft minecraft=new Minecraft(); double xTransparentOld,yTransparentOld,zTransparentOld;
  TaskDispatcher taskDispatcher=new TaskDispatcher(); ChunkQueue chunkQueue=new ChunkQueue();
  __WORLD__
 }
 interface Tickable { void tick(); }
 static class CommandPool { static class CommandBuffer {} }
 static class Renderer { static boolean skipRendering; }
 static class GraphicsQueue {
  boolean active; int starts, submits;
  boolean hasActiveUploadBatch() { return active; }
  void startRecording() { active=true; starts++; }
  void endRecordingAndSubmit() { active=false; submits++; }
  CommandPool.CommandBuffer getCommandBuffer() { return new CommandPool.CommandBuffer(); }
 }
 static class Device { static GraphicsQueue queue=new GraphicsQueue(); static GraphicsQueue getGraphicsQueue() { return queue; } }
 static class TextureTickAttribution {
  enum Phase { BATCH_START,TICKABLE_LOOP,BATCH_DRAIN,LAYOUT_TRANSITIONS,QUEUE_SUBMIT }
  static int ticks; static long beginTick() { ticks++; return 0; }
  static void endTick(long t) { ticks--; }
  static long begin(Phase p) { return 0; } static void end(Phase p,long t) {}
 }
 static class VulkanImage {
  boolean fail; int transitions;
  void readOnlyLayout(CommandPool.CommandBuffer c) { transitions++; if(fail) throw new IllegalStateException("transition"); }
 }
 static class SpriteUtil {
  static boolean upload=true;
  static boolean shouldUpload() { return upload; }
  static Set<VulkanImage> transitionedLayouts=new HashSet<>(); static VulkanImage lastTransitionedLayout;
  __SPRITE__
 }
 static class VTextureSelector {
  static int spriteUploadDepth,spriteUploadRegionCount,flushes; static Object spriteUploadTexture;
  static long spriteUploadStagingBufferId; static final long VK_NULL_HANDLE=0;
  static boolean failFlush;
  static void flushSpriteUploadCopies() { flushes++; if(failFlush) throw new IllegalStateException("flush"); spriteUploadRegionCount=0; }
  __SCOPE__
 }
 static class TextureManager {
  Set<Tickable> tickableTextures=new LinkedHashSet<>();
  __TICK__
  __FINISH__
 }
 interface VAbstractTextureI { VulkanImage getVulkanImage(); }
 static class Atlas implements VAbstractTextureI {
  boolean vulkanmod$ownsAtlasUploadBatch; int vulkanmod$atlasUploadOriginalDepth;
  VulkanImage image=new VulkanImage(); public VulkanImage getVulkanImage() { return image; }
  __ATLAS__
 }
 static void reset() {
  check(TextureTickAttribution.ticks==0);
  Device.queue=new GraphicsQueue(); VTextureSelector.spriteUploadDepth=0; VTextureSelector.failFlush=false;
  SpriteUtil.upload=true; SpriteUtil.transitionedLayouts.clear(); SpriteUtil.lastTransitionedLayout=null;
 }
 public static void main(String[] args) {
  World w=new World();
  for(int i=0;i<40;i++) w.chunkQueue.sections.add(new RenderSection());
  for(int i=0;i<20;i++) {
   RenderSection s=new RenderSection(); s.compiled.renderTypes.add(TerrainRenderType.TRANSLUCENT); s.compiled.transparencyState=new Object(); w.chunkQueue.sections.add(s);
  }
  w.sortTranslucentSections(10,0,0); check(w.taskDispatcher.scheduled==15 && w.minecraft.p.depth==0);
  w.sortTranslucentSections(10,0,0); check(w.taskDispatcher.scheduled==15);
  RenderSection s=w.chunkQueue.sections.get(40); var old=s.compileStatus.sortTask;
  s.compiled.transparencyState=null;
  check(!s.resortTransparency(TerrainRenderType.TRANSLUCENT,w.taskDispatcher) && !old.canceled && w.taskDispatcher.scheduled==15);
  s.compiled.transparencyState=new Object(); check(s.resortTransparency(TerrainRenderType.TRANSLUCENT,w.taskDispatcher) && old.canceled);
  reset(); TextureManager tm=new TextureManager(); tm.tick();
  check(!Device.queue.active && Device.queue.starts==1 && Device.queue.submits==1 && VTextureSelector.spriteUploadDepth==0);
  reset(); RuntimeException original=new RuntimeException("tick");
  tm=new TextureManager(); tm.tickableTextures.add(()->{ VTextureSelector.beginSpriteUploadBatch(); throw original; });
  try { tm.tick(); throw new AssertionError(); } catch(RuntimeException e) { check(e==original); }
  check(VTextureSelector.spriteUploadDepth==0 && !Device.queue.active && Device.queue.submits==1);
  reset(); tm=new TextureManager();
  tm.tickableTextures.add(()->{ VTextureSelector.beginSpriteUploadBatch(); Device.queue.active=false; SpriteUtil.addTransitionedLayout(new VulkanImage()); throw new IllegalStateException("restart"); });
  try { tm.tick(); throw new AssertionError(); } catch(IllegalStateException expected) {}
  check(VTextureSelector.spriteUploadDepth==0 && Device.queue.submits==0 && SpriteUtil.transitionedLayouts.isEmpty());
  reset(); VTextureSelector.failFlush=true;
  tm=new TextureManager(); tm.tickableTextures.add(()->{ VTextureSelector.beginSpriteUploadBatch(); throw original; });
  try { tm.tick(); throw new AssertionError(); } catch(RuntimeException e) { check(e==original && e.getSuppressed().length==1); }
  check(VTextureSelector.spriteUploadDepth==0 && !Device.queue.active && Device.queue.submits==1);
  reset(); VulkanImage image=new VulkanImage(); image.fail=true;
  tm=new TextureManager(); tm.tickableTextures.add(()->SpriteUtil.addTransitionedLayout(image));
  try { tm.tick(); throw new AssertionError(); } catch(IllegalStateException expected) {}
  check(SpriteUtil.transitionedLayouts.isEmpty() && SpriteUtil.lastTransitionedLayout==null && !Device.queue.active);
  reset(); Device.queue.startRecording(); VTextureSelector.beginSpriteUploadBatch();
  tm=new TextureManager(); tm.tickableTextures.add(()->{ VTextureSelector.beginSpriteUploadBatch(); throw new IllegalStateException("nested"); });
  try { tm.tick(); throw new AssertionError(); } catch(IllegalStateException expected) {}
  check(Device.queue.active && Device.queue.submits==0 && VTextureSelector.spriteUploadDepth==1);
  VTextureSelector.endSpriteUploadBatchesTo(0); Device.queue.endRecordingAndSubmit();
  reset(); SpriteUtil.upload=false; tm=new TextureManager(); final int[] ticks={0}; tm.tickableTextures.add(()->ticks[0]++); tm.tick();
  check(ticks[0]==1 && Device.queue.starts==0 && Device.queue.submits==0);
  reset(); Atlas atlas=new Atlas(); atlas.vulkanmod$ownsAtlasUploadBatch=true; Device.queue.startRecording(); VTextureSelector.beginSpriteUploadBatch();
  check(atlas.vulkanmod$closeAtlasUploadBatch());
  check(!atlas.vulkanmod$ownsAtlasUploadBatch && !Device.queue.active && VTextureSelector.spriteUploadDepth==0 && atlas.image.transitions==1);
  reset(); atlas=new Atlas(); atlas.vulkanmod$atlasUploadOriginalDepth=1; Device.queue.startRecording(); VTextureSelector.beginSpriteUploadBatch(); VTextureSelector.beginSpriteUploadBatch();
  check(!atlas.vulkanmod$closeAtlasUploadBatch());
  check(Device.queue.active && Device.queue.submits==0 && VTextureSelector.spriteUploadDepth==1 && atlas.image.transitions==1);
  VTextureSelector.endSpriteUploadBatchesTo(0); Device.queue.endRecordingAndSubmit();
  check(TextureTickAttribution.ticks==0);
  System.out.println("Prebenchmark audit contract passed: admitted sort quota, no-state admission, successful/failed/nested upload cleanup, atlas draining, layout failures");
 }
}'''}
model_compile = method('net/vulkanmod/mixin/render/model/ModelPartM.java', 'protected void compile(')
sources['ModelScratchContract.java'] = r'''import java.util.*;
public class ModelScratchContract {
 static void check(boolean c) { if(!c) throw new AssertionError(); }
 static class Vector3f {
  static int allocations; float x,y,z;
  Vector3f() { allocations++; }
  Vector3f(float x,float y,float z) { this(); this.x=x; this.y=y; this.z=z; }
  Vector3f set(Vector3f other) { x=other.x; y=other.y; z=other.z; return this; }
  float x() { return x; } float y() { return y; } float z() { return z; }
 }
 static class Matrix3f { Vector3f transform(Vector3f v) { v.x*=2; v.y*=3; v.z*=4; return v; } }
 static class Matrix4f {}
 static class PoseStack { static class Pose { Matrix3f normal() { return new Matrix3f(); } Matrix4f pose() { return new Matrix4f(); } } }
 interface VertexConsumer {
  void vertex(float x,float y,float z,float r,float g,float b,float a,float u,float v,int overlay,int light,float nx,float ny,float nz);
 }
 interface ExtendedVertexBuilder extends VertexConsumer {
  void vertex(float x,float y,float z,int color,float u,float v,int overlay,int light,int normal);
 }
 static class VertexUtil {
  static int packColor(float r,float g,float b,float a) { return 1; }
  static int packNormal(float x,float y,float z) { return (int)(x*10000+y*100+z); }
 }
 interface ModelPartCubeMixed { CubeModel getCubeModel(); }
 static class ModelPart {
  static class Cube implements ModelPartCubeMixed {
   CubeModel model; Cube(CubeModel m) { model=m; } public CubeModel getCubeModel() { return model; }
  }
  static class Polygon { Vector3f normal; Vertex[] vertices; Polygon(float x,float y,float z) { normal=new Vector3f(x,y,z); vertices=new Vertex[]{new Vertex(),new Vertex(),new Vertex(),new Vertex()}; } }
  static class Vertex { Vector3f pos=new Vector3f(1,2,3); float u,v; }
 }
 static class CubeModel { ModelPart.Polygon[] polygons; CubeModel(ModelPart.Polygon... p) { polygons=p; } ModelPart.Polygon[] getPolygons() { return polygons; } void transformVertices(Matrix4f m) {} }
 static class Model {
  List<ModelPart.Cube> cubes;
  Model(ModelPart.Polygon... p) { cubes=List.of(new ModelPart.Cube(new CubeModel(p))); }
  __COMPILE__
 }
 static class Recording implements VertexConsumer {
  List<float[]> normals=new ArrayList<>(); Runnable reenter; boolean entered;
  public void vertex(float x,float y,float z,float r,float g,float b,float a,float u,float v,int overlay,int light,float nx,float ny,float nz) {
   normals.add(new float[]{nx,ny,nz});
   if(reenter!=null && !entered) { entered=true; reenter.run(); }
  }
 }
 static class Packed extends Recording implements ExtendedVertexBuilder {
  List<Integer> packed=new ArrayList<>();
  public void vertex(float x,float y,float z,int color,float u,float v,int overlay,int light,int normal) { packed.add(normal); }
 }
 public static void main(String[] args) {
  ModelPart.Polygon a=new ModelPart.Polygon(1,0,0),b=new ModelPart.Polygon(0,1,0),c=new ModelPart.Polygon(0,0,1);
  Model model=new Model(a,b,c); PoseStack.Pose pose=new PoseStack.Pose(); Recording fallback=new Recording();
  Vector3f.allocations=0; model.compile(pose,fallback,1,2,1,1,1,1);
  check(Vector3f.allocations==1 && fallback.normals.size()==12);
  check(Arrays.equals(fallback.normals.get(0),new float[]{2,0,0}));
  check(Arrays.equals(fallback.normals.get(4),new float[]{0,3,0}));
  check(Arrays.equals(fallback.normals.get(8),new float[]{0,0,4}));
  check(a.normal.x==1 && b.normal.y==1 && c.normal.z==1);
  Packed packed=new Packed(); Vector3f.allocations=0; model.compile(pose,packed,1,2,1,1,1,1);
  check(Vector3f.allocations==1 && packed.packed.size()==12 && packed.normals.isEmpty());
  for(int i=0;i<12;i++) { float[] n=fallback.normals.get(i); check(packed.packed.get(i)==VertexUtil.packNormal(n[0],n[1],n[2])); }
  Model child=new Model(new ModelPart.Polygon(7,8,9)); Recording nested=new Recording(); Recording parent=new Recording();
  parent.reenter=()->child.compile(pose,nested,1,2,1,1,1,1);
  Vector3f.allocations=0; model.compile(pose,parent,1,2,1,1,1,1);
  check(Vector3f.allocations==2 && parent.normals.size()==12 && nested.normals.size()==4);
  for(int i=0;i<12;i++) check(Arrays.equals(parent.normals.get(i),fallback.normals.get(i)));
  System.out.println("Model normal scratch contract passed: one allocation, immutable source normals, packed/fallback parity, reentrant consumer");
 }
}'''.replace('__COMPILE__', model_compile)
for name, value in [('SECTION',section),('WORLD',world),('SPRITE',sprite_methods),('SCOPE',scope),('TICK',tick),('FINISH',finish),('ATLAS',atlas_close)]:
    sources['AuditContract.java'] = sources['AuditContract.java'].replace("__" + name + "__",value)
with tempfile.TemporaryDirectory(prefix='vulkanmod-audit-') as folder:
    target = Path(folder)
    for path, source in sources.items():
        file = target / path
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_text(source)
    compiler = [shutil.which('javac')] if shutil.which('javac') else ['java','com.sun.tools.javac.Main']
    subprocess.run([*compiler,'--release','17','-d',folder,*map(str,target.rglob('*.java'))],check=True,timeout=30)
    subprocess.run(['java','-cp',folder,'net.vulkanmod.vulkan.memory.SampleContract',str(target/'missing')],check=True,timeout=30)
    subprocess.run(['java','-cp',folder,'AuditContract'],check=True,timeout=30)
    subprocess.run(['java','-cp',folder,'ModelScratchContract'],check=True,timeout=30)
