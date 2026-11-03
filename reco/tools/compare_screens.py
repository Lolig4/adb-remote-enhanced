#!/usr/bin/env python3
"""compare_screens.py <origdir> <rebuiltdir> — per snapshot: hierarchy diff (text/desc/bounds;
class and resource-id ignored, they are obfuscated in the original) and pixel diff (status bar
cropped). Writes <rebuiltdir>/diff_<name>.png highlighting differing pixels."""
import sys, os, glob, difflib
from PIL import Image, ImageChops
A, B = sys.argv[1], sys.argv[2]
def rows(p):
    out = []
    for l in open(p):
        c, rid, t, d, b = l.rstrip('\n').split('\t')
        if t == "''" and d == "''": continue
        out.append(f'{t} {d} {b}')
    return out
for ta in sorted(glob.glob(A + '/*.txt')):
    n = os.path.basename(ta)[:-4]; tb = os.path.join(B, n + '.txt')
    if not os.path.exists(tb): print(f'{n:24} MISSING in rebuild'); continue
    ra, rb = rows(ta), rows(tb)
    hd = [l for l in difflib.unified_diff(ra, rb, lineterm='', n=0) if l[:1] in '+-' and l[:3] not in ('+++', '---')]
    ia = Image.open(os.path.join(A, n + '.png')).convert('RGB'); ib = Image.open(os.path.join(B, n + '.png')).convert('RGB')
    box = (0, 160, ia.width, ia.height); ia, ib = ia.crop(box), ib.crop(box)
    d = ImageChops.difference(ia, ib).convert('L').point(lambda v: 255 if v > 40 else 0)
    px = sum(1 for v in d.getdata() if v); pct = 100 * px / (ia.width * ia.height)
    if px:
        m = ib.copy(); m.paste((255, 0, 255), mask=d); m.save(os.path.join(B, f'diff_{n}.png'))
    print(f'{n:24} tree {"same" if not hd else str(len(hd)) + " lines"}   pixels {pct:6.2f}% {d.getbbox() or ""}')
    for l in hd[:6]: print('      ', l[:150])
