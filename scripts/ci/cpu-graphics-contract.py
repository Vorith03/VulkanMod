#!/usr/bin/env python3
"""Execute stack accounting, real Java 17 JFR, and stripped-runtime fallback."""
from pathlib import Path
import os
import subprocess
import tempfile

root = Path(__file__).resolve().parents[2]
package = Path('net/vulkanmod/render/profiling')
profiler = (root / 'src/main/java' / package / 'PerformanceProfiler.java').read_text()
finish = profiler.split('public static boolean finishAutomatedCapture(String reason) {', 1)[1].split('public static void abortAutomatedCapture', 1)[0]
assert finish.index('return false;') < finish.index('CpuGraphicsProfiler.finish') < finish.index('TextureTickAttribution.emitSummary')
start = profiler.split('private static boolean startCapture(long now) {', 1)[1].split('private static Path resolveOutputPath', 1)[0]
assert start.index('CpuGraphicsProfiler.start') < start.index('captureStartNanos = now')
assert 'if (!active || !captureArmed || frameActive)' in profiler
assert 'CpuGraphicsProfiler.cancel();' in profiler.split('private static boolean closeOutput()', 1)[1]
assert 'AutomatedBenchmark.onProfilerCaptureStarted(now);' in start
controller = (root / 'src/main/java' / package / 'AutomatedBenchmark.java').read_text()
alignment = controller.split('public static void onProfilerCaptureStarted(long now) {', 1)[1].split('}', 1)[0]

