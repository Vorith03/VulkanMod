#!/usr/bin/env python3
"""Check the actual Forge-transformed world/post-effect/native-GUI boundary."""
from pathlib import Path
import re
import sys

text = Path(sys.argv[1]).read_text()
methods = re.split(r'(?m)^  (?=\S.*\(.*\);$)', text)
body = next((method for method in methods if method.startswith('public void render(float, long, boolean);')), None)
if body is None:
    raise SystemExit('Missing transformed GameRenderer.render(float,long,boolean)')
anchors = [r'invoke\w+.*vulkanmod\$scaleWorld:',
           r'invoke\w+.*PostChain\.process:',
           r'invoke\w+.*vulkanmod\$composeBeforeGui:',
           r'new\s+.*class net/minecraft/client/gui/GuiGraphics']
positions = []
for anchor in anchors:
    match = re.search(anchor, body)
    if match is None:
        raise SystemExit('Missing transformed world-scale anchor: ' + anchor)
    positions.append(match.start())
if positions != sorted(positions):
    raise SystemExit('World scale must enclose camera effects and compose before native GUI')
print('World scale transformed world/post-effect/native-GUI hook order passed')
