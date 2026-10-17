#!/usr/bin/env python3
"""Run the production row kernel against an independent checked-pixel oracle."""
from pathlib import Path
import shutil
import subprocess
import tempfile

root = Path(__file__).resolve().parents[2]
pkg = Path('net/vulkanmod/render/texture')
mem = '''package org.lwjgl.system;
public final class MemoryUtil {
 private static final sun.misc.Unsafe U;
 static { try { var f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); f.setAccessible(true); U=(sun.misc.Unsafe)f.get(null); }
 catch(Exception e) { throw new RuntimeException(e); } }
 public static long allocate(long bytes) { long p=U.allocateMemory(bytes); U.setMemory(p,bytes,(byte)0); return p; }
 public static void free(long p) { U.freeMemory(p); }
 public static int memGetInt(long p) { return U.getInt(p); }
 public static void memPutInt(long p,int v) { U.putInt(p,v); }
}'''
image = '''package com.mojang.blaze3d.platform;
import net.vulkanmod.interfaces.VNativeImageI;
import org.lwjgl.system.MemoryUtil;
public final class NativeImage implements VNativeImageI, AutoCloseable {
 final int w,h; public boolean rgba=true; long p; public long mutations;
 public NativeImage(int w,int h) {this.w=w;this.h=h;p=MemoryUtil.allocate(w*h*4L);}
 public int getWidth(){return w;} public int getHeight(){return h;}
 public void setPixelRGBA(int x,int y,int v){check(x,y);MemoryUtil.memPutInt(p+4L*(y*w+x),v);mutations++;}
 public int getPixelRGBA(int x,int y){check(x,y);return MemoryUtil.memGetInt(p+4L*(y*w+x));}
 void check(int x,int y){if(p==0||!rgba||x<0||y<0||x>=w||y>=h)throw new IllegalArgumentException();}
 public long vulkanmod$getRgbaPixels(){return rgba?p:0;}
 public void vulkanmod$markPixelsChanged(){mutations++;}
 public long vulkanmod$getTrackedNativeBytes(){return p==0?0:w*h*4L;}
 public java.nio.ByteBuffer vulkanmod$getReadOnlyBuffer(){return null;}
 public long vulkanmod$getMutationGeneration(){return mutations;}
 public boolean vulkanmod$isRgba(){return rgba;}
 public void close(){if(p!=0){MemoryUtil.free(p);p=0;}}
}'''
harness = '''package net.vulkanmod.render.texture;
import com.mojang.blaze3d.platform.NativeImage;
import java.util.Random;
public class CpuInterpolationContract {
 static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
 static int mix(int a,int b,int step,int duration){
  double weight=1.0D-(double)step/(double)duration;
  int red=(int)(weight*(a&255)+(1.0D-weight)*(b&255));
  int green=(int)(weight*((a>>8)&255)+(1.0D-weight)*((b>>8)&255));
  int blue=(int)(weight*((a>>16)&255)+(1.0D-weight)*((b>>16)&255));
  return (a&0xff000000)|red|(green<<8)|(blue<<16);
 }
 static void test(int w,int h,int columns,int rows,int levels,Random r){
  NativeImage[] source=new NativeImage[levels], output=new NativeImage[levels];
  try {
   for(int mip=0;mip<levels;mip++) {
    source[mip]=new NativeImage(Math.max(1,w*columns>>mip),Math.max(1,h*rows>>mip));
    output[mip]=new NativeImage(Math.max(1,w>>mip)+2,Math.max(1,h>>mip)+2);
    for(int y=0;y<source[mip].getHeight();y++)for(int x=0;x<source[mip].getWidth();x++)source[mip].setPixelRGBA(x,y,r.nextInt());
   }
   for(int t=0;t<180;t++) {
    int current=r.nextInt(columns*rows),next=(current+1+r.nextInt(columns*rows-1))%(columns*rows);
    int duration=2+r.nextInt(1000),step=1+r.nextInt(duration-1);
    long[] before=new long[levels];
    for(int mip=0;mip<levels;mip++)before[mip]=source[mip].mutations;
    check(CpuSpriteInterpolation.tryInterpolate(source,output,w,h,columns,current,next,step,duration),"valid row kernel rejected");
    for(int mip=0;mip<levels;mip++) {
     int mw=w>>mip,mh=h>>mip;
     for(int y=0;y<output[mip].getHeight();y++)for(int x=0;x<output[mip].getWidth();x++) {
      int expected=0;
      if(x<mw&&y<mh) {
       int a=source[mip].getPixelRGBA((current%columns*w>>mip)+x,(current/columns*h>>mip)+y);
       int b=source[mip].getPixelRGBA((next%columns*w>>mip)+x,(next/columns*h>>mip)+y);
       expected=mix(a,b,step,duration);
      }
      check(output[mip].getPixelRGBA(x,y)==expected,"oracle or outside-frame guard pixel changed");
     }
     check(source[mip].mutations==before[mip],"source modified");
    }
   }
  } finally {for(var i:source)if(i!=null)i.close();for(var i:output)if(i!=null)i.close();}
 }
 static void rejected() {
  try(var a=new NativeImage(4,4);var b=new NativeImage(2,2);var d=new NativeImage(2,2);var small=new NativeImage(1,1)) {
   var src=new NativeImage[]{a,b}; var dst=new NativeImage[]{d,small};
   check(!CpuSpriteInterpolation.tryInterpolate(src,dst,2,2,2,0,8,1,3),"out-of-range frame");
   b.rgba=false;
   check(!CpuSpriteInterpolation.tryInterpolate(src,dst,2,2,2,0,1,1,3),"non RGBA later mip");
   check(d.mutations==0,"invalid later mip partially wrote earlier mip");b.rgba=true;
   small.close();
   check(!CpuSpriteInterpolation.tryInterpolate(src,dst,2,2,2,0,1,1,3),"closed output");
   check(d.mutations==0,"closed later mip partially wrote earlier mip");
   check(!CpuSpriteInterpolation.tryInterpolate(new NativeImage[]{a},new NativeImage[]{a},2,2,2,0,1,1,3),"source/output alias");
   check(!CpuSpriteInterpolation.tryInterpolate(new NativeImage[]{a},new NativeImage[]{d},2,2,2,0,0,1,3),"repeated index");
   check(!CpuSpriteInterpolation.tryInterpolate(new NativeImage[]{a},new NativeImage[]{d},2,2,2,0,1,3,3),"boundary clock");
   check(!CpuSpriteInterpolation.tryInterpolate(new NativeImage[]{a},new NativeImage[]{d},2,2,2,Integer.MAX_VALUE,0,1,3),"overflow frame coordinates");
   check(!CpuSpriteInterpolation.tryInterpolate(null,dst,2,2,2,0,1,1,3),"null source");
  }
 }
 public static void main(String[] args) {
  var r=new Random(20261017);
  for(int[] shape:new int[][]{{16,16,4,3,5},{3,2,2,2,3},{1,2,2,2,4},{31,17,3,2,6},{4,7,1,4,4}})
   test(shape[0],shape[1],shape[2],shape[3],shape[4],r);
  rejected();
  System.out.println("Bulk CPU interpolation passed: native-memory scalar oracle, odd/rectangular/all mips, RGB double truncation/current alpha, untouched padding/source, validation before writes, aliases/closed/non-RGBA/clock/overflow rejection");
 }
}'''
with tempfile.TemporaryDirectory(prefix='vulkanmod-cpu-interpolation-') as temp:
    folder = Path(temp)
    files = {
        pkg/'CpuSpriteInterpolation.java': (root/'src/main/java'/pkg/'CpuSpriteInterpolation.java').read_text(),
        pkg/'CpuInterpolationContract.java': harness,
        Path('net/vulkanmod/interfaces/VNativeImageI.java'): (root/'src/main/java/net/vulkanmod/interfaces/VNativeImageI.java').read_text(),
        Path('com/mojang/blaze3d/platform/NativeImage.java'): image,
        Path('org/lwjgl/system/MemoryUtil.java'): mem,
    }
    for name, text in files.items():
        path=folder/name
        path.parent.mkdir(parents=True,exist_ok=True)
        path.write_text(text)
    compiler = [shutil.which('javac')] if shutil.which('javac') else ['java', 'com.sun.tools.javac.Main']
    subprocess.run([*compiler,'--release','17','-d',temp,*[str(folder/p) for p in files]],check=True,timeout=30)
    subprocess.run(['java','-cp',temp,'net.vulkanmod.render.texture.CpuInterpolationContract'],check=True,timeout=30)
