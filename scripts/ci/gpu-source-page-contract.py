#!/usr/bin/env python3
"""Isolated Java 17 contract for shared device-local animation source page layout."""
import os
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[2]
pkg = Path("net/vulkanmod/render/texture")
test = '''package net.vulkanmod.render.texture;
public final class GpuSourcePageLayoutContract {
    static void check(boolean b, String msg) {
        if(!b) throw new AssertionError(msg);
    }
    public static void main(String[] args) {
        GpuSourcePageLayout page = new GpuSourcePageLayout(32);
        check(page.reserve(5) == 0, "first allocation");
        check(page.reserve(4) == 8, "aligned second allocation");
        check(page.reserve(1) == 12, "third allocation");
        check(page.used() == 13, "used bytes");
        check(page.reserve(20) == -1, "limit enforced");
        check(page.used() == 13, "failure must not consume bytes");
        page.release();
        check(!page.empty(), "must retain live slices");
        check(page.reserve(4) == 16, "released slice must not be reused");
        page.release();
        page.release();
        check(!page.empty(), "last lease survives");
        page.release();
        check(page.empty(), "page eligible for deferred retirement");
        try { page.release(); throw new AssertionError("underflow accepted"); }
        catch(IllegalStateException expected) {}
        try { page.reserve(0); throw new AssertionError("zero accepted"); }
        catch(IllegalArgumentException expected) {}
        check(new GpuSourcePageLayout(4).reserve(Integer.MAX_VALUE) == -1,
              "overflow budget");
        GpuSourcePageLayout aligned = new GpuSourcePageLayout(4096, 256);
        check(aligned.reserve(3) == 0, "aligned first");
        check(aligned.reserve(4) == 256, "hardware storage-offset alignment");
        try { new GpuSourcePageLayout(1024, 24); throw new AssertionError("bad alignment accepted"); }
        catch(IllegalArgumentException expected) {}
        System.out.println("GPU shared texture source page layout contract passed");
    }
}
'''
with tempfile.TemporaryDirectory(prefix="vulkanmod-gpu-source-pool-") as directory:
    d = Path(directory) / pkg
    d.mkdir(parents=True)
    (d / "GpuSourcePageLayout.java").write_text(
        (root / "src/main/java" / pkg / "GpuSourcePageLayout.java").read_text())
    (d / "GpuSourcePageLayoutContract.java").write_text(test)
    jhome = os.environ.get("JAVA_HOME")
    javac = str(Path(jhome)/"bin/javac") if jhome else "javac"
    java = str(Path(jhome)/"bin/java") if jhome else "java"
    subprocess.run([javac, "--release", "17", "-d", directory,
                    str(d / "GpuSourcePageLayout.java"),
                    str(d / "GpuSourcePageLayoutContract.java")], check=True, timeout=30)
    subprocess.run([java, "-cp", directory,
                    "net.vulkanmod.render.texture.GpuSourcePageLayoutContract"],
                   check=True, timeout=30)

# Exercise the production inventory planner with adversarial order, fixed memory,
# delayed retirement, failed allocation, reload/close, and density ranking.
admission_test = """package net.vulkanmod.render.texture;
public final class AdmissionContract {
    static void check(boolean b, String why) { if(!b) throw new AssertionError(why); }
    public static void main(String[] args) {
        var copy = GpuAnimationAdmission.register("copy-first", 100, 0);
        var large = GpuAnimationAdmission.register("large-interpolation", 100, 10);
        var small = GpuAnimationAdmission.register("small-interpolation", 20, 10);
        GpuAnimationAdmission.prepare(100, 0);
        check(small.selected() && !large.selected() && !copy.selected(), "useful density beats first admission");
        small.admitted();
        var newcomer = GpuAnimationAdmission.register("new-high-density", 80, 1000);
        GpuAnimationAdmission.prepare(100, 20);
        check(small.selected() && newcomer.selected(), "live source pinned, bounded new source");
        newcomer.failed();
        GpuAnimationAdmission.prepare(100, 20);
        check(!newcomer.selected() && small.selected() && !copy.selected(), "failure does not evict active work");
        small.close();
        GpuAnimationAdmission.prepare(100, 20);
        check(!large.selected() && !copy.selected(), "pending retirement still charged");
        GpuAnimationAdmission.memoryReleased();
        GpuAnimationAdmission.prepare(100, 0);
        check(large.selected() && !copy.selected(), "reclaimed memory prioritizes interpolation");
        copy.close(); large.close(); newcomer.close(); small.close();
        check(GpuAnimationAdmission.stats().candidates() == 0, "reload unregisters every candidate, idempotent close");
        var huge = GpuAnimationAdmission.register("huge", Long.MAX_VALUE, Double.MAX_VALUE);
        var fits = GpuAnimationAdmission.register("fits", 4, 1);
        GpuAnimationAdmission.prepare(4, 0);
        check(!huge.selected() && fits.selected(), "overflow/oversize skips without starving later work");
        huge.close(); fits.close();
        check(GpuAnimationAdmission.register("bad", 4, Double.NaN) == null, "reject invalid score");
        System.out.println("GPU animation fixed-budget admission contract passed");
    }
}
"""
with tempfile.TemporaryDirectory(prefix="vulkanmod-gpu-admission-") as directory:
    d = Path(directory) / pkg
    d.mkdir(parents=True)
    (d / "GpuAnimationAdmission.java").write_text((root / "src/main/java" / pkg / "GpuAnimationAdmission.java").read_text())
    (d / "AdmissionContract.java").write_text(admission_test)
    subprocess.run([javac, "--release", "17", "-d", directory, str(d / "GpuAnimationAdmission.java"), str(d / "AdmissionContract.java")], check=True, timeout=30)
    subprocess.run([java, "-Dvulkanmod.experimentalGpuAnimationAdmission=true", "-cp", directory, "net.vulkanmod.render.texture.AdmissionContract"], check=True, timeout=30)
