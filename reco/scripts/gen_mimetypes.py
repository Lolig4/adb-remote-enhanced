import re,sys
src=open(sys.argv[1]).read()
names={'f3147':'APK','f3136':'ARCHIVE','f3134':'AUDIO','f3145':'CALENDAR','f3151':'CERTIFICATE','f3138':'CODE','f3148':'CONTACT','f3149':'DIRECTORY','f3152':'SYMBOLIC_LINK','f3129':'DOCUMENT','f3150':'EBOOK','f3140':'EMAIL','f3137':'FONT','f3132':'GENERIC','f3143':'IMAGE','f3142':'PDF','f3146':'TEXT','f3131':'EXCEL','f3139':'POWERPOINT','f3141':'PRESENTATION','f3135':'SPREADSHEET','f3133':'VIDEO','f3144':'WORD'}
var={}   # local var -> enum
strs={}
for m in re.finditer(r'EnumC0200 (\w+) = EnumC0200\.(f\d+);',src): var[m.group(1)]=names[m.group(2)]
for m in re.finditer(r'String (\w+) = "([^"]*)";',src): strs[m.group(1)]=m.group(2)
pairs={}
order=[]
for m in re.finditer(r'(?:Pair (\w+) = )?new Pair\(("[^"]*"|\w+), (EnumC0200\.f\d+|\w+)\)',src):
    k=m.group(2); v=m.group(3)
    k=k.strip('"') if k.startswith('"') else strs[k]
    v=names[v.split('.')[1]] if v.startswith('EnumC0200') else var[v]
    order.append((k,v))
# split into three maps: f4398 up to powerpoint template; then general; then suffix
i_general=[i for i,(k,v) in enumerate(order) if k=='audio'][0]
i_suffix=[i for i,(k,v) in enumerate(order) if k=='json'][0]
full=order[:i_general]; gen=order[i_general:i_suffix]; suf=order[i_suffix:]
def fmt(lst): return "\n".join('        "%s" to MimeTypeIcon.%s,'%(k,v) for k,v in lst)
print(len(full),len(gen),len(suf),file=sys.stderr)
open(sys.argv[2],'w').write(f"""package com.jcapps.adbremote.utils

import com.jcapps.adbremote.provider.common.MimeTypeIcon

/**
 * Static MIME-type -> icon-category lookup tables used by the file list (MaterialFiles
 * MimeTypeIcon tables). The lookup itself (directory / symlink / exact / general type / suffix /
 * generic fallback) is inlined in FilesAdapter.
 */
object MimeTypes {{
    // obf: AbstractC0633

    // obf: f4398 — full MIME string -> icon
    val iconByMimeType: Map<String, MimeTypeIcon> = mapOf(
{fmt(full)}
    )

    // obf: f4397 — general type (audio/font/image/text/video) -> icon
    val iconByGeneralType: Map<String, MimeTypeIcon> = mapOf(
{fmt(gen)}
    )

    // obf: f4399 — structured-syntax suffix (json/xml/zip) -> icon
    val iconBySuffix: Map<String, MimeTypeIcon> = mapOf(
{fmt(suf)}
    )
}}
""")
