#!/usr/bin/env python3
"""Run real prewarmer/state/cache contracts; stub only loader/device surroundings."""
from pathlib import Path
import shutil
import subprocess
import tempfile

root = Path(__file__).resolve().parents[2]
package = 'net/vulkanmod/vulkan/shader'
constants = {
    'VK_FORMAT_UNDEFINED': 0, 'VK_FORMAT_R8G8B8A8_UNORM': 37,
    'VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST': 3, 'VK_PRIMITIVE_TOPOLOGY_LINE_LIST': 1,
    'VK_PRIMITIVE_TOPOLOGY_LINE_STRIP': 2, 'VK_LOGIC_OP_CLEAR': 0, 'VK_LOGIC_OP_SET': 15,
    'VK_LOGIC_OP_OR_REVERSE': 11,
}
for prefix, names in {
    'VK_BLEND_FACTOR_': ['ZERO','ONE','SRC_COLOR','ONE_MINUS_SRC_COLOR','DST_COLOR','ONE_MINUS_DST_COLOR','SRC_ALPHA','ONE_MINUS_SRC_ALPHA'],
    'VK_BLEND_OP_': ['ADD','SUBTRACT','REVERSE_SUBTRACT','MIN','MAX'],
    'VK_COMPARE_OP_': ['NEVER','LESS','EQUAL','LESS_OR_EQUAL','GREATER','NOT_EQUAL','GREATER_OR_EQUAL','ALWAYS'],
    'VK_STENCIL_OP_': ['KEEP','ZERO','REPLACE','INCREMENT_AND_CLAMP','DECREMENT_AND_CLAMP','INVERT','INCREMENT_AND_WRAP','DECREMENT_AND_WRAP'],
}.items():
    constants.update({prefix + name: i for i, name in enumerate(names)})
