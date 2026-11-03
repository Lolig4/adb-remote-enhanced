import sys; sys.path.append("/home/urias/.local/lib/python3.14/site-packages"); sys.path.append("/usr/lib64/python3.14/site-packages"); sys.path.append("/usr/lib/python3.14/site-packages")
import sys, struct
from elftools.elf.elffile import ELFFile
from unicorn import *
from unicorn.arm64_const import *

SO='original/compare/so/lib/arm64-v8a/libapp.so'
BASE=0x40000000
def al(x,a=0x1000): return (x+a-1)&~(a-1)

e=ELFFile(open(SO,'rb'))
dyn=e.get_section_by_name('.dynsym'); syms=list(dyn.iter_symbols())
defd={}; undef={}
for i,s in enumerate(syms):
    if s['st_shndx']=='SHN_UNDEF':
        if s.name: undef[i]=s.name
    elif s.name: defd.setdefault(s.name,s['st_value'])
java=sorted([(s.name,s['st_value']) for s in syms if s.name.startswith('Java_com_jcapps')],key=lambda x:x[1])

uc=Uc(UC_ARCH_ARM64,UC_MODE_ARM)
# map image
loads=[seg for seg in e.iter_segments() if seg['p_type']=='PT_LOAD']
lo=min(s['p_vaddr'] for s in loads); hi=max(s['p_vaddr']+s['p_memsz'] for s in loads)
mapsz=al(hi-lo+0x1000)
uc.mem_map(BASE, mapsz)
f=open(SO,'rb')
for s in loads:
    f.seek(s['p_offset']); data=f.read(s['p_filesz'])
    uc.mem_write(BASE+s['p_vaddr'], data)

# regions: trap page for imports, tls, jni env, stack, heap
TRAP=0x10000000; uc.mem_map(TRAP,0x4000)
TLS =0x20000000; uc.mem_map(TLS,0x10000)
ENV =0x30000000; uc.mem_map(ENV,0x10000)
STK =0x50000000; uc.mem_map(STK,0x100000)
HEAP=0x60000000; uc.mem_map(HEAP,0x400000); heap_ptr=[HEAP]
def halloc(n):
    p=heap_ptr[0]; heap_ptr[0]=al(p+n,16); return p

# import sentinels
sent2name={}; name2sent={}
nexts=[TRAP+8]
def sent_for(name):
    if name not in name2sent:
        a=nexts[0]; nexts[0]+=8; name2sent[name]=a; sent2name[a]=name
    return name2sent[name]
END=TRAP+0x3000  # return sentinel
NEWSTRUTF=sent_for('__NewStringUTF__')

def rd64(a): return struct.unpack('<Q',uc.mem_read(a,8))[0]
def wr64(a,v): uc.mem_write(a,struct.pack('<Q',v&((1<<64)-1)))

# relocations
for sec in e.iter_sections():
    if sec.name not in ('.rela.dyn','.rela.plt'): continue
    for r in sec.iter_relocations():
        t=r['r_info_type']; off=BASE+r['r_offset']; add=r['r_addend']; si=r['r_info_sym']
        if t==1027:  # RELATIVE
            wr64(off, BASE+add)
        elif t in (257,1025,1026):  # ABS64/GLOB_DAT/JUMP_SLOT
            nm=undef.get(si)
            if nm is None:
                s=syms[si]; wr64(off, BASE+s['st_value']+add)
            else:
                wr64(off, sent_for(nm)+add)

# JNIEnv: env ptr at ENV -> vtable at ENV+0x1000
ENVPTR=ENV; VT=ENV+0x1000
wr64(ENVPTR, VT)
wr64(VT+0x538, NEWSTRUTF)          # NewStringUTF (167)
for idx in range(0,235):           # fill vtable with per-index sentinels
    o=idx*8
    if idx!=167: wr64(VT+o, sent_for('JNI_%d'%idx))


