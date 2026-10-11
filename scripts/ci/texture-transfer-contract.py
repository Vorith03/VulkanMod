#!/usr/bin/env python3
"""Exercise the real unified texture transfer overlap policy with Java 17."""
from pathlib import Path
import os
import subprocess
import tempfile

root = Path(__file__).resolve().parents[2]
pkg = Path("net/vulkanmod/vulkan/texture")
harness = r'''package net.vulkanmod.vulkan.texture;
public final class TextureTransferBatchContract {
    private static void check(boolean value, String label) {
        if(!value) throw new AssertionError(label);
    }
    public static void main(String[] args) {
        int[] mips = {0, 1, 0, 0, 0};
        int[] xs = {100, 50, 300, Integer.MAX_VALUE - 1, 0};
        int[] ys = {100, 50, 300, 0, Integer.MAX_VALUE - 1};
        int[] widths = {16, 8, 16, 2, 2};
        int[] heights = {16, 8, 16, 2, 2};
        check(!TextureTransferBatchPolicy.overlapsAny(mips,xs,ys,widths,heights,3,
                0,116,100,16,16),"touching right edge");
        check(!TextureTransferBatchPolicy.overlapsAny(mips,xs,ys,widths,heights,3,
                1,100,100,16,16),"different mip");
        check(TextureTransferBatchPolicy.overlapsAny(mips,xs,ys,widths,heights,3,
                0,115,115,16,16),"single texel overlap");
        check(!TextureTransferBatchPolicy.overlapsAny(mips,xs,ys,widths,heights,3,
                0,120,120,8,8),"diagonal disjoint");
        check(TextureTransferBatchPolicy.overlapsAny(mips,xs,ys,widths,heights,5,
                0,Integer.MAX_VALUE,0,1,1),"overflow safe X");
        check(TextureTransferBatchPolicy.overlapsAny(mips,xs,ys,widths,heights,5,
                0,0,Integer.MAX_VALUE,1,1),"overflow safe Y");
        check(!TextureTransferBatchPolicy.overlapsAny(mips,xs,ys,widths,heights,0,
                0,100,100,16,16),"empty queue");
        try {
            TextureTransferBatchPolicy.overlapsAny(mips,xs,ys,widths,heights,6,
                    0,100,100,1,1);
            throw new AssertionError("bad count accepted");
        } catch(IllegalArgumentException expected) {}
        System.out.println("Unified texture transfer region geometry passed");
    }
}
'''
with tempfile.TemporaryDirectory(prefix="vulkanmod-texture-batch-") as directory:
    target = Path(directory) / pkg
    target.mkdir(parents=True)
    (target / "TextureTransferBatchPolicy.java").write_text(
        (root / "src/main/java" / pkg / "TextureTransferBatchPolicy.java").read_text())
    (target / "TextureTransferBatchContract.java").write_text(harness)
    java_home = os.environ.get("JAVA_HOME")
    javac = str(Path(java_home)/"bin/javac") if java_home else "javac"
    java = str(Path(java_home)/"bin/java") if java_home else "java"
    subprocess.run([javac, "--release", "17", "-d", directory,
                    str(target/"TextureTransferBatchPolicy.java"),
                    str(target/"TextureTransferBatchContract.java")], check=True, timeout=30)
    subprocess.run([java,"-cp",directory,
                    "net.vulkanmod.vulkan.texture.TextureTransferBatchContract"],check=True,timeout=30)
