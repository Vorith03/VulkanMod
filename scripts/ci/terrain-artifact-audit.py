#!/usr/bin/env python3
"""Audit downloaded CI bytes without rebuilding or substituting an executable."""
from collections import Counter
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import re
import struct
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[2]


def unique(paths, description):
    paths = list(paths)
    if len(paths) != 1:
        raise AssertionError(f"Expected one {description}, got {len(paths)}")
    return paths[0]


def inspect_jar(path, commit, number):
    data = path.read_bytes()
    with zipfile.ZipFile(io.BytesIO(data)) as jar:
        assert jar.testzip() is None, "Runtime JAR CRC failure"
        names = set(jar.namelist())
        manifest = jar.read("META-INF/MANIFEST.MF").decode()
        version = f"0.3.2-forge.2-build.{number}-g{commit[:8]}"
        assert f"Implementation-Version: {version}" in manifest, "Wrong executable source/version"
        assert "MixinConfigs: vulkanmod.mixins.json" in manifest
        mods = jar.read("META-INF/mods.toml").decode()
        assert 'modId="vulkanmod"' in mods and f'version="{version}"' in mods
        assert 'versionRange="[47.3.0,48)"' in mods
        assert 'versionRange="[1.20.1,1.20.2)"' in mods
        for name in ("vulkanmod.mixins.json", "vulkanmod.refmap.json",
                     "META-INF/accesstransformer.cfg",
                     "linux/x64/org/lwjgl/vma/liblwjgl_vma.so",
                     "linux/x64/org/lwjgl/shaderc/libshaderc.so",
                     "windows/x64/org/lwjgl/vma/lwjgl_vma.dll",
                     "windows/x64/org/lwjgl/shaderc/shaderc.dll"):
            assert name in names and jar.read(name), f"Missing packaged runtime entry: {name}"
        classes = ["render/chunk/TerrainAppearanceEvents", "render/chunk/TerrainDrawReadback",
                   "render/chunk/TerrainDrawProvenance", "render/chunk/DrawBuffers",
                   "render/chunk/voxel/RegionVoxelGpuStore", "vulkan/memory/MemoryManager"]
        for name in classes:
            code = jar.read("net/vulkanmod/" + name + ".class")
            assert code[:4] == b"\xca\xfe\xba\xbe" and struct.unpack(">H", code[6:8])[0] == 61, name
        assert b"freeBufferAfterFence" in jar.read("net/vulkanmod/vulkan/memory/MemoryManager.class")
        assert b"VULKANMOD_TERRAIN_INPUT_PAGE_BUDGET" in jar.read("net/vulkanmod/render/chunk/TerrainAppearanceEvents.class")
        metadata = json.loads(jar.read("META-INF/jarjar/metadata.json"))
        nested = unique((entry for entry in metadata["jars"]
                         if entry["identifier"]["artifact"] == "lwjgl-vulkan"), "nested Vulkan module")
        assert nested["version"]["artifactVersion"] == "3.3.1"
        assert not any(name.startswith("org/lwjgl/vulkan/") for name in names), "Flattened Vulkan module"
        with zipfile.ZipFile(io.BytesIO(jar.read(nested["path"]))) as binding:
            assert binding.testzip() is None, "Nested Vulkan module CRC failure"
            assert "org/lwjgl/vulkan/VkInstance.class" in binding.namelist()
            assert "META-INF/versions/9/module-info.class" in binding.namelist()
    return {"filename": path.name, "bytes": len(data), "sha256": hashlib.sha256(data).hexdigest(),
            "commit": commit, "version": version, "crc": "passed", "java_class_major": 61,
            "forge": "[47.3.0,48)", "minecraft": "[1.20.1,1.20.2)",
            "mixins_nested_vulkan_linux_windows_natives": "passed"}