import os
OBJS={0x72000000:('ctx',)}
EXC=int(os.environ.get('EXC','0'))
PT=bytes.fromhex('a8506157dcde888ae7ee8462b9e61d7c6db2bc1e2fafc97157dc370eccd9b80a')
NEXT=[0x73000000]
def newobj(v):
    a=NEXT[0]; NEXT[0]+=0x10; OBJS[a]=v; return a
def cstr(a):
    b=bytes(uc.mem_read(a,400)).split(b'\0')[0]; return b.decode('latin1')

import base64 as _b64
from cryptography.hazmat.primitives.ciphers import Cipher as _C, algorithms as _alg, modes as _modes
from cryptography.hazmat.primitives import padding as _pad
EXCF=[0]
TOKEN=os.environ.get('TOKEN','TESTTOKEN')
def real(idx,args):
    o=OBJS.get
    if idx==176: return newobj(['bytes',bytearray(args[1])])
    if idx==208:
        x4=uc.reg_read(UC_ARM64_REG_X4); a=o(args[1]); st=args[2]; n=args[3]
        a[1][st:st+n]=bytes(uc.mem_read(x4,n)); return 0
    if idx==171: return len(o(args[1])[1])
    if idx in (184,):
        b=bytes(o(args[1])[1]); pp=halloc(len(b)+16); uc.mem_write(pp,b+b'\0'); return pp
    if idx==192: return 0
    if idx==228: v=EXCF[0]; return v
    if idx==17: EXCF[0]=0; return 0
    if idx==15: return newobj(('exc',)) if EXCF[0] else 0
    if idx==168:
        return len(o(args[1])[1].encode())
    if idx==164: return len(o(args[1])[1])
    return None
def call(idx,tgt,mid,vs):
    nm=mid[1]
    if nm=='getName': return newobj(('str',tgt[1].replace('/','.')))
    if nm=='decode': return newobj(['bytes',bytearray(_b64.b64decode(OBJS[vs[0]][1]))])
    if nm=='getInstance': return newobj(['cipher',OBJS[vs[0]][1],None,None])
    if nm=='init': tgt[2]=vs[0]; tgt[3]=bytes(OBJS[vs[1]][1]); print('  cipher.init mode',vs[0],'key',tgt[3]); return 0
    if nm=='doFinal':
        data=bytes(OBJS[vs[0]][1]); c=_C(_alg.AES(tgt[3]),_modes.ECB())
        try:
            if tgt[2]==1:
                pd=_pad.PKCS7(128).padder(); data=pd.update(data)+pd.finalize(); e=c.encryptor(); r=e.update(data)+e.finalize()
            else:
                d=c.decryptor(); r=d.update(data)+d.finalize(); print('  raw decrypted',r)
                u=_pad.PKCS7(128).unpadder(); r=u.update(r)+u.finalize()
        except Exception as ex:
            print('  doFinal EXC',ex); EXCF[0]=1; return 0
        print('  doFinal ->',r); return newobj(['bytes',bytearray(r)])
    if nm=='randomUUID': return newobj(('uuid',))
    if nm=='toString' and tgt[0]=='uuid': return newobj(('str','11111111-2222-3333-4444-555555555555'))
    if nm=='trim': return newobj(('str',tgt[1].strip()))
    if nm=='getString': return newobj(('str',TOKEN)) if TOKEN!='NULL' else 0
    if nm=='getBytes': return newobj(['bytes',bytearray(tgt[1].encode())])
    if nm=='encodeToString':
        r=_b64.b64encode(bytes(OBJS[vs[0]][1])).decode(); fl=vs[1]
        if fl&2: pass
        else: r=r
        print('  encodeToString flags',fl,r); return newobj(('str',r if fl&2 else r+'\n'))
    return None

def va(vl,n):
    stack=rd64(vl); grtop=rd64(vl+8); groff=struct.unpack('<i',uc.mem_read(vl+24,4))[0]
    out=[]
    for i in range(n):
        if groff<0:
            out.append(rd64(grtop+groff)); groff+=8
        else:
            out.append(rd64(stack)); stack+=8
    return out
