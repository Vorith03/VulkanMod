#!/usr/bin/env python3
"""Assert real transformed hooks preserve uncertain views before external cancellation."""
from pathlib import Path
import re
import sys

text = Path(sys.argv[1]).read_text()
methods = re.split(r'(?m)^  (?=\S.*\(.*\);$)', text)
for body in methods:
    if 'handler$' not in body:
        continue
    guard = re.search(r'invoke\w+.*handler\$[^\s:]*vulkanmod\$preserveEntityCullingView:', body)
    external = re.search(r'invoke\w+.*handler\$[^\s:]*\$renderEntity:', body)
    if guard and external:
        if guard.start() >= external.start():
            raise SystemExit('Portal/bounds guard runs after EntityCulling cancellation')
        print('EntityCulling transformed renderEntity hook order passed')
        break
else:
    raise SystemExit('Missing conservative guard or original EntityCulling renderEntity hook')
