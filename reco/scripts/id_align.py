#!/usr/bin/env python3
"""Recover the real names of R8-shortened *library* view IDs (and library layout names).

The app's res tree (from apktool) contains copies of library layouts whose file names and
@id names were shortened. Library code looks views up by the REAL id (R.id.parentPanel), so the
copies must use real names again. Each app layout is matched to an AAR layout with the identical
tag sequence; IDs are then paired by order of appearance. Votes are pooled over all layouts.
Cross-check: aapt2 assigns IDs alphabetically by original name, so real names of matched IDs
must be monotonic in the original numeric ID order (reported, conflicting pairs dropped).

Output: id_map.tsv (obf \t real), layout_map.tsv (obf layout \t real layout)
"""
import re, os, glob, collections, sys
from xml.etree import ElementTree as ET

APP = '/home/urias/Documents/adbremote-reconstructed/app/src/main/res'
ORIG_PUB = 'original/decompiled/apktool/res/values/public.xml'
MERGER = '/home/urias/Documents/adbremote-reconstructed/app/build/intermediates/incremental/release/mergeReleaseResources/merger.xml'
OUT = 'reco/data'
A = '{http://schemas.android.com/apk/res/android}'

def shape(path):
    try:
        root = ET.parse(path).getroot()
    except Exception:
        return None
    tags, ids = [], []
    for el in root.iter():
        tags.append(el.tag if el.tag != 'view' else 'view:' + (el.get('class') or ''))
        i = el.get(A + 'id')
        if i and i.startswith('@'):
            ids.append(i.split('/', 1)[1])
        else:
            ids.append(None)
    return tuple(tags), ids

lib = collections.defaultdict(list)       # tag-seq -> [(layoutname, ids)]
m = open(MERGER, encoding='utf-8').read()
for sp in sorted(set(re.findall(r'<source path="([^"]+)"', m))):
    if '/.gradle/caches/' not in sp:
        continue
    for p in glob.glob(os.path.join(sp, 'layout*', '*.xml')):
        s = shape(p)
        if s:
            lib[s[0]].append((os.path.basename(p)[:-4], s[1]))

votes = collections.defaultdict(collections.Counter)
lay = {}
for p in glob.glob(os.path.join(APP, 'layout*', '*.xml')):
    s = shape(p)
    if not s or s[0] not in lib:
        continue
    cands = lib[s[0]]
    names = {c[0] for c in cands}
    name = os.path.basename(p)[:-4]
    if len(names) == 1:
        lay[name] = cands[0][0]
    for _, lids in cands:
        w = 1.0 / len(cands)
        for o, r in zip(s[1], lids):
            if o and r and o != r:
                votes[o][r] += w

idmap = {o: c.most_common(1)[0][0] for o, c in votes.items()}

# monotonic cross-check against original ID order
pub = open(ORIG_PUB, encoding='utf-8').read()
order = {n: int(i, 16) for n, i in re.findall(r'<public type="id" name="([^"]+)" id="(0x[0-9a-f]+)"', pub)}
pairs = sorted((order[o], o, r) for o, r in idmap.items() if o in order)
# longest increasing subsequence on real names (byte order)
import bisect
keys = [r.encode() for _, _, r in pairs]
tails, tidx, prev = [], [], [-1] * len(keys)
for i, k in enumerate(keys):
    j = bisect.bisect_left(tails, k)
    if j == len(tails): tails.append(k); tidx.append(i)
    else: tails[j] = k; tidx[j] = i
    prev[i] = tidx[j - 1] if j else -1
keep, i = set(), tidx[-1] if tidx else -1
while i >= 0:
    keep.add(i); i = prev[i]
dropped = [(pairs[i][1], pairs[i][2]) for i in range(len(pairs)) if i not in keep]
final = {pairs[i][1]: pairs[i][2] for i in keep}
print(f'lib layouts matched {len(lay)}; id votes {len(idmap)}; monotonic kept {len(final)}; dropped {dropped}', file=sys.stderr)
with open(os.path.join(OUT, 'id_map.tsv'), 'w') as f:
    for o, r in sorted(final.items()): f.write(f'{o}\t{r}\n')
with open(os.path.join(OUT, 'layout_map.tsv'), 'w') as f:
    for o, r in sorted(lay.items()): f.write(f'{o}\t{r}\n')