def jni(name,args,strs):
    idx0=None if name=='__NewStringUTF__' else int(name[4:])
    if idx0 in (29,34,35,36,37,38,39,40,41,42,43,44,45,46,47,48,49,50,51,52,53,54,55,56,57,58,59,60,61,62,63,64,115,118,121,124,127,130,133,136,139,142):
        try:
            vs=va(args[3],2); print('  VA',idx0,[ (hex(v), OBJS.get(v)) for v in vs])
        except Exception as ex: print('va err',ex)
    if name=='__NewStringUTF__':
        return newobj(('str',cstr(args[1])))
    idx=int(name[4:])
    if idx==29:
        vs=va(args[3],2); print('  NewObject',OBJS.get(args[1])); return newobj(['key',bytes(OBJS[vs[0]][1])])
    if idx in (61,62,63):
        vs=va(args[3],3); call(idx,OBJS.get(args[1]),OBJS.get(args[2]),vs); return 0
    rr=real(idx,args)
    if rr is not None: return rr
    o=lambda a: OBJS.get(a)
    if idx==6: return newobj(('class',cstr(args[1])))
    if idx==31:
        v=o(args[1]); 
        if v and v[0]=='class': return newobj(('class','java/lang/Class'))
        if v and v[0]=='ctx': return newobj(('class','com/jcapps/adbremote/expose/C0104'))
        return newobj(('class','?of'+repr(v)))
    if idx in (33,113,94,144): return newobj(('mid',cstr(args[2]),cstr(args[3]), o(args[1])))
    if idx in (23,21,22,17): return 0
    if idx==228 or idx==15: return EXC
    pass
    if idx==184:
        pp=halloc(len(PT)+16); uc.mem_write(pp,PT); return pp
    if idx in (192,):
        return 0
    if idx in (34,35,36,114,115,116):
        tgt=o(args[1]); mid=o(args[2])
        print('CALL',idx,tgt,mid)
        try: vs=va(args[3],3)
        except Exception: vs=[0,0,0]
        r=call(idx,tgt,mid,vs)
        if r is not None: return r
        return newobj(('ret',mid[1] if mid else None, tgt))
    if idx==169:
        v=o(args[1]); print('GetStringUTFChars',v)
        if v and v[0]=='str':
            b=v[1].encode()+b'\0'; pp=halloc(len(b)+16); uc.mem_write(pp,b); return pp
        b=b'<<'+repr(v).encode()+b'>>\0'; pp=halloc(len(b)+16); uc.mem_write(pp,b); return pp
    if idx==170: return 0
    if idx==208:
        x4=uc.reg_read(UC_ARM64_REG_X4); print('SETREGION buf',hex(x4), bytes(uc.mem_read(x4,args[3])))
    print('JNI unhandled',idx,[hex(a) for a in args],[o(a) for a in args[1:]])
    return newobj(('jni',idx))

emutls_cache={}
captured=[]
LOG=[]

