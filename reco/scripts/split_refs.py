#!/usr/bin/env python3
"""Restore references into the density split (split_config.xxhdpi.apk).

apktool decodes only the base APK, so every reference from base resources to a resource that lives
only in the split is written as "@null". This finds those references with aapt2 (raw @0x7f...
ids), resolves them against the split's resource table and patches the project's res tree.
"""
import re, os, subprocess, sys
AAPT2 = os.path.expanduser('~/Android/Sdk/build-tools/36.0.0/aapt2')
BASE = os.path.expanduser('original/input/base.apk')
SPLIT = os.path.expanduser('original/input/split_config.xxhdpi.apk')
RES = os.path.expanduser('~/Documents/adbremote-reconstructed/app/src/main/res')
amap = {l.split('\t')[0]: l.split('\t')[1] for l in open(os.path.expanduser('reco/data/attr_map.tsv'))}

def dump(apk):
    return subprocess.run([AAPT2, 'dump', 'resources', apk], capture_output=True, text=True).stdout
split = {}
for i, n in re.findall(r'resource (0x[0-9a-f]+) ([a-z]+/\S+)', dump(SPLIT)):
    split[int(i, 16)] = n
base = dump(BASE)
bnames = {int(i, 16): n for i, n in re.findall(r'resource (0x[0-9a-f]+) ([a-z]+/\S+)', base)}
fixed = 0

# 1) style items: "<attr>(0x7f04....)=@0x7f......" inside "resource ... style/NAME" blocks
cur, cfg = None, None
style_fixes = []
for line in base.splitlines():
    m = re.match(r'\s+resource 0x[0-9a-f]+ (\S+)', line)
    if m: cur = m.group(1); continue
    m = re.match(r'\s+\((.*?)\) \(style\)', line)
    if m: cfg = m.group(1); continue
    m = re.match(r'\s+(\S+?)(?:\((0x[0-9a-f]+)\))?=@(0x7f[0-9a-f]{6})$', line)
    if m and cur and cur.startswith('style/') and int(m.group(3), 16) in split:
        attr = m.group(1)
        if attr.startswith('0x'):
            attr = bnames.get(int(attr, 16), attr).split('/', 1)[-1]
        style_fixes.append((cfg, cur.split('/')[1], amap.get(attr, attr), '@' + split[int(m.group(3), 16)]))
for cfg, style, attr, val in style_fixes:
    d = 'values' + ('-' + cfg if cfg else '')
    p = os.path.join(RES, d, 'styles.xml')
    s = open(p).read()
    m = re.search(r'<style name="%s"[^>]*>.*?</style>' % re.escape(style), s, re.S)
    if not m: print('MISSING style', d, style); continue
    blk = m.group(0)
    nb, k = re.subn(r'(<item name="(?:android:)?%s">)@null(</item>)' % re.escape(attr), r'\g<1>%s\g<2>' % val, blk, count=1)
    if not k: print('NO @null item', d, style, attr, val); continue
    s = s.replace(blk, nb); open(p, 'w').write(s); fixed += 1

# 2) XML files
files = re.findall(r'resource 0x[0-9a-f]+ (\S+)\n((?:\s+\(.*?\) \(file\) \S+ type=XML\n)+)', base + '\n')
for name, block in files:
    typ, rname = name.split('/')
    for cfg, path in re.findall(r'\((.*?)\) \(file\) (\S+) type=XML', block):
        tree = subprocess.run([AAPT2, 'dump', 'xmltree', '--file', path, BASE], capture_output=True, text=True).stdout
        if not re.search(r'=@0x7f', tree): continue
        refs, el = [], -1
        for line in tree.splitlines():
            if re.match(r'\s*E: ', line): el += 1
            m = re.match(r'\s*A: (?:http://schemas.android.com/apk/res/android:)?(\S+?)\(0x[0-9a-f]+\)=@(0x7f[0-9a-f]{6})', line)
            if not m:
                m = re.match(r'\s*A: (?:http://schemas.android.com/apk/res-auto:)?(\S+?)\(0x[0-9a-f]+\)=@(0x7f[0-9a-f]{6})', line)
            if m and int(m.group(2), 16) in split:
                refs.append((el, m.group(1), '@' + split[int(m.group(2), 16)]))
        if not refs: continue
        d = typ + ('-' + cfg if cfg else '')
        cands = [os.path.join(RES, d, rname + '.xml')]
        p = next((c for c in cands if os.path.exists(c)), None)
        if not p: print('MISSING file', d, rname); continue
        s = open(p).read()
        starts = [m.start() for m in re.finditer(r'<(?![?!/])', s)]
        for el, attr, val in sorted(refs, key=lambda r: -r[0]):
            a = amap.get(attr, attr)
            st = starts[el]; en = s.index('>', st)
            tag = s[st:en]
            nt, k = re.subn(r'((?:android|app):%s=")@null(")' % re.escape(a), r'\g<1>%s\g<2>' % val, tag, count=1)
            if not k: print('NO @null attr', p, el, a, val); continue
            s = s[:st] + nt + s[en:]; fixed += 1
        open(p, 'w').write(s)
print('fixed', fixed)
