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
