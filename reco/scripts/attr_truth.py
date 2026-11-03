#!/usr/bin/env python3
"""Ground truth for attr names: compiled XML files keep the attribute NAME strings in their string
pool, while R8 only shortened the resource-table entries. Collect (obf table name -> pool name)
from every XML file of the original base APK and compare with attr_map.tsv."""
import re, os, subprocess, collections, concurrent.futures as cf
AAPT2 = os.path.expanduser('~/Android/Sdk/build-tools/36.0.0/aapt2')
BASE = os.path.expanduser('original/input/base.apk')
PORT = os.path.expanduser('reco/data')
d = subprocess.run([AAPT2, 'dump', 'resources', BASE], capture_output=True, text=True).stdout
names = {int(i, 16): n for i, n in re.findall(r'resource (0x[0-9a-f]+) (\S+)', d)}
files = re.findall(r'\(file\) (\S+) type=XML', d)
def one(f):
    t = subprocess.run([AAPT2, 'dump', 'xmltree', '--file', f, BASE], capture_output=True, text=True).stdout
    return re.findall(r'A: http://schemas.android.com/apk/res-auto:(\w+)\((0x7f[0-9a-f]{6})\)', t)
truth = collections.defaultdict(set)
with cf.ThreadPoolExecutor(16) as ex:
    for pairs in ex.map(one, files):
        for real, i in pairs:
            truth[names.get(int(i, 16), i).split('/')[-1]].add(real)
amap = {l.split('\t')[0]: l.split('\t')[1] for l in open(os.path.join(PORT, 'attr_map.tsv'))}
ok = bad = new = 0
with open(os.path.join(PORT, 'attr_truth.tsv'), 'w') as f:
    for o, rs in sorted(truth.items()):
        r = sorted(rs)[0]
        f.write(f'{o}\t{r}\n')
        if o not in amap: new += 1; print('NOT IN MAP', o, '->', r)
        elif amap[o] != r: bad += 1; print('WRONG', o, amap[o], '-> truth', r)
        else: ok += 1
print(f'truth {len(truth)}: ok {ok}, wrong {bad}, not-in-map {new}')