def emulate_import(name):
    x0=uc.reg_read(UC_ARM64_REG_X0)
    if name=='__emutls_get_address':
        if x0 not in emutls_cache:
            size=rd64(x0); templ=rd64(x0+24)
            buf=halloc(max(size,1)+16)
            uc.mem_write(buf,b'\0'*(al(size,16)+16))
            if templ: uc.mem_write(buf, bytes(uc.mem_read(templ,size)))
            emutls_cache[x0]=buf
        uc.reg_write(UC_ARM64_REG_X0, emutls_cache[x0])
    elif name in ('memmove',):
        a0,a1,a2=[uc.reg_read(r) for r in (UC_ARM64_REG_X0,UC_ARM64_REG_X1,UC_ARM64_REG_X2)]
        if a2: uc.mem_write(a0, bytes(uc.mem_read(a1,a2)))
        uc.reg_write(UC_ARM64_REG_X0,a0)
    elif name in ('memset','strncpy','__strncpy_chk'):
        a0,a1,a2=[uc.reg_read(r) for r in (UC_ARM64_REG_X0,UC_ARM64_REG_X1,UC_ARM64_REG_X2)]
        if name=='memset': uc.mem_write(a0, bytes([a1&0xff])*a2)
        else:
            src=bytes(uc.mem_read(a1,a2)); n=src.find(b'\0'); n=a2 if n<0 else n
            uc.mem_write(a0, src[:n]+b'\0'*(a2-n))
        print('IMPORT',name,hex(a0),hex(a1),hex(a2))
    elif name=='memcpy' or name=='__memcpy_chk':
        d=x0; s=uc.reg_read(UC_ARM64_REG_X1); n=uc.reg_read(UC_ARM64_REG_X2)
        if n: uc.mem_write(d, bytes(uc.mem_read(s,n)))
        uc.reg_write(UC_ARM64_REG_X0,d)
    elif name in ('strlen','__strlen_chk'):
        p=x0; n=0
        while uc.mem_read(p+n,1)!=b'\0': n+=1
        uc.reg_write(UC_ARM64_REG_X0,n)
    if name.startswith('JNI_') or name=='__NewStringUTF__' or name not in ('memcpy','strlen','__strlen_chk','__memcpy_chk','__emutls_get_address','malloc','calloc'):
        args=[uc.reg_read(r) for r in (UC_ARM64_REG_X0,UC_ARM64_REG_X1,UC_ARM64_REG_X2,UC_ARM64_REG_X3)]
        strs=[]
        for a in args[1:]:
            if a>0x1000:
                try:
                    b=bytes(uc.mem_read(a,200)).split(b'\0')[0]
                    if len(b)>=2 and all(32<=x<127 for x in b): strs.append(b.decode())
                except UcError: pass
        LOG.append((name,[hex(a) for a in args],strs))
    if name=='__NewStringUTF__' or name.startswith('JNI_'):
        r=jni(name, args, strs)
        if r is not None:
            uc.reg_write(UC_ARM64_REG_X0, r); return
    if False: pass
    elif name in ('memmove','memset','strncpy','__strncpy_chk','memcpy','__memcpy_chk','strlen','__strlen_chk','__emutls_get_address'): pass
    elif name=='__NewStringUTF__' or name.startswith('JNI_'):
        for reg in (UC_ARM64_REG_X1,UC_ARM64_REG_X2):
            p=uc.reg_read(reg)
            if p<0x1000: continue
            b=bytearray()
            try:
                while len(b)<4096:
                    c=uc.mem_read(p+len(b),1)
                    if c==b'\0': break
                    b+=c
            except UcError: continue
            txt=bytes(b)
            if len(txt)>=2 and all(32<=x<127 for x in txt): captured.append(txt)
        # UTF-16LE at x1 with length x2
        p=uc.reg_read(UC_ARM64_REG_X1); n=uc.reg_read(UC_ARM64_REG_X2)
        if p>=0x1000 and 0<n<=4096:
            try:
                raw=bytes(uc.mem_read(p,n*2)); u=raw.decode('utf-16le','replace')
                if u and all(32<=ord(c)<127 for c in u): captured.append(u.encode())
            except UcError: pass
        uc.reg_write(UC_ARM64_REG_X0, 0x70000000+len(captured)+1)
    elif name in ('malloc','calloc'):
        n=uc.reg_read(UC_ARM64_REG_X0); print('MALLOC',hex(n), hex(uc.reg_read(UC_ARM64_REG_X30)))
        n=min(n,0x10000)
        if name=='calloc': n*=uc.reg_read(UC_ARM64_REG_X1)
        p=halloc(max(n,16)+16); uc.mem_write(p,b'\0'*(al(max(n,16),16)+16))
        uc.reg_write(UC_ARM64_REG_X0,p)
    else:
        uc.reg_write(UC_ARM64_REG_X0, 0x71000000)

TRACE=0
EMUTLS_ADDR=BASE+0x11028c
def do_emutls():
    x0=uc.reg_read(UC_ARM64_REG_X0)
    if x0 not in emutls_cache:
        size=rd64(x0); templ=rd64(x0+24)
        buf=halloc(max(size,1)+16); uc.mem_write(buf,b'\0'*(al(size,16)+16))
        if templ: uc.mem_write(buf, bytes(uc.mem_read(templ,size)))
        emutls_cache[x0]=buf
    uc.reg_write(UC_ARM64_REG_X0, emutls_cache[x0])
    uc.reg_write(UC_ARM64_REG_PC, uc.reg_read(UC_ARM64_REG_X30))