constants.update({f'VK_COLOR_COMPONENT_{name}_BIT': 1 << i for i, name in enumerate('RGBA')})
stubs = {
    'org/lwjgl/vulkan/VK10.java': 'package org.lwjgl.vulkan; public class VK10 {' + ''.join(f'public static final int {k}={v};' for k,v in constants.items()) + '}',
    'com/mojang/blaze3d/vertex/VertexFormat.java': 'package com.mojang.blaze3d.vertex; public record VertexFormat(String name) {}',
    'com/mojang/blaze3d/platform/GlStateManager.java': '''package com.mojang.blaze3d.platform;
        public class GlStateManager { public enum LogicOp { OR_REVERSE }
        public static class SourceFactor { public int value; } public static class DestFactor { public int value; } }''',
    'net/minecraftforge/fml/loading/FMLPaths.java': '''package net.minecraftforge.fml.loading;
        public enum FMLPaths { GAMEDIR; public java.nio.file.Path get() { return java.nio.file.Path.of(System.getProperty("test.game")); } }''',
    'net/vulkanmod/Initializer.java': '''package net.vulkanmod; public class Initializer {
        public static final Log LOGGER=new Log(); public static class Log {
        public void warn(String s,Object... args) {} public void info(String s,Object... args) {} } }''',
    'net/vulkanmod/vulkan/shader/cache/CompilationCache.java': '''package net.vulkanmod.vulkan.shader.cache;
        public class CompilationCache { public static boolean enabled=true; public static boolean enabled() { return enabled; } }''',
    'net/vulkanmod/vulkan/framebuffer/Framebuffer.java': '''package net.vulkanmod.vulkan.framebuffer;
        public class Framebuffer {
        private final int format,depthFormat; private final boolean stencil; private boolean retired;
        public Framebuffer(int format,int depthFormat,boolean stencil) { this.format=format; this.depthFormat=depthFormat; this.stencil=stencil; }
        public void retire() { retired=true; }
        public Object getColorAttachment() { return retired || format==0 ? null : this; }
        public Object getDepthAttachment() { return retired || depthFormat==0 ? null : this; }
        public int getFormat() { return format; } public int getDepthFormat() { return depthFormat; }
        public boolean hasStencilAttachment() { return stencil; } }''',
    'net/vulkanmod/vulkan/framebuffer/RenderPass.java': '''package net.vulkanmod.vulkan.framebuffer;
        public record RenderPass(Framebuffer framebuffer) { public Framebuffer getFramebuffer() { return framebuffer; } }''',
    'net/vulkanmod/vulkan/VRenderSystem.java': '''package net.vulkanmod.vulkan;
        import net.vulkanmod.vulkan.shader.PipelineState;
        public class VRenderSystem { public static boolean cull,depthTest,depthMask,stencilTest;
        public static int depthFun=515,stencilFun=519,stencilRef,stencilCompareMask=-1,stencilWriteMask=-1,
            stencilFailOp=7680,stencilDepthFailOp=7680,stencilPassOp=7680;
        public static int getColorMask() { return 15; }
        public static PipelineState.DepthState getDepthState() { return new PipelineState.DepthState(depthTest,depthMask,depthFun); }
        public static PipelineState.StencilState getStencilState() { return new PipelineState.StencilState(stencilTest,stencilFun,stencilRef,stencilCompareMask,stencilWriteMask,stencilFailOp,stencilDepthFailOp,stencilPassOp); } }''',
}
harness = r'''package net.vulkanmod.vulkan.shader;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.vulkanmod.vulkan.VRenderSystem;
import net.vulkanmod.vulkan.framebuffer.*;
import net.vulkanmod.vulkan.shader.cache.CompilationCache;
import java.nio.*; import java.nio.file.*; import java.util.*;
import static org.lwjgl.vulkan.VK10.*;
public class VariantContract {
 static void check(boolean ok,String why) { if(!ok) throw new AssertionError(why); }
 static PipelineState state(RenderPass pass,int reference) {
  return new PipelineState(PipelineState.DEFAULT_BLEND_STATE,PipelineState.DEFAULT_DEPTH_STATE,
   PipelineState.DEFAULT_LOGICOP_STATE,PipelineState.DEFAULT_COLORMASK,pass,
   new PipelineState.StencilState(false,519,reference,-1,-1,7680,7680,7680));
 }
 static PipelineVariantPrewarmer.Session open(ByteBuffer vertex,ByteBuffer fragment) {
  return PipelineVariantPrewarmer.open(vertex,fragment,new VertexFormat("position"),null);
 }
 public static void main(String[] args) throws Exception {
  ByteBuffer vertex=ByteBuffer.wrap(new byte[]{9,1,2,3,4,9}).position(1).limit(5);
  ByteBuffer fragment=ByteBuffer.wrap(new byte[]{5,6,7,8});
  if(args.length>0) {
   check(open(vertex,fragment)==null,"Disabled feature created history session");
   check(!Files.exists(Path.of(System.getProperty("test.game"))),"Disabled feature touched filesystem");
   System.out.println("Disabled pipeline history contract passed"); return;
  }
  var session=open(vertex,fragment);
  var pass=new RenderPass(new Framebuffer(37,129,true));
  // An empty list and indexOf miss both produced -1 in the broken implementation.
  session.observe(state(pass,7),VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST,false); session.persist();
  var restored=open(vertex,fragment);
  var resized=new RenderPass(new Framebuffer(37,129,true));
  var first=restored.replays(state(resized,0),false);
  check(first.size()==1,"First cold observation was lost");
  check(first.get(0).state().equals(state(resized,7)),"Persisted key changed");
  check(first.get(0).state().hashCode()==state(resized,7).hashCode(),"Replay hash differs");
  check(first.get(0).state().renderPass==resized,"Stale native pass retained");
  check(restored.replays(state(resized,0),false).isEmpty(),"Boundary replayed twice");
  var retiringPass=new RenderPass(new Framebuffer(37,129,true));
  var retiringState=state(retiringPass,7);
  var handles=new HashMap<PipelineState,Long>(); handles.put(retiringState,123L);
  int stableHash=retiringState.hashCode(); retiringPass.getFramebuffer().retire();
  check(retiringState.hashCode()==stableHash,"Framebuffer retirement mutated pipeline-key hash");
  check(retiringState.equals(state(resized,7)),"Framebuffer retirement mutated pipeline-key equality");
  check(Objects.equals(handles.get(state(resized,7)),123L),"Compatible resize lost cached pipeline handle");
  check(vertex.position()==1 && vertex.limit()==5 && fragment.position()==0,"Shader cursor consumed");
  check(open(fragment,vertex).replays(state(pass,0),false).isEmpty(),"Shader identity collision");
  check(PipelineVariantPrewarmer.open(vertex,fragment,new VertexFormat("block"),null).replays(state(pass,0),false).isEmpty(),"Vertex layout collision");
  var instance=new InstanceVertexFormat(4,List.of(new InstanceVertexFormat.Attribute(1,InstanceVertexFormat.Format.FLOAT,0)));
  check(PipelineVariantPrewarmer.open(vertex,fragment,new VertexFormat("position"),instance).replays(state(pass,0),false).isEmpty(),"Instance layout collision");
  check(open(vertex,fragment).replays(state(pass,0),true).isEmpty(),"Depth clamp boundary crossed");
  check(open(vertex,fragment).replays(state(new RenderPass(new Framebuffer(44,129,true)),0),false).isEmpty(),"Attachment format boundary crossed");
  VRenderSystem.cull=true;
  check(open(vertex,fragment).replays(state(pass,0),false).isEmpty(),"Cull boundary crossed"); VRenderSystem.cull=false;
  for(int i=0;i<40;i++) session.observe(state(pass,i),VK_PRIMITIVE_TOPOLOGY_LINE_LIST,false);
  session.persist();
  var limited=open(vertex,fragment).replays(state(pass,0),false);
  check(limited.size()==4 && limited.get(0).state().stencilState.reference==39,"Bounded newest-first replay differs");
  for(var r:limited) check(r.topology()==VK_PRIMITIVE_TOPOLOGY_LINE_LIST,"Topology lost");
  // Test cache envelope and codec rejection with the real implementation.
  var observedField=session.getClass().getDeclaredField("observed"); observedField.setAccessible(true);
  List<?> observed=(List<?>)observedField.get(session); check(observed.size()==32,"History cap failed");
  var encode=PipelineVariantPrewarmer.class.getDeclaredMethod("encode",List.class); encode.setAccessible(true);
  var decode=PipelineVariantPrewarmer.class.getDeclaredMethod("decode",byte[].class); decode.setAccessible(true);
  byte[] bytes=(byte[])encode.invoke(null,observed);
  check(observed.equals(decode.invoke(null,(Object)bytes)),"All fields did not round trip");
  check(((List<?>)decode.invoke(null,(Object)Arrays.copyOf(bytes,bytes.length-1))).isEmpty(),"Truncation admitted");
  byte[] bad=bytes.clone(); ByteBuffer.wrap(bad).order(ByteOrder.LITTLE_ENDIAN).putInt(8,33);
  check(((List<?>)decode.invoke(null,(Object)bad)).isEmpty(),"Oversized count admitted");
  bad=bytes.clone(); ByteBuffer.wrap(bad).order(ByteOrder.LITTLE_ENDIAN).putInt(12,2);
  check(((List<?>)decode.invoke(null,(Object)bad)).isEmpty(),"Malformed boolean admitted");
  bad=bytes.clone(); ByteBuffer.wrap(bad).order(ByteOrder.LITTLE_ENDIAN).putInt(32,999);
  check(((List<?>)decode.invoke(null,(Object)bad)).size()==31,"Bad topology poisoned valid neighbors");
  Path entry; try(var paths=Files.walk(Path.of(System.getProperty("test.game")))) {
   entry=paths.filter(p->p.toString().endsWith(".bin")).findFirst().orElseThrow();
  }
  byte[] corrupt=Files.readAllBytes(entry); corrupt[corrupt.length-1]^=1; Files.write(entry,corrupt);
  check(open(vertex,fragment).replays(state(pass,0),false).isEmpty(),"Corrupt disk history reused");
  CompilationCache.enabled=false; check(open(vertex,fragment)==null,"Disabled parent cache bypass ignored");
  System.out.println("Pipeline history contract passed: cold persistence, exact keys, cursors/identity, live pass, boundaries, bounds, corruption and disabled admission");
 }
}'''
with tempfile.TemporaryDirectory(prefix='vulkanmod-variants-') as folder:
    dest = Path(folder)
    for name in ('PipelineState.java', 'PipelineVariantPrewarmer.java', 'InstanceVertexFormat.java'):
        stubs[f'{package}/{name}'] = (root / 'src/main/java' / package / name).read_text()
    cache_path = 'net/vulkanmod/vulkan/shader/cache/BoundedDiskCache.java'
    stubs[cache_path] = (root / 'src/main/java' / cache_path).read_text()
    stubs[f'{package}/VariantContract.java'] = harness
    for path, source in stubs.items():
        target = dest / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(source)
    compiler = [shutil.which('javac')] if shutil.which('javac') else ['java', 'com.sun.tools.javac.Main']
    subprocess.run([*compiler, '--release', '17', '-d', folder, *map(str,dest.rglob('*.java'))], check=True, timeout=30)
    for enabled in (False, True):
        subprocess.run(['java', f'-Dvulkanmod.pipelineVariantPrewarm={str(enabled).lower()}',
                        f'-Dtest.game={dest / ("enabled" if enabled else "disabled")}', '-cp', folder,
                        'net.vulkanmod.vulkan.shader.VariantContract', *([] if enabled else ['disabled'])],
                       check=True, timeout=30)
