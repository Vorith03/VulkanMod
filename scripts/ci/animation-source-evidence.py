#!/usr/bin/env python3
"""Record exact Forge-generated source when available; transformed bytecode is also archived."""
import hashlib
import os
from pathlib import Path
import zipfile

cache = Path(os.environ.get('GRADLE_USER_HOME', str(Path.home()/'.gradle')))/'caches/forge_gradle'
entry = 'net/minecraft/client/renderer/texture/SpriteContents.java'
for path in sorted(cache.rglob('*sources*.jar')):
    try:
        with zipfile.ZipFile(path) as archive:
            if entry not in archive.namelist():
                continue
            raw = archive.read(entry)
    except (OSError, zipfile.BadZipFile):
        continue
    print(f'Pinned animation source evidence: {path.name}; SHA256 {hashlib.sha256(raw).hexdigest()}')
    source = raw.decode('utf-8')
    # The file is small and records frame resolution, exact ticker/interpolation,
    # Forge mip guards and mod-visible source ownership together.
    print(source)
    break
else:
    print('Generated animation source archive unavailable; inspect transformed SpriteContents/Ticker/InterpolationData bytecode in smoke artifacts.')
