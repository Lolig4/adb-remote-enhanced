#!/usr/bin/env python3
"""Find/fix Kotlin string literals that were copied from the double-escaped jadx-mapped tree.
A literal is double-escaped if its Kotlin value V is not a decrypted original string, but
tsv_unescape(V) is. Usage: fix_double_escape.py [--fix]"""
import os, re, sys
DEC = 'original/alldec.tsv'
K = '/home/urias/Documents/adbremote-reconstructed/app/src/main/kotlin'
def tsv_unescape(v):
    out=[]; i=0
    while i < len(v):
        c=v[i]
        if c=='\\' and i+1<len(v):
            n=v[i+1]; out.append({'\\':'\\','n':'\n','t':'\t','r':'\r'}.get(n,'\\'+n)); i+=2
        else: out.append(c); i+=1
    return ''.join(out)
orig=set()
for l in open(DEC,encoding='utf-8',newline='\n'):
    l=l.rstrip('\n'); t=l.find('\t')
    if t>0: orig.add(tsv_unescape(l[t+1:]))
def k_unescape(s):  # Kotlin string-literal body -> value (no templates)
    out=[]; i=0
    while i<len(s):
        c=s[i]
        if c=='\\' and i+1<len(s):
            n=s[i+1]
            if n=='u': out.append(chr(int(s[i+2:i+6],16))); i+=6; continue
            out.append({'n':'\n','t':'\t','r':'\r','\\':'\\','"':'"',"'":"'",'$':'$','b':'\b'}.get(n,n)); i+=2
        else: out.append(c); i+=1
    return ''.join(out)
def k_escape(v):
    return v.replace('\\','\\\\').replace('"','\\"').replace('\n','\\n').replace('\t','\\t').replace('\r','\\r').replace('$','\\$')
lit=re.compile(r'(?<!")"((?:[^"\\\n]|\\.)*)"(?!")')
fix='--fix' in sys.argv; hits=0
for d,_,fs in os.walk(K):
    for f in fs:
        if not f.endswith('.kt'): continue
        p=os.path.join(d,f); s=open(p,encoding='utf-8').read()
        s2=s.replace('"""','\x00\x00\x00')  # skip raw strings crudely
        def rep(m):
            global hits
            body=m.group(1)
            if '\\' not in body or '${' in body: return m.group(0)
            v=k_unescape(body)
            if v in orig: return m.group(0)
            w=tsv_unescape(v)
            if w!=v and w in orig:
                hits+=1; print(f'{os.path.relpath(p,K)}: "{body}"  ->  "{k_escape(w)}"')
                return '"'+k_escape(w)+'"'
            return m.group(0)
        out=lit.sub(rep,s2).replace('\x00\x00\x00','"""')
        if fix and out!=s: open(p,'w',encoding='utf-8').write(out)
print('double-escaped literals:',hits,'(fixed)' if fix else '(dry run)')