def hook_code(uc,addr,size,ud):
    if addr==EMUTLS_ADDR:
        do_emutls(); return
    if addr in sent2name:
        emulate_import(sent2name[addr])
        if TRACE: print('IMP',sent2name[addr],hex(uc.reg_read(UC_ARM64_REG_X0)),'lr',hex(uc.reg_read(UC_ARM64_REG_X30)-BASE))
        uc.reg_write(UC_ARM64_REG_PC, uc.reg_read(UC_ARM64_REG_X30))
uc.hook_add(UC_HOOK_CODE, hook_code, begin=TRAP, end=TRAP+0x4000)
uc.hook_add(UC_HOOK_CODE, hook_code, begin=EMUTLS_ADDR, end=EMUTLS_ADDR+4)

def hook_unmap(uc,typ,addr,size,val,ud):
    pg=addr & ~0xfff
    try: uc.mem_map(pg,0x2000)
    except UcError: pass
    return True
uc.hook_add(UC_HOOK_MEM_UNMAPPED, hook_unmap)

# tls: canary at tls+0x28
uc.reg_write(UC_ARM64_REG_TPIDR_EL0, TLS)
wr64(TLS+0x28, 0xCAFEBABEDEADBEEF)

# run .init_array constructors so guard globals / static state initialize
INIT=BASE+e.get_section_by_name(".init_array")["sh_addr"]; INITSZ=e.get_section_by_name(".init_array")["sh_size"]
for i in range(INITSZ//8):
    fn=rd64(INIT+i*8)
    if not fn: continue
    uc.reg_write(UC_ARM64_REG_SP,STK+0x80000)
    uc.reg_write(UC_ARM64_REG_X0,0); uc.reg_write(UC_ARM64_REG_X1,0)
    uc.reg_write(UC_ARM64_REG_X30,END)
    try: uc.emu_start(fn,END,count=5000000,timeout=5000000)
    except UcError as ex: print('init%d ERR %s @%x'%(i,ex,uc.reg_read(UC_ARM64_REG_PC)))
results={}
TRACE=1
for name,addr in java:
    if addr not in (0x8d730,0x8f558): continue
    LOG.clear()
    captured.clear()
    uc.reg_write(UC_ARM64_REG_X2, 0x72000000)
    uc.reg_write(UC_ARM64_REG_SP, STK+0x80000)
    uc.reg_write(UC_ARM64_REG_X0, ENVPTR)
    uc.reg_write(UC_ARM64_REG_X1, 0)
    uc.reg_write(UC_ARM64_REG_X30, END)
    try:
        uc.emu_start(BASE+addr, END, timeout=8*1000000, count=3000000)
    except UcError as ex:
        pc=uc.reg_read(UC_ARM64_REG_PC)
        results[name]=f"<ERR {ex} pc={pc:#x}>"
        continue
    rv=uc.reg_read(UC_ARM64_REG_X0); print('RETURN',hex(rv),OBJS.get(rv))
    short=name.split('C0104__')[-1]
    seen=[];[seen.append(x) for x in captured if x not in seen]
    # also dump decrypted emutls buffers (gated secrets live here even if not returned)
    bufs=[]
    for ctrl,buf in emutls_cache.items():
        sz=rd64(ctrl)
        if 1<sz<4096:
            raw=bytes(uc.mem_read(buf,sz))
            t=raw.split(b'\0')[0]
            if len(t)>=2 and all(32<=x<127 for x in t) and t not in seen: bufs.append(t)
    results[name]=(seen+[b'[buf]'+x for x in bufs]) if (seen or bufs) else '<none>'

for l in LOG: print(l)
print('SENT50', sent2name.get(0x10000050))
for name,addr in java:
    if name not in results: continue
    short=name.split('C0104__')[-1]
    v=results[name]
    print(f'{addr:#08x} {short:46} {v!r}')