accounting = r'''package net.vulkanmod.render.profiling;
import java.util.*;
public class CpuGraphicsAccountingContract {
 static boolean ENABLED=true; enum State { SETTLING,CAPTURING }; static State state=State.SETTLING;
 static long captureStartedAt=1;
 static void align(long now) { __ALIGNMENT__ }
 static void check(boolean b,String s) { if(!b) throw new AssertionError(s); }
 public static void main(String[] args) {
  align(9); check(captureStartedAt==1,"warmup clock changed");
  state=State.CAPTURING; align(9); check(captureStartedAt==9,"setup charged to deadline");
  ENABLED=false; align(11); check(captureStartedAt==9,"disabled clock changed");
  var samples=new CpuGraphicsSamples(7L);
  var texture=List.of("some.mod.CustomAnimation.blend:12", "net.minecraft.client.renderer.texture.SpriteContents.upload:42");
  samples.accept(false,7,"Render thread",texture,false);
  samples.accept(false,7,"Render thread",texture,false);
  samples.accept(true,7,"Render thread",List.of("org.lwjgl.vulkan.VK10.nvkQueueSubmit:1"),false);
  samples.accept(false,9,"worker",List.of("net.vulkanmod.render.chunk.build.ChunkTask.run:1"),false);
  samples.accept(false,7,"Render thread",List.of("unknown.mod.Callback.draw:2"),true);
  samples.accept(false,10,"server",List.of("net.minecraft.server.Server.tick:2"),false);
  samples.accept(false,7,"Render thread",List.of(),false);
  samples.accept(false,7,"Render thread",List.of("net.vulkanmod.render.profiling.PerformanceProfiler.emitSummary:3"),false);
  List<String> lines=new ArrayList<>(); samples.emit(lines::add);
  String all=String.join("\n",lines);
  check(all.contains("java_execution_samples=7 native_boundary_samples=1"),all);
  check(all.contains("relevant_java_samples=4 relevant_native_samples=1"),all);
  check(all.contains("excluded_background_samples=1 missing_stack_samples=1 truncated_stack_samples=1 profiler_observer_samples=1"),all);
  check(all.contains("owner=render/texture_pixels_transfers java_samples=2 native_samples=0"),all);
  check(all.contains("owner=graphics_worker/terrain_mesh java_samples=1"),all);
  check(all.contains("owner=render/render_thread_unclassified java_samples=1"),all);
  check(all.contains("some.mod.CustomAnimation.blend:12;net.minecraft.client.renderer.texture.SpriteContents.upload:42"),all);
  check(all.contains("kind=java_execution rank=1 owner=render/texture_pixels_transfers samples=2 pct_relevant_kind=50.000"),all);
  var bounded=new CpuGraphicsSamples(7);
  List<String> deep=new ArrayList<>();
  for(int i=0;i<60;i++) deep.add("mod.Render"+i+".draw:1");
  // A graphics caller beyond the displayed frame cap must still identify a worker.
  deep.add("net.minecraft.client.renderer.GameRenderer.render:1");
  bounded.accept(false,8,"deep",deep,false);
  for(int i=0;i<CpuGraphicsSamples.MAX_STACKS+10;i++) bounded.accept(false,7,"Render",List.of("mod.Unique"+i+".draw:1"),false);
  lines.clear(); bounded.emit(lines::add); all=String.join("\n",lines);
  check(all.contains("unique_stacks=2048 stack_cap=2048 stack_overflow_samples=11"),all);
  check(all.contains("owner=graphics_worker/other_graphics java_samples=1"),all);
  check(all.contains("frame_cap=48"),all);
  check(lines.stream().filter(s->s.startsWith("cpu_graphics_hotspot ")).count()<=80,all);
  lines.clear(); new CpuGraphicsSamples(7).emit(lines::add);
  check(lines.get(0).contains("java_execution_samples=0 native_boundary_samples=0"),"state leaked");
  System.out.println("CPU graphics accounting passed: routes, mod stacks, workers, unknowns, separate kinds, observer exclusion, bounds, reset");
 }
}'''.replace('__ALIGNMENT__', alignment)
capture = r'''import java.nio.file.*;
import java.util.*;
import net.vulkanmod.render.profiling.CpuGraphicsProfiler;
public class CpuGraphicsCaptureContract {
 static void check(boolean b,String s) { if(!b) throw new AssertionError(s); }
 public static void main(String[] args) throws Exception {
  Path dir=Path.of(args[0]); List<String> lines=new ArrayList<>();
  if(args.length>1) {
   CpuGraphicsProfiler.start(dir.resolve("capture.log"),true,lines::add);
   check(lines.get(0).contains("status=unavailable"),lines.toString());
   CpuGraphicsProfiler.finish(lines::add); CpuGraphicsProfiler.cancel();
   System.out.println("CPU graphics stripped-runtime fallback passed"); return;
  }
  CpuGraphicsProfiler.start(dir.resolve("disabled.log"),false,lines::add);
  check(lines.get(0).contains("requested=false status=disabled"),lines.toString());
  CpuGraphicsProfiler.finish(lines::add); check(lines.size()==1,"disabled capture emitted samples");
  lines.clear();
  CpuGraphicsProfiler.start(dir.resolve("capture.log"),true,lines::add);
  check(lines.get(0).contains("status=active"),lines.toString());
  net.minecraft.client.renderer.texture.ContractTexture.work();
  CpuGraphicsProfiler.finish(lines::add);
  String text=String.join("\n",lines);
  check(text.contains("cpu_graphics_recording status=stopped"),text);
  check(text.contains("cpu_graphics_samples status=complete"),text);
  check(text.contains("net.minecraft.client.renderer.texture.ContractTexture.work:"),text);
  check(text.contains("owner=render/texture_pixels_transfers"),text);
  int n=lines.size(); CpuGraphicsProfiler.finish(lines::add); CpuGraphicsProfiler.cancel();
  check(lines.size()==n,"duplicate completion");
  try(var files=Files.list(dir)) {
   Path raw=files.filter(p->p.toString().endsWith(".jfr")).findFirst().orElseThrow();
   check(Files.size(raw)>0,"missing raw JFR");
   try(var recording=new jdk.jfr.consumer.RecordingFile(raw)) { check(recording.hasMoreEvents(),"invalid JFR"); }
  }
  lines.clear(); CpuGraphicsProfiler.start(dir.resolve("cancel.log"),true,lines::add);
  CpuGraphicsProfiler.cancel(); CpuGraphicsProfiler.finish(lines::add);
  check(lines.size()==1,"cancel unexpectedly emitted complete summary");
  lines.clear(); CpuGraphicsProfiler.start(dir.resolve("missing/capture.log"),true,lines::add);
  check(lines.get(0).contains("status=unavailable"),"output failure hidden");
  CpuGraphicsProfiler.cancel();
  System.out.println("CPU graphics actual JFR passed: real samples/raw recording, disabled, cancel, duplicate finish and I/O failure");
 }
}'''
work = '''package net.minecraft.client.renderer.texture;
public class ContractTexture {
 static volatile double sink;
 public static void work() {
  long until=System.nanoTime()+2_000_000_000L; double x=1;
  while(System.nanoTime()<until) { for(int i=0;i<10000;i++) x=Math.sqrt(x+i+1); sink=x; }
 }
}'''
with tempfile.TemporaryDirectory(prefix='vulkanmod-cpu-graphics-') as directory:
    d = Path(directory)
    sources = d / package
    sources.mkdir(parents=True)
    for name in ('CpuGraphicsProfiler.java', 'CpuGraphicsSamples.java'):
        (sources / name).write_text((root / 'src/main/java' / package / name).read_text())
    (sources / 'CpuGraphicsAccountingContract.java').write_text(accounting)
    (d / 'CpuGraphicsCaptureContract.java').write_text(capture)
    worker = d / 'net/minecraft/client/renderer/texture/ContractTexture.java'
    worker.parent.mkdir(parents=True)
    worker.write_text(work)
    java_home = os.environ.get('JAVA_HOME')
    javac = str(Path(java_home)/'bin/javac') if java_home else 'javac'
    java = str(Path(java_home)/'bin/java') if java_home else 'java'
    subprocess.run([javac, '--release', '17', '-d', directory,
                    *map(str, d.rglob('*.java'))], check=True, timeout=30)
    subprocess.run([java, '-cp', directory, 'net.vulkanmod.render.profiling.CpuGraphicsAccountingContract'], check=True, timeout=30)
    subprocess.run([java, '-cp', directory, 'CpuGraphicsCaptureContract', directory], check=True, timeout=30)
    subprocess.run([java, '--limit-modules', 'java.base', '-cp', directory,
                    'CpuGraphicsCaptureContract', directory, 'unavailable'], check=True, timeout=30)
