#!/usr/bin/env python3
"""Test the production legacy FBO routing against controlled renderer/texture lifetimes."""
from pathlib import Path
import shutil
import subprocess
import tempfile
root=Path(__file__).resolve().parents[2]
sources={
'it/unimi/dsi/fastutil/ints/Int2ReferenceOpenHashMap.java': 'package it.unimi.dsi.fastutil.ints; public class Int2ReferenceOpenHashMap<T> extends java.util.HashMap<Integer,T> {}',
'org/lwjgl/opengl/GL11.java': 'package org.lwjgl.opengl; public class GL11 { public static final int GL_TEXTURE_2D=3553; }',
'org/lwjgl/opengl/GL30C.java': '''package org.lwjgl.opengl; public class GL30C {
 public static final int GL_FRAMEBUFFER=36160,GL_RENDERBUFFER=36161,GL_COLOR_ATTACHMENT0=36064,GL_DEPTH_ATTACHMENT=36096,
 GL_FRAMEBUFFER_COMPLETE=36053,GL_FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT=36055,GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT=36054,GL_FRAMEBUFFER_UNSUPPORTED=36061;
}''',
'org/lwjgl/vulkan/VK10.java': 'package org.lwjgl.vulkan; public class VK10 { public static final int VK_ATTACHMENT_LOAD_OP_LOAD=0; }',
'net/vulkanmod/vulkan/texture/VulkanImage.java': '''package net.vulkanmod.vulkan.texture;
public class VulkanImage {
 public int width,height,frees; boolean color,depth;
 public VulkanImage(int w,int h,boolean c,boolean d) { width=w; height=h; color=c; depth=d; }
 public boolean supportsColorAttachment() { return color; } public boolean supportsDepthAttachment() { return depth; }
}''',
'net/vulkanmod/gl/GlTexture.java': '''package net.vulkanmod.gl;
import net.vulkanmod.vulkan.texture.VulkanImage;
public class GlTexture {
 static java.util.Map<Integer,GlTexture> textures=new java.util.HashMap<>(); VulkanImage image;
 static void put(int id,VulkanImage image) { GlTexture t=new GlTexture(); t.image=image; textures.put(id,t); }
 public static GlTexture getTexture(int id) { return textures.get(id); }
 public static VulkanImage getVulkanImage(int id) { GlTexture t=textures.get(id); return t==null?null:t.image; }
}''',
'net/vulkanmod/vulkan/Renderer.java': '''package net.vulkanmod.vulkan;
import net.vulkanmod.vulkan.framebuffer.*;
public class Renderer {
 public boolean recording; public RenderPass pass; public Framebuffer framebuffer; public int ends;
 static Renderer instance=new Renderer(); public static Renderer getInstance() { return instance; }
 public boolean isRecordingFrame() { return recording; }
 public RenderPass getBoundRenderPass() { return pass; }
 public void endRenderPass() { if(pass==null) throw new AssertionError(); pass=null; ends++; }
 public void setBoundFramebuffer(Framebuffer f) { framebuffer=f; }
}''',
'net/vulkanmod/vulkan/framebuffer/Framebuffer.java': '''package net.vulkanmod.vulkan.framebuffer;
import net.vulkanmod.vulkan.texture.VulkanImage;
public class Framebuffer {
 public VulkanImage color,depth; public boolean cleaned; public static int created,retired;
 public Framebuffer(VulkanImage c,VulkanImage d) { color=c; depth=d; created++; }
 public void cleanUp() { if(cleaned) throw new AssertionError("double retirement"); cleaned=true; retired++; }
}''',
'net/vulkanmod/vulkan/framebuffer/RenderPass.java': '''package net.vulkanmod.vulkan.framebuffer;
public class RenderPass {
 public Framebuffer framebuffer; public int load; public boolean cleaned; public static boolean fail;
 public void cleanUp() { if(cleaned) throw new AssertionError(); cleaned=true; }
 public static class Builder {
  RenderPass pass=new RenderPass(); public Builder(Framebuffer f) { pass.framebuffer=f; }
  public Builder setLoadOp(int op) { pass.load=op; return this; }
  public RenderPass build() { if(fail) throw new IllegalStateException("create"); return pass; }
 }
}''',
'net/vulkanmod/vulkan/framebuffer/RenderTargetManager.java': '''package net.vulkanmod.vulkan.framebuffer;
import net.vulkanmod.vulkan.Renderer;
public class RenderTargetManager {
 public static int mains,binds;
 public static void bindMain(boolean v,int w,int h) { Renderer r=Renderer.getInstance(); if(r.pass!=null) r.endRenderPass(); r.framebuffer=null; mains++; }
 public static void bind(Framebuffer f,RenderPass p,boolean v,int w,int h) { Renderer r=Renderer.getInstance(); if(r.pass!=null && r.pass!=p) r.endRenderPass(); r.pass=p; r.framebuffer=f; binds++; }
}''',
'net/vulkanmod/gl/FramebufferContract.java': '''package net.vulkanmod.gl;
import net.vulkanmod.vulkan.*;
import net.vulkanmod.vulkan.texture.*;
import net.vulkanmod.vulkan.framebuffer.*;
import static org.lwjgl.opengl.GL30C.*;
import static org.lwjgl.opengl.GL11.*;
public class FramebufferContract {
 static void check(boolean c) { if(!c) throw new AssertionError(); }
 static void status(int expected) { check(GlFramebuffer.glCheckFramebufferStatus(GL_FRAMEBUFFER)==expected); }
 static void attach(int slot,int texture) { GlFramebuffer.glFramebufferTexture2D(GL_FRAMEBUFFER,slot,GL_TEXTURE_2D,texture,0); }
 static void unsupported(Runnable r) { try { r.run(); throw new AssertionError(); } catch(UnsupportedOperationException expected) {} }
 public static void main(String[] args) {
  Renderer r=Renderer.getInstance();
  int id=GlFramebuffer.genFramebufferId(); check(id>0); GlFramebuffer.bindFramebuffer(GL_FRAMEBUFFER,id); status(GL_FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT);
  VulkanImage c=new VulkanImage(8,6,true,false),d=new VulkanImage(8,6,false,true);
  GlTexture.put(1,c); GlTexture.put(2,d); GlTexture.put(3,new VulkanImage(9,6,false,true)); GlTexture.put(4,new VulkanImage(8,6,false,false));
  attach(GL_COLOR_ATTACHMENT0,1); status(GL_FRAMEBUFFER_COMPLETE); check(Framebuffer.created==0);
  r.recording=true; GlFramebuffer.bindFramebuffer(GL_FRAMEBUFFER,id);
  Framebuffer original=r.framebuffer; check(original.color==c && original.depth==null && r.pass.load==0);
  GlFramebuffer.bindFramebuffer(GL_FRAMEBUFFER,id); check(r.framebuffer==original && Framebuffer.created==1);
  attach(GL_DEPTH_ATTACHMENT,2); check(original.cleaned && r.framebuffer.depth==d);
  attach(GL_DEPTH_ATTACHMENT,3); status(GL_FRAMEBUFFER_UNSUPPORTED); check(r.pass==null && r.framebuffer==null);
  attach(GL_DEPTH_ATTACHMENT,0); status(GL_FRAMEBUFFER_COMPLETE);
  Framebuffer borrowed=r.framebuffer;
  GlFramebuffer.textureStorageChanged(1); check(borrowed.cleaned && r.pass==null && c.frees==0 && d.frees==0);
  GlTexture.put(1,new VulkanImage(12,10,true,false)); GlFramebuffer.bindFramebuffer(GL_FRAMEBUFFER,id);
  check(r.framebuffer.color.width==12);
  attach(GL_COLOR_ATTACHMENT0,4); status(GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT);
  attach(GL_COLOR_ATTACHMENT0,0); status(GL_FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT);
  attach(GL_DEPTH_ATTACHMENT,2); status(GL_FRAMEBUFFER_COMPLETE); check(r.framebuffer.color==null && r.framebuffer.depth==d);
  borrowed=r.framebuffer; GlFramebuffer.deleteFramebuffer(id); check(borrowed.cleaned && GlFramebuffer.getBoundFramebufferId()==0);
  int retired=Framebuffer.retired; GlFramebuffer.deleteFramebuffer(id); check(Framebuffer.retired==retired); status(GL_FRAMEBUFFER_COMPLETE);
  unsupported(()->GlFramebuffer.bindFramebuffer(36008,0));
  unsupported(()->GlFramebuffer.bindRenderbuffer(GL_RENDERBUFFER,1)); GlFramebuffer.bindRenderbuffer(GL_RENDERBUFFER,0);
  unsupported(()->GlFramebuffer.glRenderbufferStorage(GL_RENDERBUFFER,0,8,6));
  unsupported(()->GlFramebuffer.glFramebufferRenderbuffer(GL_FRAMEBUFFER,GL_DEPTH_ATTACHMENT,GL_RENDERBUFFER,0));
  int broken=GlFramebuffer.genFramebufferId(); GlFramebuffer.bindFramebuffer(GL_FRAMEBUFFER,broken); RenderPass.fail=true;
  retired=Framebuffer.retired;
  try { attach(GL_COLOR_ATTACHMENT0,1); throw new AssertionError(); } catch(IllegalStateException expected) {}
  check(Framebuffer.retired==retired+1); RenderPass.fail=false;
  GlFramebuffer.bindFramebuffer(GL_FRAMEBUFFER,broken); check(r.framebuffer.color.width==12);
  GlFramebuffer.deleteFramebuffer(broken); check(c.frees==0 && d.frees==0);
  System.out.println("Legacy framebuffer contract passed: status, real pass routing, LOAD reuse, incomplete isolation, borrowed lifetime, storage replacement, explicit unsupported paths, failed creation");
 }
}'''}
production='net/vulkanmod/gl/GlFramebuffer.java'
sources[production]=(root/'src/main/java'/production).read_text()
with tempfile.TemporaryDirectory(prefix='vulkanmod-fbo-') as folder:
 target=Path(folder)
 for path,source in sources.items():
  file=target/path; file.parent.mkdir(parents=True,exist_ok=True); file.write_text(source)
 compiler=[shutil.which('javac')] if shutil.which('javac') else ['java','com.sun.tools.javac.Main']
 subprocess.run([*compiler,'--release','17','-d',folder,*map(str,target.rglob('*.java'))],check=True,timeout=30)
 subprocess.run(['java','-cp',folder,'net.vulkanmod.gl.FramebufferContract'],check=True,timeout=30)
