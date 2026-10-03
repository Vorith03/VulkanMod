#!/usr/bin/env python3
"""Run admission/pressure boundaries against the production Java budget."""
from pathlib import Path
import shutil
import subprocess
import tempfile

root = Path(__file__).resolve().parents[2]
package = Path('net/vulkanmod/render/chunk/build')
harness = '''package net.vulkanmod.render.chunk.build;
public class BudgetContract {
 static void check(boolean value) { if(!value) throw new AssertionError(); }
 public static void main(String[] args) {
  FrameWorkBudget b = new FrameWorkBudget();
  b.begin(1, 100, 1000000, 2, 16, 16);
  check(b.admit(100)); check(b.admit(101)); check(!b.admit(102));
  // Additional portal/upload calls in one frame cannot reset admission.
  b.begin(1, 500, 100000000, 64, 1, 100);
  check(!b.admit(500));
  b.begin(2, 100, 1000000, 64, 16, 16);
  check(b.admit(100)); check(b.admit(1000099)); check(!b.admit(1000100));
  // Long-frame pressure quarters allowance; at least one atomic result progresses.
  b.begin(3, 100, 1000000, 64, 80, 16);
  check(b.admit(10000000)); check(!b.admit(10000001));
  b.begin(4, 100, 1000000, 64, 64, 16);
  check(b.admit(100)); check(!b.admit(250100));
  b.begin(5, Long.MAX_VALUE - 10, 100000, 64, 16, 16);
  check(b.admit(Long.MAX_VALUE - 10)); check(b.admit(Long.MIN_VALUE + 5));
  check(!b.admit(Long.MIN_VALUE + 100000));
  b.begin(6, 100, 1000000, 0, 16, 16);
  check(b.admit(100)); check(!b.admit(101));
  b.begin(7, 100, 1000000, 100, 16, 16);
  for(int i=0;i<64;i++) check(b.admit(100)); check(!b.admit(100));
  check(FrameWorkBudget.workerLimit(8, 16, 16) == 8);
  check(FrameWorkBudget.workerLimit(8, 32, 16) == 4);
  check(FrameWorkBudget.workerLimit(8, 1000, 16) == 2);
  check(FrameWorkBudget.workerLimit(3, 32, 16) == 2);
  check(FrameWorkBudget.workerLimit(1, 1000, 16) == 1);
  System.out.println("Chunk frame budget contract passed: count, deadline, pressure, progress, repeated views, wrap");
 }
}'''
with tempfile.TemporaryDirectory(prefix='vulkanmod-chunk-budget-') as folder:
    sources = Path(folder) / package
    sources.mkdir(parents=True)
    (sources/'FrameWorkBudget.java').write_text((root/'src/main/java'/package/'FrameWorkBudget.java').read_text())
    (sources/'BudgetContract.java').write_text(harness)
    compiler = [shutil.which('javac')] if shutil.which('javac') else ['java', 'com.sun.tools.javac.Main']
    subprocess.run([*compiler, '--release', '17', '-d', folder, *map(str, sources.glob('*.java'))], check=True, timeout=30)
    subprocess.run(['java', '-cp', folder, 'net.vulkanmod.render.chunk.build.BudgetContract'], check=True, timeout=30)