def inspect_logs(root):
    errors = []
    for path in root.glob("*.log"):
        for line in path.read_text(errors="replace").splitlines():
            if re.search(r"Validation Error|SYNC-HAZARD", line):
                errors.append(path.name + ": " + line)
    assert not errors, "\n".join(errors[:10])
    native = (root / "vulkan-smoke-no-splash.log").read_text()
    assert "VULKANMOD_GPU_TERRAIN_INPUT_PAGE_TURNOVER_OK" in native
    screenshot = (root / "vulkan-screenshot-smoke.log").read_text()
    assert "VULKANMOD_TERRAIN_DRAW_READBACK_OK" in screenshot
    for folder in (root / "run/gpu-world-smoke/vanilla", root / "run/gpu-world-smoke"):
        subprocess.run([sys.executable, str(ROOT / "scripts/ci/gpu-world-pixels.py"), str(folder)], check=True)
    spec = importlib.util.spec_from_file_location("terrain_appearance", ROOT / "scripts/ci/terrain-appearance.py")
    appearance = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(appearance)
    observations = {}
    for stem in ("vulkan-world-vanilla-cpu", "vulkan-world-vanilla-gpu",
                 "vulkan-world-cpu", "vulkan-world-gpu"):
        trace = appearance.load(root / (stem + "-smoke.log"), 1)
        assert trace["reference"] is not None, "Late census missing"
        assert trace["view_locked_since"] < trace["reference"], "Census projection did not settle"
        assert trace["draws"], "No real world consumption"
        assert not any(draw["generation"] == -(1 << 63) for draw in trace["draws"]), "Unknown geometry owner"
        producers = Counter(draw["producer"] for draw in trace["draws"])
        paths = Counter(draw["path"] for draw in trace["draws"])
        assert any(draw["key"][3] == 3 and draw["producer"] == "cpu"
                   and draw["path"] in ("direct", "legacy_indirect") for draw in trace["draws"])
        budgets = sorted(trace["page_budget_events"])
        assert budgets and budgets[0][1] == "entry"
        assert all(event[3] == 32 * 1024 * 1024 for event in budgets), "Changed global input quota"
        counts = Counter(event[1] for event in budgets)
        if stem.endswith("-gpu"):
            assert producers["gpu"] and producers["gpu_append"] and producers["cpu_append"], "GPU/APPEND consumption missing"
            assert counts["allocated"] > 0 and counts["retired"] > 0
            assert budgets[-1][2] == 0, "Quiet initial scene retained charged input pages"
            text = (root / (stem + "-smoke.log")).read_text()
            adoption = [dict(re.findall(r"(\w+)=([^ ;]+)", line)) for line in text.splitlines()
                        if "VULKANMOD_GPU_LOADED_WORLD_ADOPTION:" in line and "phase=initial" in line]
            assert adoption and any(int(item["ordinaryCpuIndices"]) == 0
                                    and int(item["mixedCpuIndices"]) > 0
                                    and int(item["gpuOwners"]) > 0 for item in adoption), "CPU displacement witness missing"
        observations[stem] = {
            "main_epoch": 1, "geometry_observations": len(trace["draws"]),
            "producers": dict(producers), "paths": dict(paths),
            "view_lock_seconds_since_entry": (trace["view_locked_since"] - trace["origin"]) / 1e9,
            "census_seconds_since_entry": (trace["reference"] - trace["origin"]) / 1e9,
            "input_pages": {"entry_bytes": budgets[0][2],
                            "peak_charge_observed_bytes": max(event[2] for event in budgets),
                            "last_retained_charge_bytes": budgets[-1][2],
                            "events": dict(counts)}}
    return {"original_exact_pixel_pairs": 18, "vulkan_validation_sync_errors": 0,
            "native_one_page_turnover_bytes_compute": "passed",
            "world_observations": observations,
            "limits": ["Small D2 software correctness controls; not RX/D32 speed qualification",
                       "CPU/GPU controls change19 features; not a causal repair performance comparison",
                       "Recording/fence witnesses are not first usable pixels/presentation",
                       "True all-required-input availability and matched owner appearance remain unknown",
                       "Portal child-view geometry is not credited to main-world coverage",
                       "Charges are global input buffers, not output geometry or driver heap",
                       "No new visually accepted reference or coverage percentage is asserted by this audit"]}


def main():
    directory, commit, number = Path(sys.argv[1]), sys.argv[2], sys.argv[3]
    assert re.fullmatch(r"[0-9a-f]{40}", commit) and number.isdigit()
    jar = unique(directory.rglob("*-all.jar"), "published runtime JAR")
    log = unique(directory.rglob("vulkan-world-vanilla-cpu-smoke.log"), "main CPU world log")
    result = {"executable": inspect_jar(jar, commit, number), "evidence": inspect_logs(log.parent)}
    output = ROOT / ".ci-results/terrain-artifact-audit.json"
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, indent=2) + "\n")
    print("VULKANMOD_PUBLISHED_TERRAIN_ARTIFACT_AUDIT_OK")
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
