# ADB Remote Enhanced

Enhanced version of the Android app **ADB Remote 1.1.0** (`com.jcapps.adbremote`, versionCode 6),
based on a faithful Kotlin rebuild of its R8-obfuscated release APK. The first commit reproduces
the original app as readable, buildable Kotlin source; every later commit is a deliberate change
on top of it (see
[Changes compared to the original](#changes-compared-to-the-original)).

Current version: **1.2.0** (versionCode 7, server 1.2.0).

## Modules

| Module | What it is |
|---|---|
| `app` | The phone app (`com.jcapps.adbremote`) |
| `server` | The on-device server the app pushes to the TV and starts with `app_process` (`com.jcapps.adbserver`); packed into `assets/server_v<serverVersion>.jar` at build time |
| `navhelper` | **TV Nav Helper**: an accessibility service for the TV that drives the Netflix phone app with the normal TV remote |

## Status

- ~190 app classes in ~300 Kotlin files under `app/src/main/kotlin`. Every class keeps its
  original obfuscated name as a comment (`// obf: C1204`).
- Without a connected device all 39 compared screens match the original (view tree and pixels);
  all 214 compiled layouts match (see *Checking against the original*).
- Tested with a target device (Android TV box, Android 14): connecting, remote control, process
  list, sideload, NTP/FTP/file errors, virtual HID keyboard/trackpad, live mirroring.
- Not compared in detail yet: the remaining server features (apps, files/FTP, clipboard,
  screenshot, performance, device info), light theme, other languages.

## Changes compared to the original

### Reconstruction (first commit)

- **`libapp.so` is gone.** Its 21 native methods (`m787`..`m807`) only returned constants; the
  values recovered by emulation live in `NativeConfig.kt` (derivation:
  `reco/docs/native_values.md`). This also drops the native anti-tamper check.
- **Theme roots on Material3.** apktool flattens them to framework themes, which crashes
  AppCompat/Material widgets.
- **Resource names `do`/`if`** are called `do_kw`/`if_kw` (Java keywords).
- **Readable resource names**: layouts, drawables and other file resources carry descriptive
  names (`dialog_connect` instead of `b5`); duplicated library resources were removed and
  references point to the real library names.
- **Resource shrinking like the original**: release builds use R8 with `shrinkResources` and
  strict mode (`res/raw/keep.xml`), so the APK contents stay close to the original.
- Signed with an own release key (see *Building*), so it does not replace the original as an
  update.

### On-device server rebuilt from source

The original shipped `server_v1.1.0.jar` as a prebuilt binary. It is now the Kotlin module
`server` (same routes, same token scheme, same behavior), built and packed into the app's assets
on every build. Bugs fixed on the way (each marked `Fixed (rebuild)` in the code):

- The server busy-waited (`while (true) {}`) and burned one CPU core at 100 %; it now sleeps.
- `pm`/`am` calls were reported as successful for every exit code; now success needs exit 0.
- `/intent` never fell back to `am start` because every failure counted as success.
- Package names went unquoted into shell commands (`"a; reboot"` would run); they are validated
  now, also for the icon cache file names (no `../`).
- Uninstalling system apps now uses `--user 0` instead of failing; apps removed for the user no
  longer show up as installed.
- Icon cache matched by prefix (wrong icons), ignored banner vs. icon, crashed on drawables
  without intrinsic size, and resolved XML icons with the wrong resources.
- Clipboard: leaked file descriptors and providers, hung without an image part, wrote images to
  a hard-coded folder, answered errors with a stack trace typed as an image.
- Screenshots could return the stale image of an earlier call after a failed `screencap`.
- Relative cache path that only worked because the server starts with cwd `/`.
- Failure messages contained a literal `%s`; IPv6 addresses were handled wrongly.

### App changes

- **Ads and premium removed**: no ad SDKs, no premium/promo screens, everything unlocked.
- **Drawer**: rate, Telegram and Facebook entries removed.
- **Sheets and trackpad**: full-height bottom sheets fill the screen; the trackpad reaches the
  screen edge, has a keyboard button, and can be swiped away like the clipboard sheet (the close
  button is gone); same for the keyboard sheet. Fixed a trackpad crash on recycled
  `MotionEvent`s.
- **Virtual HID devices** (keyboard/mouse on the TV) survive reconnects and work on the first
  connect. Android 13+ forbids `mkfifo` in `/data/local/tmp` (SELinux), so the HID host now reads
  a journal file instead of a FIFO, with a lock and its own PID to avoid duplicate hosts.
- **App list**: warning before uninstalling system apps (red icon), user-uninstalled apps are
  hidden, toolbar icons for sort and force stop, fixed search menu, shorter titles, lists padded
  above the navigation bar, pinned toolbar.
- **APK sideload to the TV** ("Sideload App"): pick APKs on the phone and install them on the TV
  through the server (`/apk/*` routes); the list keeps the right icons while scrolling.
- **One connection state for the whole app**: pages derive "device not reachable" from a shared
  connection status instead of pinging on their own; errors appear right away in headline style.
- **Wording**: "Device" everywhere instead of "TV"/"server"; NTP, FTP and file dialogs show the
  device's real error message (NTP has separate messages for connection and server errors); the
  connect dialog shows a fallback text instead of an empty message.
- **Clipboard**: no save button (and no crash) when no device is connected, as in the original
  layout.
- **Live mirroring** (new tool tile "Mirror"): the TV screen live on the phone, landscape full
  screen; taps and swipes on the picture go to the TV; buttons for Back, Home and closing the
  stream. The server captures display 0 into a virtual display, encodes it with the hardware
  H.264 encoder and streams it on TCP port 3126 (same token as the HTTP API); the app decodes it
  with `MediaCodec` straight into a `SurfaceView`. Input is injected through `InputManager`.
  Capture, encoder setup and injection follow [scrcpy](https://github.com/Genymobile/scrcpy)
  (Apache License 2.0).

### TV Nav Helper (`navhelper`)

The Netflix TV app does not run on the box, the phone app does but is made for touch. The Nav
Helper is an accessibility service on the TV that makes the phone app usable with the normal
remote, optimized only for Netflix:

- **Selection frame**: a green frame marks the current element; the D-pad moves it, OK clicks.
  Unhelpful targets are skipped (trailer controls, rating/share, cast lists, unlabeled views,
  loading spinners, the player's lock button).
- **Rows and lists**: up/down move row by row, left/right card by card. The list scrolls with
  exact drags (finger held still before lifting, so nothing flings or overshoots), the selection
  is centered vertically, the frame hides while scrolling and shows at the predicted end position
  as soon as the finger lifts. Tile sizes come from the views' real bounds, so rows of different
  heights center correctly. Fast key presses are queued (up to 4) instead of cancelling each
  other; a watchdog brings the frame back if a gesture callback never arrives.
- **Pages**: after a click the frame jumps to the new page (detail pages start on Play/Resume,
  choice dialogs on the current choice); going back restores the element you came from.
- **Player**: with controls hidden, OK = play/pause, left/right = 10 s back/forward, up/down show
  the controls. With controls shown the frame stays on play/pause, left/right skip 10 s, down
  selects the progress bar. On the progress bar a finger is put on the knob and held; left/right
  drag it (holding the key speeds up: 10 s, 30 s, 60 s steps), up/down/OK release it. Netflix
  starts playback after every seek; a paused video is paused again immediately.
- **Key repeat**: keys consumed by an accessibility service never reach Android's key repeat, so
  the helper repeats held direction keys itself.
- **Settings app** "TV Nav Helper" on the TV: service status, target apps (default
  `com.netflix.mediaclient`), and a **debug overlay** switch (all candidates, scroll containers,
  key/status log). The overlay can also be toggled over adb:

      adb shell am broadcast -a dev.adbremote.navhelper.DEBUG --ez on true -p dev.adbremote.navhelper

  Further test hooks (`DUMP`, `CLICK --es label …`, `DRAG --es xy x1,y1,x2,y2`) are documented
  in `NavService.kt`.
- Long press OK pauses the helper for the current app.

Install on the TV, then allow restricted settings and enable the service:

    adb install navhelper/build/outputs/apk/debug/navhelper-debug.apk
    adb shell appops set dev.adbremote.navhelper ACCESS_RESTRICTED_SETTINGS allow
    # Settings > Accessibility > TV Nav Helper

## Building

Requirements: JDK 21, Android SDK (compileSdk 35), Gradle 8.13 (wrapper included).

    ./gradlew :app:assembleRelease          # app/build/outputs/apk/release/app-release.apk (R8)
    ./gradlew :app:assembleQa               # test build without R8, see below
    ./gradlew :navhelper:assembleDebug      # TV Nav Helper

Release signing reads `keystore.properties` in the project root (not in git):

    storeFile=keystore/release.jks
    storePassword=…
    keyAlias=adbremote
    keyPassword=…

`keystore/` and `keystore.properties` are git-ignored. Without the file, release and qa builds
are signed with the debug key.

`app/src/main/assets` (Lottie animations, texts) and `app/src/main/jniLibs` (adb binaries
`libfs*.so`) come unchanged from the original APK. `server_v<serverVersion>.jar` (version in `gradle.properties`) is generated from the
`server` module on every build.

## Test build `qa`

`qa` is the release build without R8 plus two things from `app/src/qa`:

- Package `com.jcapps.adbremote.qa` and own provider authorities, so it installs **next to the
  original**.
- `DebugBridge`: a broadcast receiver (shell only, permission `DUMP`) that drives the app from
  inside its own process — view tree, click on a view/preference, back, start an activity,
  screenshot of the own window, last crash. Usage:
  `reco/tools/ar.sh tree | click text:Settings | pref theme | back | shot | crash`

None of this is in the release build.

## Checking against the original

- `reco/scripts/compare_layouts.py <apk>` — diff all compiled layouts (aapt2) with name
  normalization.
- `reco/tools/drive.py <package> reco/tools/screens.steps <output>` — drive both apps identically
  through all screens (uiautomator2, coordinates only from the current view tree). The package of
  the foreground window is checked before and after every action; if another app is in front,
  the run stops without touching it.
- `reco/tools/compare_screens.py <orig> <rebuild>` — view tree and pixel diff per screen.

## How the resources were repaired

R8 shortened not only class names but also resource names in the original — including those of
the libraries. Library code looks resources up by their real names, though. The scripts in
`reco/scripts` restore them:

| Script | Problem | Approach |
|---|---|---|
| `attr_align.py` / `attr_apply.py` | library attribute names shortened | aapt2 assigns IDs alphabetically by real name → align the ID order with the AAR attributes (weighted LCS over format signatures) |
| `attr_truth.py` | verifying the mapping | compiled XMLs keep the real attribute names in their string pool → ground truth (108/109 correct, rest fixed) |
| `id_align.py` / `id_apply.py` | library view IDs shortened (`parentPanel` → `or`) | match layouts to the AAR layouts by tag structure, take over IDs pairwise, check monotonicity against the ID order |
| `split_refs.py` | 64 references as `@null` | 45 drawables live in the density split `split_config.xxhdpi.apk`, which apktool does not decode |
| `fix_double_escape.py` | `\\n` instead of `\n` in strings | undo the TSV escaping of the string list |

The mapping tables are in `reco/data` (`attr_map.tsv`, `id_map.tsv`, `layout_map.tsv`,
`registry.tsv` = obfuscated class → Kotlin name, `native_method_map.tsv`). `reco/tools/emu-m798`
emulates `libapp.so` with a JNI trace (Unicorn), `reco/tools/strdec` decrypts single obfuscated
strings.

The scripts expect the original APK and its decompiled output under `original/`, the mapping
tables under `reco/data`.
