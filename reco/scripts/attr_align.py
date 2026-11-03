#!/usr/bin/env python3
"""Recover the real names of R8-shortened *library* attributes.

aapt2 assigns resource IDs per type in byte-wise alphabetical order of the (original) entry name.
R8/AGP resource shortening renamed the entries afterwards, so the original attr IDs are still in
real-name order. We align
  O = original attrs sorted by ID (apktool public.xml) with their format signature (apktool attrs.xml)
  L = real attrs of all dependency AARs sorted by name, with their format signature
by a weighted LCS on equal signatures (rare signatures weigh more). Unmatched O entries are the
app's own attributes; unmatched L entries were shrunk away or come from newer library versions.

Output: attr_map.tsv  (obf \t real \t signature \t enum-name map as json)
"""
import re, os, sys, json, glob, collections
from xml.etree import ElementTree as ET

ORIG = 'original/decompiled/apktool/res/values'
MERGER = '/home/urias/Documents/adbremote-reconstructed/app/build/intermediates/incremental/release/mergeReleaseResources/merger.xml'
OUT = 'reco/data/attr_map.tsv'

def sig_of(el):
    fmt = set((el.get('format') or '').split('|')) - {''}
    kids = [(c.tag, c.get('name'), c.get('value')) for c in el if c.tag in ('enum', 'flag')]
    def num(v):
        v = v.strip()
        return int(v, 16) if v.lower().startswith('0x') else int(v)
    if kids:
        kind = kids[0][0]
        fmt -= {'enum', 'flag', 'flags'}
        vals = tuple(sorted(num(v) & 0xffffffff for _, _, v in kids))
        return (tuple(sorted(fmt)), kind, vals), {num(v) & 0xffffffff: n for _, n, v in kids}
    return (tuple(sorted(fmt)), None, ()), {}

def attrs_in(path, into):
    try:
        root = ET.parse(path).getroot()
    except Exception:
        return
    def add(el):
        n = el.get('name')
        if not n or n.startswith('android:'):
            return
        s, e = sig_of(el)
        if s == ((), None, ()) and n in into:   # bare reference inside a styleable
            return
        if n not in into or into[n][0] == ((), None, ()):
            into[n] = (s, e)
    for el in root.iter('attr'):
        add(el)

# ---- O: original (obfuscated) attrs in ID order
pub = open(os.path.join(ORIG, 'public.xml'), encoding='utf-8').read()
O_ids = sorted((int(i, 16), n) for n, i in re.findall(r'<public type="attr" name="([^"]+)" id="(0x[0-9a-f]+)"', pub))
O_def = {}
attrs_in(os.path.join(ORIG, 'attrs.xml'), O_def)
O = [(n, O_def.get(n, (((), None, ()), {}))) for _, n in O_ids]

# ---- L: real library attrs
m = open(MERGER, encoding='utf-8').read()
srcs = set()
for sp in re.findall(r'<source path="([^"]+)"', m):
    if '/.gradle/caches/' in sp:      # dependency AAR res dirs (project sources live elsewhere)
        srcs.add(sp)
L_def = {}
for s in sorted(srcs):
    for p in glob.glob(os.path.join(s, 'values*', '*.xml')):
        attrs_in(p, L_def)
L = sorted(L_def.items(), key=lambda kv: kv[0].encode())
print(f'O={len(O)} L={len(L)} lib-sources={len(srcs)}', file=sys.stderr)

# ---- weighted LCS
cnt = collections.Counter(s for _, (s, _) in L)
def w(sig):
    base = 1.0 / cnt[sig]
    if sig[1]:                 # enum/flag signatures are near-unique anchors
        base += 3.0
    return base + 0.01
EMPTY = ((), None, ())
def eq(a, b):
    if a == b: return True
    # unknown format on one side (bare styleable reference) matches any non-enum/flag signature
    return (a == EMPTY and b[1] is None) or (b == EMPTY and a[1] is None)
def w2(a, b):
    return w(a) if a == b and a in cnt else 0.004
n, k = len(O), len(L)
score = [[0.0] * (k + 1) for _ in range(n + 1)]
for i in range(n - 1, -1, -1):
    oi = O[i][1][0]; row = score[i]; nxt = score[i + 1]
    for j in range(k - 1, -1, -1):
        best = nxt[j] if nxt[j] > row[j + 1] else row[j + 1]
        if eq(oi, L[j][1][0]):
            v = nxt[j + 1] + w2(oi, L[j][1][0])
            if v > best:
                best = v
        row[j] = best
i = j = 0; pairs = []
while i < n and j < k:
    oi = O[i][1][0]
    if eq(oi, L[j][1][0]) and abs(score[i][j] - (score[i + 1][j + 1] + w2(oi, L[j][1][0]))) < 1e-9:
        pairs.append((i, j)); i += 1; j += 1
    elif score[i + 1][j] >= score[i][j + 1]:
        i += 1
    else:
        j += 1

with open(OUT, 'w', encoding='utf-8') as f:
    for i, j in pairs:
        on, (osig, oenum) = O[i]; ln, (lsig, lenum) = L[j]
        emap = {oenum[v]: lenum[v] for v in oenum if v in lenum}
        f.write(f'{on}\t{ln}\t{osig}\t{json.dumps(emap)}\n')
print(f'matched {len(pairs)} of O={n}; unmatched O (app-own?) {n-len(pairs)}; unmatched L {k-len(pairs)}', file=sys.stderr)
