#!/usr/bin/env python3
"""Run the production query owner against controllable Vulkan availability/failure bindings."""
from pathlib import Path
import subprocess
import shutil
import tempfile

root = Path(__file__).resolve().parents[2]
sources = {
    'org/lwjgl/vulkan/VkCommandBuffer.java': 'package org.lwjgl.vulkan; public class VkCommandBuffer {}',
    'org/lwjgl/vulkan/VkQueryPoolCreateInfo.java': '''package org.lwjgl.vulkan;
public class VkQueryPoolCreateInfo {
 public static VkQueryPoolCreateInfo calloc(Object stack) { return new VkQueryPoolCreateInfo(); }
 public VkQueryPoolCreateInfo sType(int x) { return this; }
 public VkQueryPoolCreateInfo queryType(int x) { return this; }
 public VkQueryPoolCreateInfo queryCount(int x) { return this; }
}''',
    'org/lwjgl/system/MemoryStack.java': '''package org.lwjgl.system;
public class MemoryStack implements AutoCloseable {
 public static MemoryStack stackPush() { return new MemoryStack(); }
 public java.nio.LongBuffer mallocLong(int n) { return java.nio.LongBuffer.allocate(n); }
 public void close() {}
}''',
    'org/lwjgl/vulkan/VK10.java': '''package org.lwjgl.vulkan;
public class VK10 {
 public static final long VK_NULL_HANDLE=0;
 public static final int VK_STRUCTURE_TYPE_QUERY_POOL_CREATE_INFO=1, VK_QUERY_TYPE_TIMESTAMP=2,
  VK_SUCCESS=0, VK_NOT_READY=1, VK_QUERY_RESULT_64_BIT=2, VK_QUERY_RESULT_WAIT_BIT=4,
  VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT=1, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT=2;
 public static int resets, waits, result=VK_NOT_READY;
 public static int vkCreateQueryPool(Object d, Object i, Object a, java.nio.LongBuffer h) { h.put(0, 9); return 0; }
 public static void vkDestroyQueryPool(Object d, long p, Object a) {}
 public static void vkCmdResetQueryPool(VkCommandBuffer c, long p, int q, int n) { resets++; }
 public static void vkCmdWriteTimestamp(VkCommandBuffer c, int stage, long p, int q) {}
 public static int vkGetQueryPoolResults(Object d, long p, int q, int n, java.nio.LongBuffer v, long stride, int flags) {
  if((flags & VK_QUERY_RESULT_WAIT_BIT)!=0) waits++;
  v.put(0, 100); v.put(1, 150); return result;
 }
}''',
    'net/vulkanmod/Initializer.java': '''package net.vulkanmod;
public class Initializer {
 public static final Logger LOGGER=new Logger();
 public static class Logger { public void info(String s, Object... args) {} public void warn(String s, Object... args) {} }
}''',
    'net/vulkanmod/vulkan/Device.java': '''package net.vulkanmod.vulkan;
public class Device {
 public static final Object device=new Object();
 public static Queue getGraphicsQueue() { return new Queue(); }
 public static class Queue { public void startRecording() {} public void endRecordingAndSubmit() {} }
}''',
    'net/vulkanmod/vulkan/Vulkan.java': 'package net.vulkanmod.vulkan; public class Vulkan { public static void waitIdle() { throw new AssertionError("Unexpected idle wait"); } }',
    'net/vulkanmod/render/profiling/GpuTimestampProfiler.java': '''package net.vulkanmod.render.profiling;
public class GpuTimestampProfiler {
 static boolean capture;
 public static boolean requested() { return true; }
 static boolean capturing() { return capture; }
}''',
    'net/vulkanmod/render/profiling/PerformanceProfiler.java': '''package net.vulkanmod.render.profiling;
public class PerformanceProfiler {
 static String line;
 public static void benchmarkEvent(String s) { line=s; }
}''',
    'net/vulkanmod/render/profiling/UploadGpuContract.java': '''package net.vulkanmod.render.profiling;
import org.lwjgl.vulkan.VkCommandBuffer;
import static org.lwjgl.vulkan.VK10.*;
public class UploadGpuContract {
 static void check(boolean b) { if(!b) throw new AssertionError(); }
 static final VkCommandBuffer commands=new VkCommandBuffer();
 static int batch() {
  int t=TextureUploadGpuProfiler.begin(commands);
  TextureUploadGpuProfiler.end(commands,t);
  TextureUploadGpuProfiler.submitted(t); return t;
 }
 static void summary(String expected) {
  TextureUploadGpuProfiler.emitSummary();
  if(!PerformanceProfiler.line.contains(expected)) throw new AssertionError(PerformanceProfiler.line);
 }
 public static void main(String[] args) {
  TextureUploadGpuProfiler.verifyForCi();
  TextureUploadGpuProfiler.create(64, 1);
  check(batch()==-1 && resets==0); // Warmup must never reset/write a query.
  GpuTimestampProfiler.capture=true;
  TextureUploadGpuProfiler.resetMeasurements();
  int recording=TextureUploadGpuProfiler.begin(commands);
  TextureUploadGpuProfiler.collect(false); check(waits==0); // Recording is not submitted.
  TextureUploadGpuProfiler.end(commands,recording);
  TextureUploadGpuProfiler.submitted(recording);
  TextureUploadGpuProfiler.submitted(recording); // Duplicate admission is ignored.
  for(int i=1;i<64;i++) check(batch()==i);
  check(batch()==-1 && resets==64);
  TextureUploadGpuProfiler.collect(false); // NOT_READY cannot release any range.
  check(batch()==-1 && resets==64 && waits==0);
  result=VK_SUCCESS; TextureUploadGpuProfiler.collect(false);
  check(batch()==0 && waits==0); // Completion permits reuse.
  summary("submitted_batches=65 measured_batches=65 unresolved_batches=0");
  check(PerformanceProfiler.line.contains("capacity_drops=2"));
  check(PerformanceProfiler.line.contains("batch_ms_sum=0.003"));
  // Delayed old-capture data cannot contaminate a new capture, or be reset early.
  result=VK_NOT_READY; check(batch()==0);
  int waitsBefore=waits; TextureUploadGpuProfiler.resetMeasurements();
  check(waits==waitsBefore && batch()==1);
  result=VK_SUCCESS; TextureUploadGpuProfiler.collect(false);
  summary("submitted_batches=1 measured_batches=1 unresolved_batches=0");
  // A driver error is not evidence of completion: quarantine, never reuse that range.
  result=-4; check(batch()==0); TextureUploadGpuProfiler.collect(false);
  check(batch()==1);
  result=VK_SUCCESS; TextureUploadGpuProfiler.collect(false);
  summary("read_failures=1");
  check(PerformanceProfiler.line.contains("unresolved_batches=1"));
  TextureUploadGpuProfiler.destroy(); check(batch()==-1);
  System.out.println("Texture upload GPU contract passed: warmup, pending capacity, nonblocking collection, reuse, epochs, driver failure quarantine");
 }
}''',
}
production = 'net/vulkanmod/render/profiling/TextureUploadGpuProfiler.java'
sources[production] = (root / 'src/main/java' / production).read_text()
with tempfile.TemporaryDirectory(prefix='vulkanmod-upload-gpu-') as folder:
    target = Path(folder)
    for path, source in sources.items():
        file = target / path
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_text(source)
    compiler = [shutil.which('javac')] if shutil.which('javac') else ['java', 'com.sun.tools.javac.Main']
    subprocess.run([*compiler, '--release', '17', '-d', folder, *map(str, target.rglob('*.java'))], check=True, timeout=30)
    subprocess.run(['java', '-cp', folder, 'net.vulkanmod.render.profiling.UploadGpuContract'], check=True, timeout=30)
