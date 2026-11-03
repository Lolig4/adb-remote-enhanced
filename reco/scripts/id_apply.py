#!/usr/bin/env python3
"""Rewrite R8-shortened library view-ID names back to their real names (id_map.tsv from id_align.py)
in all res XML and R.id references in the Kotlin sources."""
import re, glob
RES = '/home/urias/Documents/adbremote-reconstructed/app/src/main/res'
KT = '/home/urias/Documents/adbremote-reconstructed/app/src/main/kotlin'
m = dict(l.rstrip('\n').split('\t') for l in open('reco/data/id_map.tsv'))
n = {'xml': 0, 'kt': 0}
for p in glob.glob(RES + '/**/*.xml', recursive=True):
    s = open(p, encoding='utf-8').read()
    s2 = re.sub(r'(@\+?id/)([A-Za-z0-9_]+)\b', lambda x: x.group(1) + m.get(x.group(2), x.group(2)), s)
    s2 = re.sub(r'(type="id" name=")([^"]+)"', lambda x: x.group(1) + m.get(x.group(2), x.group(2)) + '"', s2)
    if s2 != s: open(p, 'w', encoding='utf-8').write(s2); n['xml'] += 1
for p in glob.glob(KT + '/**/*.kt', recursive=True):
    s = open(p, encoding='utf-8').read()
    s2 = re.sub(r'\bR\.id\.([A-Za-z0-9_]+)\b', lambda x: 'R.id.' + m.get(x.group(1), x.group(1)), s)
    if s2 != s: open(p, 'w', encoding='utf-8').write(s2); n['kt'] += 1
print(n)
