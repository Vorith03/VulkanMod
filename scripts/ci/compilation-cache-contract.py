#!/usr/bin/env python3
"""Execute cache corruption/admission/device-identity contracts without Vulkan."""
from pathlib import Path
import shutil
import subprocess
import tempfile

root = Path(__file__).resolve().parents[2]
package = Path('net/vulkanmod/vulkan/shader/cache')
harness = r'''package net.vulkanmod.vulkan.shader.cache;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
public class CacheContract {
    static void check(boolean value, String why) { if(!value) throw new AssertionError(why); }
    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args[0]);
        BoundedDiskCache cache = new BoundedDiskCache(dir, 64, 280, 2);
        byte[] a = BoundedDiskCache.digest(new byte[]{1}), b = BoundedDiskCache.digest(new byte[]{2});
        byte[] payload = new byte[64]; Arrays.fill(payload, (byte)7);
        check(cache.read(a) == null, "cold miss");
        check(cache.write(a, payload), "initial write");
        check(Arrays.equals(cache.read(a), payload), "round trip");
        check(cache.write(b, payload), "second entry");
        check(!cache.write(BoundedDiskCache.digest(new byte[]{3}), payload), "entry/byte limit");
        check(cache.write(a, new byte[]{3}), "replacement at capacity");
        check(!cache.write(a, new byte[65]), "oversize write");
        Path file = dir.resolve(HexFormat.of().formatHex(a) + ".bin");
        byte[] encoded = Files.readAllBytes(file); encoded[encoded.length-1] ^= 1;
        Files.write(file, encoded);
        check(cache.read(a) == null, "checksum rejection");
        cache.write(a, payload);
        // Valid checksum with wrong embedded identity must not be reused.
        encoded = Files.readAllBytes(file); System.arraycopy(b, 0, encoded, 12, 32);
        Files.write(file, encoded); check(cache.read(a) == null, "wrong key rejection");
        Files.write(file, new byte[141]); check(cache.read(a) == null, "oversize read");
        Files.write(file, new byte[10]); check(cache.read(a) == null, "truncation");
        byte[] uuid = new byte[16]; uuid[4] = 7;
        byte[] nativeCache = ByteBuffer.allocate(40).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(32).putInt(1).putInt(0x1002).putInt(0x73bf).put(uuid).array();
        check(PipelineCacheIdentity.matches(nativeCache, 0x1002, 0x73bf, uuid), "Vulkan header");
        check(!PipelineCacheIdentity.matches(nativeCache, 0x10de, 0x73bf, uuid), "vendor change");
        byte[] other = uuid.clone(); other[4]++;
        check(!PipelineCacheIdentity.matches(nativeCache, 0x1002, 0x73bf, other), "UUID change");
        nativeCache[0] = 31;
        check(!PipelineCacheIdentity.matches(nativeCache, 0x1002, 0x73bf, uuid), "header length");
        check(!PipelineCacheIdentity.matches(new byte[31], 0, 0, uuid), "short header");
        System.out.println("Compilation cache contract passed: corruption, identity, limits, replacement");
    }
}'''
with tempfile.TemporaryDirectory(prefix='vulkanmod-cache-') as folder:
    dest = Path(folder)
    sources = dest / package
    sources.mkdir(parents=True)
    for name in ('BoundedDiskCache.java', 'PipelineCacheIdentity.java'):
        (sources / name).write_text((root / 'src/main/java' / package / name).read_text())
    (sources / 'CacheContract.java').write_text(harness)
    compiler = [shutil.which('javac')] if shutil.which('javac') else ['java', 'com.sun.tools.javac.Main']
    subprocess.run([*compiler, '--release', '17', '-d', folder, *map(str, sources.glob('*.java'))], check=True, timeout=30)
    subprocess.run(['java', '-cp', folder, 'net.vulkanmod.vulkan.shader.cache.CacheContract', str(dest/'cache')], check=True, timeout=30)
