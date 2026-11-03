# libapp.so — recovered native values (ADB Remote 1.1.0)

`com.jcapps.adbremote.expose.C0104` declares 21 `native` methods (`m787`..`m807`) that
`libapp.so` implements. Every return value is a compile-time-obfuscated string (per-string
thread-local XOR; the "xorstr"/ADVobfuscator pattern). They were recovered by emulating the
arm64 `libapp.so` with Unicorn (harness: `/tmp/claude-1000/emu8.py`, venv `/tmp/claude-1000/emu-venv`):
map the ELF, apply relocations, run `.init_array`, stub libc/JNIEnv, call each `Java_…C0104_…`
export and read the plaintext the function hands to `NewStringUTF` (JNIEnv+0x538) / leaves in its
TLS buffer. Nine of the strings are gated behind a runtime integrity check
(`if (*PTR_a == *PTR_b)`); running the constructors makes the guard pass so they decrypt too.

## Recovered strings

Support / social links (one `String` each):
- `tg://resolve?domain=adbRemote`
- `https://t.me/adbRemote`
- `fb://page/425618483963397`
- `https://www.facebook.com/adbRemote`
- `fb://profile/100012183557720`
- `https://www.facebook.com/kaungkhantkyawprofile`
- `https://github.com/kaungkhantjc/adb-remote-translations`

Backend API (`String` + `String[]`):
- primary base: `https://` + `adbremote.jcoder.workers.dev`
- fallback hosts (`String[]`): `https://adbremote.netlify.app`, `https://adbremote.onrender.com`,
  `https://adbremote.web.app`, `https://adbremote.vercel.app`, `https://adbremote.pages.dev`

TLS certificate pinning (`String[]`, OkHttp `CertificatePinner` pins):
- `sha256/mEflZT5enoR1FuXLgYYGqnVEoZvmf9c2bVBpiOjYQ0c=`
- `sha256/kIdp6NNEd8wsugYyyIYFsi1ylMCED3hZbSR8ZFsa/A4=`

On-device server + crypto (the integrity-gated group):
- server main class: `com.jcapps.adbserver.ADBServer`  (the class inside `assets/server_v1.1.0.jar`,
  launched via `app_process` — see `~.api.adb.ServerInstaller`/`ServerService`)
- AES key (Base64, decodes to **32 bytes → AES-256**): `rSzXquw/O4FXRIsMG/rmz5bAOucbpJqQdB1pcjW+zTQ=`
- helper class/method strings the native code looks up by reflection to apply it:
  `android/util/Base64` . `decode` `(Ljava/lang/String;I)[B`,
  `javax/crypto/Cipher`, `getName` `()Ljava/lang/String;`, `java/lang/String`

## Consequence for the rebuild

These values are only hidden, not secret-by-design — they ship in every APK. For a faithful,
buildable reconstruction the native methods can be replaced by plain Kotlin constants/returns
(drop `libapp.so` and its `System.loadLibrary("app")` + the 21 `external` decls), **or** keep
`libapp.so` as-is and declare the `external` methods. Replacing removes the native integrity
check (`JNI_OnLoad`), which only matters if you want to preserve anti-tamper behavior. The call
sites are: `C2120` (Constants: links + API hosts), `C0416`/`C1872`/ApiClient (base URL, pins),
`C0180`/`ServerInstaller` (server class + AES key). Full raw list: `native_strings.txt`.
