#!/usr/bin/env python3
"""Compare actual NativeImage readback words; no image library or PNG decoder."""
from pathlib import Path
import struct
import sys

root = Path(sys.argv[1])
for phase in ('initial', 'dirty', 'recovery', 'readopt', 'camera', 'return', 'reload', 'reentry'):
    cpu = (root / 'cpu' / f'{phase}.rgba').read_bytes()
    gpu = (root / 'gpu' / f'{phase}.rgba').read_bytes()
    width, height = struct.unpack('>II', cpu[:8])
    assert width > 0 and height > 0 and len(cpu) == 8 + width * height * 4
    assert gpu[:8] == cpu[:8] and len(gpu) == len(cpu), f'{phase}: framebuffer extent differs'
    differing = sum(cpu[i:i+4] != gpu[i:i+4] for i in range(8, len(cpu), 4))
    assert differing == 0, f'{phase}: {differing}/{width*height} CPU/GPU pixels differ; retain both PNGs'
    assert len(set(struct.unpack(f'>{width*height}I', cpu[8:]))) > 32, f'{phase}: empty/unvaried scene'
    print(f'VULKANMOD_GPU_LOADED_WORLD_PIXELS_OK: {phase} {width}x{height} exact CPU/GPU words')
