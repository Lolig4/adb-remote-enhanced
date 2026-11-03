import re,sys
src=open(sys.argv[1]).read()
pairs=re.findall(r'm2839\("((?:[^"\\]|\\.)*)", "((?:[^"\\]|\\.)*)"\)', src)
def k(s): return s.replace('$','\\$')
out=[]
for a,b in pairs:
    out.append('        "%s" to "%s",' % (k(a),k(b)))
print(len(pairs), file=sys.stderr)
open(sys.argv[2],'w').write("\n".join(out)+"\n")
