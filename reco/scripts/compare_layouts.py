#!/usr/bin/env python3
"""Compare every compiled layout of the original APK with the rebuilt APK (aapt2 xmltree).

References are printed by name (@type/name); library attr/id names that R8 shortened in the
original are translated with attr_map.tsv / id_map.tsv, so only real differences remain.
Usage: compare_layouts.py <rebuilt.apk> [outdir]
"""
import re, os, sys, subprocess, difflib, concurrent.futures as cf
AAPT2 = os.path.expanduser('~/Android/Sdk/build-tools/36.0.0/aapt2')
IN = os.path.expanduser('original/input')
PORT = os.path.expanduser('reco/data')
ORIG = [os.path.join(IN, 'base.apk'), os.path.join(IN, 'split_config.xxhdpi.apk')]
NEW = sys.argv[1]
OUT = sys.argv[2] if len(sys.argv) > 2 else os.path.join(PORT, 'layout-diff')
amap = {l.split('\t')[0]: l.split('\t')[1] for l in open(os.path.join(PORT, 'attr_map.tsv'))}
imap = dict(l.rstrip('\n').split('\t') for l in open(os.path.join(PORT, 'id_map.tsv')))

def table(apks):
    names, layouts = {}, {}
    for apk in apks:
        d = subprocess.run([AAPT2, 'dump', 'resources', apk], capture_output=True, text=True).stdout
        cur = None
        for line in d.splitlines():
            m = re.match(r'\s+resource (0x[0-9a-f]+) (\S+)', line)
            if m: cur = m.group(2); names[int(m.group(1), 16)] = cur; continue
            m = re.match(r'\s+\((.*?)\) \(file\) (\S+) type=XML', line)
            if m and cur and cur.startswith('layout/'):
                layouts[(cur.split('/')[1], m.group(1))] = (apk, m.group(2))
    return names, layouts

def norm(apk, path, names, orig):
    t = subprocess.run([AAPT2, 'dump', 'xmltree', '--file', path, apk], capture_output=True, text=True).stdout
    out = []
    for line in t.splitlines():
        if re.match(r'\s*N: ', line): continue
        line = re.sub(r' \(line=\d+\)', '', line)
        line = re.sub(r'\(Raw: "[^"]*"\)', '', line)
        def ref(m):
            n = names.get(int(m.group(2), 16))
            if not n: return m.group(1) + '<missing ' + m.group(2) + '>'
            typ, nm = n.split('/', 1)
            if orig and typ == 'id': nm = imap.get(nm, nm)
            if orig and typ == 'attr': nm = amap.get(nm, nm)
            return m.group(1) + typ + '/' + nm
        line = re.sub(r'([@?])(0x7f[0-9a-f]{6})', ref, line)
        def attr(m):
            nm = m.group(2)
            if orig: nm = amap.get(nm, nm)
            return m.group(1) + nm
        line = re.sub(r'(A: http://schemas.android.com/apk/res-auto:)(\w+)\(0x[0-9a-f]+\)', attr, line)
        line = re.sub(r'(A: http://schemas.android.com/apk/res/android:\w+)\(0x[0-9a-f]+\)', r'\1', line)
        line = line.replace('http://schemas.android.com/apk/res/android:', 'android:').replace('http://schemas.android.com/apk/res-auto:', 'app:')
        out.append(line.rstrip())
    # attributes are unordered in practice: sort attr lines within each element
    res, buf = [], []
    for l in out:
        if l.lstrip().startswith('A: '): buf.append(l)
        else:
            res += sorted(buf); buf = []; res.append(l)
    return res + sorted(buf)

on, ol = table(ORIG)
nn, nl = table([NEW])
os.makedirs(OUT, exist_ok=True)
def one(key):
    if key not in nl: return key, 'MISSING', None
    a = norm(*ol[key], on, True); b = norm(*nl[key], nn, False)
    if a == b: return key, 'same', None
    return key, 'DIFF', '\n'.join(difflib.unified_diff(a, b, 'original', 'rebuilt', lineterm='', n=1))
# layouts the port gave descriptive names (dialog_connect, ...): pair them by content
renamed = {}
miss = [k for k in ol if k not in nl]
extra = [k for k in nl if k not in ol and not k[0].startswith(('abc_', 'mtrl_', 'material_', 'design_', 'm3_', 'notification_', 'select_dialog', 'support_', 'browser_', 'custom_dialog', 'ime_', 'preference', 'image_frame', 'expand_button', 'libraries_', 'license', 'admob', 'offline_', 'splash_', 'test_', 'text_view_', 'list_menu', 'item_', 'fragment_'))]
na = {k: norm(*ol[k], on, True) for k in miss}
nb = {k: norm(*nl[k], nn, False) for k in extra}
def skel(lines): return [l.split('=')[0] for l in lines]
for k in miss:
    best = max(extra, key=lambda e: difflib.SequenceMatcher(None, skel(na[k]), skel(nb[e])).ratio(), default=None)
    if best and difflib.SequenceMatcher(None, skel(na[k]), skel(nb[best])).ratio() > 0.8:
        renamed[k] = best; extra.remove(best)
def one(key):
    nk = renamed.get(key, key)
    if nk not in nl: return key, 'MISSING', None
    a = norm(*ol[key], on, True); b = norm(*nl[nk], nn, False)
    st = 'same' if a == b else 'DIFF'
    if nk != key: st += '(as ' + nk[0] + ')'
    if a == b: return key, st, None
    return key, st, '\n'.join(difflib.unified_diff(a, b, 'original', 'rebuilt', lineterm='', n=1))
with cf.ThreadPoolExecutor(16) as ex:
    res = sorted(ex.map(one, ol))
stat = {}
with open(os.path.join(OUT, 'summary.txt'), 'w') as s:
    for (name, cfg), st, d in res:
        stat[st.split('(')[0]] = stat.get(st.split('(')[0], 0) + 1
        tag = name + ('-' + cfg if cfg else '')
        s.write(f'{st}\t{tag}\n')
        if d: open(os.path.join(OUT, tag + '.diff'), 'w').write(d + '\n')
print(stat, '->', OUT)
