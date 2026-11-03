#!/usr/bin/env python3
"""Rewrite R8-shortened library attribute names back to their real names in the app's res tree
(and R.attr references in the Kotlin sources), using attr_map.tsv from attr_align.py."""
import os, re, json, glob

RES = '/home/urias/Documents/adbremote-reconstructed/app/src/main/res'
KT = '/home/urias/Documents/adbremote-reconstructed/app/src/main/kotlin'
amap, emap = {}, {}
for l in open('reco/data/attr_map.tsv', encoding='utf-8'):
    o, r, _sig, em = l.rstrip('\n').split('\t')
    amap[o] = r
    emap[r] = json.loads(em)          # obf enum/flag name -> real name, keyed by REAL attr name

def map_value(attr_real, val):
    em = emap.get(attr_real)
    if not em:
        return val
    parts = val.split('|')
    if all(p in em for p in parts):
        return '|'.join(em[p] for p in parts)
    return val

tok = r'[A-Za-z0-9_]+'
stats = dict(files=0, attrs=0, items=0, refs=0, enums=0)

def fix_refs(s):
    def r(m):
        name = m.group(3)
        if name in amap:
            stats['refs'] += 1
            return m.group(1) + m.group(2) + amap[name]
        return m.group(0)
    # ?x  ?attr/x  ?app:attr/x  @attr/x   (token must end the value)
    return re.sub(r'([?@])((?:app:)?(?:attr/)?)(' + tok + r')(?=["<\s|])', r, s)

for path in glob.glob(RES + '/**/*.xml', recursive=True):
    s = open(path, encoding='utf-8').read(); o = s
    base = os.path.basename(os.path.dirname(path))
    if base.startswith('values'):
        if os.path.basename(path) == 'attrs.xml':
            # drop definitions the libraries provide now
            s = re.sub(r'\s*<attr name="(' + tok + r')"[^>]*/>', lambda m: '' if m.group(1) in amap else m.group(0), s)
            s = re.sub(r'\s*<attr name="(' + tok + r')"[^>]*>.*?</attr>', lambda m: '' if m.group(1) in amap else m.group(0), s, flags=re.S)
        else:
            def item(m):
                name, val = m.group(1), m.group(2)
                if name in amap:
                    stats['items'] += 1
                    real = amap[name]; nv = map_value(real, val)
                    if nv != val: stats['enums'] += 1
                    return f'<item name="{real}">{nv}</item>'
                return m.group(0)
            s = re.sub(r'<item name="(' + tok + r')">([^<]*)</item>', item, s)
    else:
        def attr(m):
            name, val = m.group(1), m.group(2)
            if name in amap:
                stats['attrs'] += 1
                real = amap[name]; nv = map_value(real, val)
                if nv != val: stats['enums'] += 1
                return f'app:{real}="{nv}"'
            return m.group(0)
        s = re.sub(r'\bapp:(' + tok + r')="([^"]*)"', attr, s)
    s = fix_refs(s)
    if s != o:
        open(path, 'w', encoding='utf-8').write(s); stats['files'] += 1

# Kotlin: R.attr.OBF -> R.attr.REAL (app R includes library attrs with nonTransitiveRClass=false)
kt = 0
for path in glob.glob(KT + '/**/*.kt', recursive=True):
    s = open(path, encoding='utf-8').read()
    s2 = re.sub(r'\bR\.attr\.(' + tok + r')\b', lambda m: 'R.attr.' + amap.get(m.group(1), m.group(1)), s)
    if s2 != s: open(path, 'w', encoding='utf-8').write(s2); kt += 1
print(stats, 'kotlin files', kt)
