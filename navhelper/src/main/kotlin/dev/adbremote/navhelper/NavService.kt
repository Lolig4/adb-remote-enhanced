package dev.adbremote.navhelper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.SystemClock
import kotlin.math.hypot
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction

/**
 * Drives touch-only apps with the D-pad: arrows move a highlight to the nearest clickable element
 * in that direction (scrolling lists when needed), OK clicks it, long-press OK pauses the helper
 * for the current app. Only active while a target app (see [Prefs.targets]) is in the foreground.
 *
 * Debug / test hooks via adb broadcasts (action prefix dev.adbremote.navhelper.):
 *   DEBUG --ez on true|false     debug overlay
 *   TARGET_ADD / TARGET_REMOVE --es pkg <package>
 *   DUMP                         log the window tree (logcat tag NavHelper)
 *   CLICK --es label <text/description prefix>  click that element
 *   DRAG --es xy x1,y1,x2,y2 [--el ms N]  test drag, logs how far the selection moved
 */
class NavService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: Prefs
    private val nav = Navigator()
    private var overlay: Overlay? = null
    private val windowManager by lazy { getSystemService(WindowManager::class.java) }

    private var selRect: Rect? = null
    /** Visible part of the selection (drawn). */
    private var selVisible: Rect? = null
    private var selId: String? = null
    private var selNode: AccessibilityNodeInfo? = null
    private var activePkg: String? = null
    private val pausedPkgs = HashSet<String>()
    /** Running scroll gestures; the frame is hidden while > 0 and shown again when they settled. */
    private var scrolling = 0
    private val scrollDone = Runnable { scrolling = 0; refresh(); drainKeys() }
    private var lastKey = "-"
    private var lastAction = "-"
    private var longPressFired = false
    private val longPress = Runnable {
        longPressFired = true
        activePkg?.let { pausedPkgs.add(it) }
        showMessage("Nav helper paused for this app")
        hideSelection()
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action?.substringAfterLast('.')) {
                "DEBUG" -> { prefs.debug = intent.getBooleanExtra("on", true); refresh() }
                "TARGET_ADD" -> intent.getStringExtra("pkg")?.let { prefs.targets = prefs.targets + it }
                "TARGET_REMOVE" -> intent.getStringExtra("pkg")?.let { prefs.targets = prefs.targets - it }
                "DUMP" -> dump()
                "CLICK" -> {
                    // label prefix match (text or content description), logs what it clicked
                    val label = intent.getStringExtra("label") ?: ""
                    collect()
                    val hit = nav.targets.firstOrNull { (it.label ?: "").startsWith(label) }
                    Log.d(TAG, "CLICK '$label' -> ${hit?.let { describe(it) } ?: "not found"}")
                    hit?.let { selNode = it.node; selRect = Rect(it.rect); selId = it.id; selVisible = Rect(it.visible); click() }
                }
                "DRAG" -> {
                    val f = (intent.getStringExtra("xy") ?: "").split(",").map { it.trim().toFloat() }
                    dbgMoveMs = intent.getLongExtra("ms", 0L)
                    HOLD_MS = intent.getLongExtra("hold", HOLD_MS)
                    collect(); val n = selNode
                    val before = Rect().also { n?.refresh(); n?.getBoundsInScreen(it) }
                    dragExact(f[0], f[1], f[2], f[3], n) {
                        val after = Rect().also { n?.refresh(); n?.getBoundsInScreen(it) }
                        Log.d(TAG, "DRAG req=${(f[2] - f[0]).toInt()},${(f[3] - f[1]).toInt()} moved=${after.left - before.left},${after.top - before.top}")
                    }
                }
            }
        }
    }

    override fun onServiceConnected() {
        prefs = Prefs(this)
        val filter = IntentFilter().apply {
            listOf("DEBUG", "TARGET_ADD", "TARGET_REMOVE", "DUMP", "DRAG", "CLICK").forEach { addAction("$ACTION_PREFIX$it") }
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, RECEIVER_EXPORTED) else registerReceiver(receiver, filter)
        Log.i(TAG, "connected, targets=${prefs.targets} flags=0x${Integer.toHexString(serviceInfo.flags)} caps=${serviceInfo.capabilities}")
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(receiver) }
        removeOverlay()
        super.onDestroy()
    }

    override fun onInterrupt() {}

    // ------------------------------------------------------------------ events

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (prefs.debug && event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
            Log.d(TAG, "window state: ${event.packageName} ${event.className} types=${event.contentChangeTypes}")
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = foregroundPackage() ?: event.packageName?.toString()
            if (pkg != null && pkg != activePkg) onPackageChanged(pkg)
        }
        // while our own drag runs, skip the (slow) re-read: it blocks the gesture callbacks and
        // the settle polling; the end of the drag refreshes anyway
        if (isActive() && scrolling == 0 && settling == 0) scheduleRefresh()
    }

    private fun onPackageChanged(pkg: String) {
        if (activePkg != null && activePkg != pkg) pausedPkgs.remove(activePkg)
        activePkg = pkg
        selRect = null; selId = null; selNode = null
        if (isActive()) { ensureOverlay(); scheduleRefresh() } else removeOverlay()
    }

    private fun isActive(): Boolean {
        val pkg = activePkg ?: return false
        return pkg in prefs.targets && pkg !in pausedPkgs
    }

    private var refreshPending = false
    private fun scheduleRefresh() {
        if (refreshPending) return
        refreshPending = true
        handler.postDelayed({ refreshPending = false; refresh() }, 120)
    }

    /** Re-reads the screen, keeps the selection on the same element (it may have moved). */
    private fun refresh() {
        if (!isActive()) { removeOverlay(); return }
        ensureOverlay()
        collect()
        // frame sits at the predicted position, the content is still catching up: keep it there
        if (settling > 0) { updateOverlay(); return }
        val old = selRect
        val gone = old != null && scrolling == 0 && selNode.let { it == null || !it.refresh() || !it.isVisibleToUser }
        // player: the controls just come and go, no page history; start on play/pause
        if (nav.player != null) {
            autoPicked = false
            val cur = if (gone || old == null) null else nav.targets.firstOrNull { it.node == selNode }
            if (cur != null) select(cur, scroll = false)
            else { selNode = null; selRect = null; selId = null; selVisible = null; playPause()?.let { select(it, scroll = false) } }
            updateOverlay(); return
        }
        // selected element gone and an earlier page is back (BACK): its old selection wins over
        // a lookalike found by position
        if (gone && restoreFromHistory()) { updateOverlay(); return }
        val cur = if (old != null && !gone) nav.find(old, selId, selNode) else null
        if (prefs.debug && cur != null && cur.node != selNode) Log.d(TAG, "refresh: selection re-found as other node ${describe(cur)}")
        // the new page was still loading when we picked its start element: move to the content
        // once it is there (until the user presses a key)
        if (autoPicked && scrolling == 0 && restoreFromHistory()) { updateOverlay(); return }
        if (autoPicked && scrolling == 0) startTarget()?.takeIf { it.node != selNode }?.let {
            if (prefs.debug) Log.d(TAG, "page loaded -> ${describe(it)}")
            select(it, scroll = false); updateOverlay(); return
        }
        if (cur != null) select(cur, scroll = false)
        else if (old == null) startTarget()?.let { select(it, scroll = false) }
        else if (gone) pageChanged()
        // learn how far the app lags behind a drag (undershoot), so the next drag lands exactly
        val expected = expectCenterY
        if (cur != null && expected != null && scrolling == 0 && !cur.clipped) {
            // > 0: the content moved too little (undershoot), < 0: too far
            val under = (expected - cur.full.centerY()) * expectSign
            if (kotlin.math.abs(under) in 3..60) learnedSlopY = (learnedSlopY + under * 0.5f).coerceIn(-60f, 200f)
            expectCenterY = null
            if (prefs.debug) Log.d(TAG, "center undershoot $under -> learned ${learnedSlopY.toInt()}")
        }
        updateOverlay()
    }

    /** Like a TV: start in the page content (detail page: "Abspielen"), not on the toolbar. */
    private fun startTarget(): Target? {
        if (nav.player != null) playPause()?.let { return it }
        val content = nav.targets.filter { it.container != null && !it.visible.isEmpty }
        // Netflix detail page: the play/resume button, like on the TV app
        content.firstOrNull { t -> PLAY_WORDS.any { w -> (t.label ?: "").lowercase().let { it.startsWith(w) || it.endsWith(w) } } }?.let { return it }
        // choice lists (audio/subtitles): the current choice; wide rows only, so the selected tab
        // of the bottom bar does not count
        val wide = screenRect().width() / 4
        nav.targets.firstOrNull { it.rect.width() > wide && !it.visible.isEmpty && subChecked(it.node) }?.let { return it }
        return content.minWithOrNull(compareBy<Target>({ it.visible.top / 40 }, { it.visible.left })) ?: nav.first()
    }

    /** Selection was picked automatically on a new page (no key pressed since). */
    private var autoPicked = false

    /** Selections of pages left behind (newest last), restored when such a page comes back. */
    private val history = ArrayDeque<AccessibilityNodeInfo>()

    /** The selected element is gone (new page/dialog, or back to an earlier page). */
    private fun pageChanged() {
        if (restoreFromHistory()) return
        if (!autoPicked) selNode?.let { history.addLast(it); if (history.size > 8) history.removeFirst() }
        val t = startTarget()
        autoPicked = true
        if (prefs.debug) Log.d(TAG, "page changed -> ${t?.let { describe(it) }} (new)")
        if (t != null) select(t, scroll = false) else { selRect = null; selNode = null; selId = null; selVisible = null }
    }

    /** An earlier page is visible again: select what was selected there. */
    private fun restoreFromHistory(): Boolean {
        val back = history.indices.reversed().firstOrNull { i -> nav.targets.any { it.node == history[i] } } ?: return false
        val node = history[back]
        while (history.size > back) history.removeLast()
        val t = nav.targets.first { it.node == node }
        autoPicked = false
        if (prefs.debug) Log.d(TAG, "page changed -> ${describe(t)} (back)")
        select(t, scroll = false)
        return true
    }

    private fun collect() {
        val screen = screenRect()
        val roots = windows.mapNotNull { w ->
            // our own overlay and the system bars are not navigation targets
            if (w.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY ||
                w.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_SYSTEM) null else w.root
        }.ifEmpty { listOfNotNull(rootInActiveWindow) }
        nav.collect(roots, screen)
    }

    // ------------------------------------------------------------------ keys

    /** Package of the focused application window. */
    private fun foregroundPackage(): String? =
        windows.firstOrNull { it.isActive && it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION }
            ?.root?.packageName?.toString() ?: rootInActiveWindow?.packageName?.toString()

    override fun onKeyEvent(event: KeyEvent): Boolean {
        Log.i(TAG, "onKeyEvent ${event.keyCode} action=${event.action}")
        val pkg = try { foregroundPackage() } catch (e: Exception) { Log.w(TAG, "fg failed", e); null }
        Log.i(TAG, "  pkg=$pkg active=$activePkg isActive=${pkg?.let { it in prefs.targets }}")
        if (pkg != null && pkg != activePkg) onPackageChanged(pkg)
        if (!isActive()) return false
        val dir = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> Dir.UP
            KeyEvent.KEYCODE_DPAD_DOWN -> Dir.DOWN
            KeyEvent.KEYCODE_DPAD_LEFT -> Dir.LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> Dir.RIGHT
            else -> null
        }
        val isOk = event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.keyCode == KeyEvent.KEYCODE_ENTER ||
            event.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
        if (dir == null && !isOk) return false
        if (dir != null) {
            // own key repeat: keys we consume never reach the system's repeat generator
            if (event.repeatCount > 0) return true
            handler.removeCallbacks(repeater)
            if (event.action == KeyEvent.ACTION_DOWN) {
                repeatEvent = event; repeatDir = dir; repeatN = 0
                handler.postDelayed(repeater, REPEAT_DELAY_MS)
            } else repeatDir = null
        }
        return handleKey(event, dir)
    }

    private var repeatEvent: KeyEvent? = null
    private var repeatDir: Dir? = null
    private var repeatN = 0

    /** Held direction key: repeat it (progress bar fast, lists once the last scroll is done). */
    private val repeater = object : Runnable {
        override fun run() {
            val d = repeatDir ?: return
            val ev = repeatEvent ?: return
            if (scrolling > 0 || settling > 0) { handler.postDelayed(this, 30); return }
            repeatN++
            handleKey(KeyEvent.changeTimeRepeat(ev, SystemClock.uptimeMillis(), repeatN), d)
            handler.postDelayed(this, when { scrubbing -> 70L; nav.player != null -> 300L; else -> 120L })
        }
    }

    private fun handleKey(event: KeyEvent, dir: Dir?): Boolean {
        autoPicked = false
        lastKey = KeyEvent.keyCodeToString(event.keyCode).removePrefix("KEYCODE_") +
            if (event.action == KeyEvent.ACTION_DOWN) "↓" else "↑"
        if (nav.player != null || nav.targets.isEmpty()) collect() // player controls come and go
        if (nav.player != null && playerKey(event, dir)) {
            updateOverlay()
            Log.i(TAG, "key=$lastKey action=$lastAction sel=${selRect?.toShortString()} (player)")
            return true
        }
        if (dir != null) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                // a scroll is still running: remember the press (overlapping drags cancel each
                // other), it runs as soon as the list stands still
                if (scrolling > 0 || settling > 0) {
                    if (pendingKeys.size < 4) pendingKeys.addLast(dir)
                    lastAction = "queued $dir (${pendingKeys.size})"
                } else move(dir)
            }
        } else {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                longPressFired = false
                handler.postDelayed(longPress, LONG_PRESS_MS)
            } else if (event.action == KeyEvent.ACTION_UP) {
                handler.removeCallbacks(longPress)
                if (!longPressFired) click()
            }
        }
        updateOverlay()
        Log.i(TAG, "key=$lastKey action=$lastAction sel=${selRect?.toShortString()} targets=${nav.targets.size}")
        return true
    }

    // ------------------------------------------------------------------ Netflix player

    // while the knob is dragged Netflix shows only the bar
    private fun playerControlsShown() = scrubbing || nav.targets.any { it.id in PLAY_PAUSE_IDS || isPlayerSeekbar(it.node) }
    private fun playPause() = nav.targets.firstOrNull { it.id in PLAY_PAUSE_IDS }

    /**
     * TV-like player keys. Controls hidden: OK = pause/play (and show the controls), left/right =
     * 10 s back/forward (double tap on that side, the controls stay hidden), up/down = show the
     * controls. Controls shown: the D-pad moves between the buttons, OK clicks (normal logic).
     * Returns false when the normal navigation should handle the key.
     */
    private fun playerKey(event: KeyEvent, dir: Dir?): Boolean {
        if (playerControlsShown()) {
            // finger already on the bar: keep dragging it, whatever the selection says now
            val bar = scrubBar
            if (scrubbing && bar != null && (dir == Dir.LEFT || dir == Dir.RIGHT)) {
                if (event.action == KeyEvent.ACTION_DOWN) scrub(bar, dir, event.repeatCount)
                return true
            }
            // selection lost (controls re-rendered after a seek, or just appeared): play/pause,
            // and the key still does its job from there
            val sel = selNode?.let { n -> nav.targets.firstOrNull { it.node == n } }
                ?: playPause()?.also { select(it, scroll = false) } ?: return true
            val sideways = dir == Dir.LEFT || dir == Dir.RIGHT
            // progress bar: finger down on its knob, left/right drag it (held key: faster); any
            // other key lifts the finger (the app seeks there)
            if (sideways && isPlayerSeekbar(sel.node)) {
                if (event.action == KeyEvent.ACTION_DOWN) scrub(sel.node, dir!!, event.repeatCount)
                return true
            }
            if (scrubbing) {
                // up/down lift the finger; once the controls are back, the key moves on from the bar
                if (event.action == KeyEvent.ACTION_DOWN) {
                    scrubRelease()
                    if (dir != null) handler.postDelayed({ collect(); move(dir); updateOverlay() }, 350)
                    lastAction = "player: release bar"
                }
                return true
            }
            // OK on the bar only lifts the finger (a click would tap its middle)
            if (dir == null && isPlayerSeekbar(sel.node)) { handler.removeCallbacks(longPress); return true }
            // left/right on play/pause: 10 s back/forward, the selection stays on play/pause
            if (sideways && sel.id in PLAY_PAUSE_IDS) {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    seek(if (dir == Dir.LEFT) SEEK_BACK else SEEK_FWD)
                    lastAction = "player: seek ${if (dir == Dir.LEFT) "-" else "+"}10s"
                }
                return true
            }
            return false
        }
        if (dir != null) {
            if (event.action != KeyEvent.ACTION_DOWN) return true
            when (dir) {
                Dir.LEFT, Dir.RIGHT -> {
                    val id = if (dir == Dir.LEFT) SEEK_BACK else SEEK_FWD
                    showControls { seek(id) }
                    lastAction = "player: seek ${if (dir == Dir.LEFT) "-" else "+"}10s"
                }
                else -> { showControls(); lastAction = "player: show controls" }
            }
            return true
        }
        // OK (on release; a long press keeps pausing the helper)
        if (event.action == KeyEvent.ACTION_DOWN || longPressFired) return false
        handler.removeCallbacks(longPress)
        showControls { playPause()?.node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) }
        lastAction = "player: play/pause"
        return true
    }

    /** Clicks a seek button; Netflix resumes playback on a seek, so a paused video is paused again. */
    private fun seek(id: String) {
        val paused = nav.targets.any { it.id == "playButtonTestTag" }
        nav.playerKeys[id]?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        if (paused) keepPaused()
    }

    // progress bar drag: one touch that stays down between key presses
    private var scrubbing = false
    private var scrubStroke: GestureDescription.StrokeDescription? = null
    private var scrubBusy = false
    private var scrubX = 0f
    private var scrubY = 0f
    private var scrubGoal = 0f
    private var scrubLift = false
    private var scrubBar: AccessibilityNodeInfo? = null
    private var scrubPaused = false

    /**
     * Left/right on the progress bar: put a finger on its knob (first press), then drag it 10 s
     * per press (held key: 30 s, then 60 s steps). The finger stays down until [scrubRelease].
     */
    private fun scrub(bar: AccessibilityNodeInfo, dir: Dir, repeat: Int) {
        bar.refresh()
        val ri = bar.rangeInfo ?: return
        val r = Rect().also { bar.getBoundsInScreen(it) }
        // the knob runs between the bar's ends minus half a knob (measured: ~36 px at 1080p)
        val pad = r.height() * 1.1f
        val track = r.width() - 2 * pad
        if (!scrubbing) {
            scrubbing = true; scrubLift = false; scrubBar = bar
            scrubPaused = nav.targets.any { it.id == "playButtonTestTag" }
            scrubX = r.left + pad + (ri.current - ri.min) / (ri.max - ri.min) * track
            scrubY = r.exactCenterY()
            // the knob only follows after the touch slop: the first move goes that much further
            scrubGoal = scrubX + (if (dir == Dir.RIGHT) 1 else -1) * android.view.ViewConfiguration.get(this).scaledTouchSlop
            val down = GestureDescription.StrokeDescription(Path().apply { moveTo(scrubX, scrubY) }, 0, 40, true)
            dispatchScrub(down)
        }
        val ms = when { repeat < 4 -> 10_000f; repeat < 12 -> 30_000f; else -> 60_000f }
        val px = ms / (ri.max - ri.min) * track
        scrubGoal = (scrubGoal + if (dir == Dir.RIGHT) px else -px).coerceIn(r.left + pad, r.right - pad)
        lastAction = "player: drag bar to x=${scrubGoal.toInt()} (cur ${(ri.current / 1000).toInt()}s of ${(ri.max / 1000).toInt()}s, bar ${r.toShortString()})"
        pumpScrub()
    }

    /** Lifts the finger off the progress bar (the app seeks to where it is). */
    private fun scrubRelease() {
        if (!scrubbing) return
        scrubLift = true
        pumpScrub()
    }

    private fun dispatchScrub(stroke: GestureDescription.StrokeDescription) {
        scrubBusy = true
        scrubStroke = stroke
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) { scrubBusy = false; pumpScrub() }
            override fun onCancelled(gestureDescription: GestureDescription?) { scrubBusy = false; scrubbing = false; scrubStroke = null }
        }, null)
    }

    /** Sends the next piece of the held touch once the previous one is done. */
    private fun pumpScrub() {
        val prev = scrubStroke ?: return
        if (scrubBusy || !scrubbing) return
        if (scrubGoal != scrubX) {
            val path = Path().apply { moveTo(scrubX, scrubY); lineTo(scrubGoal, scrubY) }
            scrubX = scrubGoal
            dispatchScrub(prev.continueStroke(path, 0, 60, true))
        } else if (scrubLift) {
            scrubbing = false
            val path = Path().apply { moveTo(scrubX, scrubY) }
            dispatchScrub(prev.continueStroke(path, 0, 30, false))
            scrubStroke = null
            // Netflix plays after a seek: a paused video stays paused
            if (scrubPaused) handler.postDelayed({ keepPaused() }, 150)
        }
    }

    /**
     * Netflix starts playback after every seek (buttons, progress bar, even set-progress): pause
     * again the moment the pause button exists. Looked up directly by its tag every frame instead
     * of re-reading the whole screen, so the video barely moves.
     */
    private fun keepPaused() {
        val t0 = SystemClock.uptimeMillis()
        // the player's control layer (parent of the tap area); the buttons sit a few levels below
        val layer = nav.player?.parent ?: return
        fun find(n: AccessibilityNodeInfo, d: Int): AccessibilityNodeInfo? {
            for (i in 0 until n.childCount) {
                val c = n.getChild(i) ?: continue
                if (c.viewIdResourceName == "pauseButtonTestTag") return c
                if (d < 3) find(c, d + 1)?.let { return it }
            }
            return null
        }
        val repause = object : Runnable {
            override fun run() {
                layer.refresh()
                val pause = find(layer, 0)
                if (pause == null) { if (SystemClock.uptimeMillis() - t0 < 2500) handler.postDelayed(this, 16); return }
                pause.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (prefs.debug) Log.d(TAG, "re-paused after ${SystemClock.uptimeMillis() - t0}ms")
            }
        }
        handler.post(repause)
    }

    /**
     * Shows the controls (click on the player area toggles them, so only while hidden), then runs
     * [then] and selects [select] (default play/pause) once the buttons are there.
     */
    private fun showControls(select: String? = null, then: (() -> Unit)? = null) {
        nav.player?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        var tries = 0
        val wait = object : Runnable {
            override fun run() {
                collect()
                if (!playerControlsShown() && ++tries < 20) { handler.postDelayed(this, 40); return }
                then?.invoke()
                collect()
                (select?.let { id -> nav.targets.firstOrNull { it.id == id } } ?: playPause())?.let { select(it, scroll = false) }
                updateOverlay()
            }
        }
        handler.postDelayed(wait, 40)
    }

    private var keyAt = 0L
    private fun move(dir: Dir) {
        keyAt = SystemClock.uptimeMillis()
        collect()
        val from = selRect ?: run { startTarget()?.let { select(it) }; lastAction = "initial select"; return }
        val cur = selNode?.let { n -> nav.targets.firstOrNull { it.node == n } }
        // Netflix row layout (vertical list of horizontal rows): fixed row logic
        val curRow = cur?.horizontalScroller
        val rowList = curRow?.container?.takeIf { !it.horizontal }
        if (cur != null && curRow != null && rowList != null) { rowMove(cur, curRow, rowList, dir, from); return }
        // a selected area with buttons inside (Netflix billboard): down enters it first
        if (cur != null && dir == Dir.DOWN && cur.rect.width() >= screenRect().width() * 8 / 10) {
            nav.inside(cur).minWithOrNull(compareBy<Target>({ it.rect.top }, { it.rect.left }))?.let {
                lastAction = "enter -> ${describe(it)}"; select(it); return
            }
        }
        // up from the topmost button inside such an area goes back to the area
        if (cur != null && dir == Dir.UP) {
            val outer = nav.around(cur)
            if (outer != null && nav.inside(outer).none { it.rect.bottom <= cur.rect.top }) {
                lastAction = "leave -> ${describe(outer)}"; select(outer); return
            }
        }
        // nothing above a top area (Netflix billboard behind the category chips): go to the fixed
        // elements lying over its upper part
        if (cur != null && dir == Dir.UP && nav.next(from, dir) == null) {
            nav.targets.filter { it.container == null && it.rect.top >= cur.rect.top && it.rect.bottom <= cur.rect.top + cur.rect.height() / 4 }
                .minByOrNull { kotlin.math.abs(it.rect.centerX() - from.centerX()) }
                ?.let { lastAction = "up -> ${describe(it)}"; select(it, scroll = false); return }
        }
        val next = nav.next(from, dir)?.let { n ->
            // don't jump from inside an area to the area itself (or into another area's interior)
            if (cur != null && n.rect.contains(cur.rect)) nav.next(from, dir, exclude = n) else n
        }
        val list = cur?.verticalScroller
        val vertical = dir == Dir.UP || dir == Dir.DOWN
        // TV-like: in a scrolling list, up/down first scroll the list; fixed bars come last
        if (next != null && !(vertical && list != null && next.verticalScroller == null)) {
            lastAction = "move $dir -> ${describe(next)}"
            if (dir == Dir.LEFT || dir == Dir.RIGHT) selectInRow(next) else select(next)
            return
        }
        if (next != null && list != null) {
            // drag the list exactly half a screen (page scrolls skip elements), then continue from
            // where the current element is now (shifted by the drag, also if it left the screen)
            val half = list.rect.height() / 2f
            val d = if (dir == Dir.DOWN) -half else half
            val x = list.rect.exactCenterX(); val y0 = list.rect.exactCenterY() - d / 2
            lastAction = "list step $dir"
            dragExact(x, y0, x, y0 + d) {
                collect()
                val node = selNode
                val origin = if (node != null && node.refresh()) Rect().also { node.getBoundsInScreen(it) }
                    else Rect(from).also { it.offset(0, d.toInt()) }
                val inList = nav.targets.filter { it.verticalScroller?.node == list.node }
                val pick = nav.next(origin, dir)?.takeIf { it.verticalScroller?.node == list.node }
                    ?: inList.filter { isBeyond(it.rect, origin, dir) }.minByOrNull { d2(it.rect, origin) }
                    ?: nav.next(origin, dir)
                pick?.let { select(it) }
                lastAction = "list step $dir -> ${pick?.let { describe(it) } ?: "-"}"
            }
            return
        }
        // left/right with nothing in the row: a fixed element slightly above/below (category
        // chips -> download button), else nothing (TV behaviour, no blind scrolling)
        if (dir == Dir.LEFT || dir == Dir.RIGHT) {
            val near = nav.targets.filter {
                it.container == null && it.rect != from &&
                    (if (dir == Dir.RIGHT) it.rect.left >= from.right - 4 else it.rect.right <= from.left + 4) &&
                    kotlin.math.abs(it.rect.centerY() - from.centerY()) < 150 * resources.displayMetrics.density / 2
            }.minByOrNull { kotlin.math.abs(it.rect.centerX() - from.centerX()) }
            if (near != null) { lastAction = "side $dir -> ${describe(near)}"; select(near, scroll = false) }
            else lastAction = "edge $dir"
            return
        }
        // fixed UI (toolbar, tab bar) at its edge: nothing happens
        if (cur != null && cur.container == null) { lastAction = "edge $dir"; return }
        // nothing in that direction on screen -> scroll and try again
        val container = nav.scrollableFor(from, dir)?.node
        if (scroll(from, dir)) {
            lastAction = "scroll $dir"
            handler.postDelayed({
                collect()
                val horizontal = dir == Dir.LEFT || dir == Dir.RIGHT
                val after = nav.next(from, dir, sameContainer = if (horizontal) container else null)
                    ?: nav.next(from, dir)
                    ?: (if (horizontal) nav.targets.filter { it.container?.node == container }.minByOrNull { d2(it.rect, from) } else null)
                    ?: nav.nearest(from)
                after?.let { select(it) }
                lastAction = "scroll $dir -> ${after?.let { describe(it) } ?: "nothing"}"
                updateOverlay()
            }, SCROLL_SETTLE_MS)
        } else {
            lastAction = "edge $dir"
        }
    }

    private fun scroll(from: Rect, dir: Dir): Boolean {
        val container = nav.scrollableFor(from, dir)
        if (container != null) {
            val action = when (dir) {
                Dir.DOWN -> AccessibilityAction.ACTION_SCROLL_DOWN
                Dir.UP -> AccessibilityAction.ACTION_SCROLL_UP
                Dir.LEFT -> AccessibilityAction.ACTION_SCROLL_LEFT
                Dir.RIGHT -> AccessibilityAction.ACTION_SCROLL_RIGHT
            }
            if (container.node.actionList.contains(action) && container.node.performAction(action.id)) { beginScroll(); endScroll(); return true }
            val fallback = if (dir == Dir.DOWN || dir == Dir.RIGHT) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            if (container.node.performAction(fallback)) { beginScroll(); endScroll(); return true }
        }
        // gesture swipe only inside a real scroll container (never blindly over the screen)
        if (container == null) return false
        val area = container.rect
        val cx = from.centerX().toFloat().coerceIn(area.left + 10f, area.right - 10f)
        val cy = from.centerY().toFloat().coerceIn(area.top + 10f, area.bottom - 10f)
        val dist = (if (dir == Dir.LEFT || dir == Dir.RIGHT) area.width() else area.height()) * 0.35f
        val (dx, dy) = when (dir) {
            Dir.DOWN -> 0f to -dist; Dir.UP -> 0f to dist; Dir.RIGHT -> -dist to 0f; Dir.LEFT -> dist to 0f
        }
        return swipe(cx, cy, cx + dx, cy + dy)
    }

    private fun click() {
        val node = selNode
        val rect = selRect
        if (node == null || rect == null) { lastAction = "click: nothing selected"; return }
        var n: AccessibilityNodeInfo? = node
        var depth = 0
        while (n != null && depth < 6) {
            if ((n.isClickable || n.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK }) &&
                n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                lastAction = "click ${describe(Target(node, rect))}"
                scheduleRefresh()
                return
            }
            n = n.parent; depth++
        }
        tap(rect.exactCenterX(), rect.exactCenterY())
        lastAction = "tap ${rect.centerX()},${rect.centerY()}"
        scheduleRefresh()
    }

    // ------------------------------------------------------------------ gestures

    private fun swipe(x1: Float, y1: Float, x2: Float, y2: Float): Boolean {
        val path = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
        return dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 250)).build(), null, null)
    }

    private fun tap(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 60)).build(), null, null)
    }

    // ------------------------------------------------------------------ selection / overlay

    // ------------------------------------------------------------------ Netflix rows


    private fun rowItems(row: Target): List<Target> {
        val all = nav.targets.filter { it.container?.node == row.node }
        // cards only: small buttons inside a card ("play episode") are not row items
        return all.filter { t -> all.none { o -> o !== t && o.rect != t.rect && o.rect.contains(t.rect) } }.sortedBy { it.rect.left }
    }
    private fun rows(list: Target) = nav.scrollables.filter { it.container?.node == list.node && rowItems(it).isNotEmpty() }
        .sortedBy { it.rect.top }

    /**
     * Left/right: neighbour card in the row; up/down: card at about the same x in the next row.
     * The app scrolls them into view itself (show-on-screen); for up/down the row after the target
     * is also shown, so the selection stays around the middle. The bars above/below the list are
     * only reached when the list cannot scroll further.
     */
    private fun rowMove(cur: Target, row: Target, list: Target, dir: Dir, from: Rect) {
        when (dir) {
            Dir.LEFT, Dir.RIGHT -> {
                val items = rowItems(row)
                val i = items.indexOfFirst { it.node == cur.node }
                val n = items.getOrNull(if (dir == Dir.RIGHT) i + 1 else i - 1)
                if (n != null) { lastAction = "row $dir -> ${describe(n)}"; selectInRow(n); return }
                val act = if (dir == Dir.RIGHT) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                if (row.node.actionList.any { it.id == act }) {
                    // move the row by exactly one card (a page scroll would skip cards)
                    val pitch = items.zipWithNext { a, b -> b.rect.left - a.rect.left }.filter { it > 0 }.minOrNull() ?: cur.full.width()
                    // one drag: the next card (one pitch beyond the current one) ends up exactly at
                    // the row edge, so it needs no second pull-in
                    val margin = edgeMargin()
                    val screen = screenRect()
                    val d = (if (dir == Dir.RIGHT) (minOf(row.rect.right, screen.right) - margin) - (cur.full.right + pitch)
                        else (maxOf(row.rect.left, screen.left) + margin) - (cur.full.left - pitch)).toFloat()
                    val y = cur.rect.exactCenterY(); val x0 = row.rect.exactCenterX() - d / 2
                    lastAction = "row step $dir"
                    dragExact(x0, y, x0 + d, y, cur.node) { collect(); rowAfterScroll(dir) }
                } else lastAction = "row end $dir"
            }
            Dir.UP, Dir.DOWN -> {
                val rs = rows(list)
                val i = rs.indexOfFirst { it.node == row.node }
                val r = rs.getOrNull(if (dir == Dir.DOWN) i + 1 else i - 1)
                if (r != null) {
                    val n = rowItems(r).minByOrNull { kotlin.math.abs(it.rect.centerX() - from.centerX()) }!!
                    lastAction = "row $dir -> ${describe(n)}"
                    selectShown(n)
                    return
                }
                // other list elements first (billboard and its buttons above the first row)
                nav.next(from, dir)?.takeIf { it.verticalScroller?.node == list.node && it.horizontalScroller == null }?.let { other ->
                    lastAction = "list $dir -> ${describe(other)}"; select(other); return
                }
                val act = if (dir == Dir.DOWN) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                val canScroll = list.node.actionList.any { it.id == act }
                if (canScroll) {
                    // move the list by exactly one row (a page scroll would skip rows)
                    val pitch = if (rs.size >= 2) rs.zipWithNext { a, b -> b.rect.top - a.rect.top }.filter { it > 0 }.minOrNull() ?: row.rect.height()
                    else row.rect.height()
                    val d = (if (dir == Dir.DOWN) -pitch else pitch).toFloat()
                    val x = list.rect.exactCenterX(); val y0 = list.rect.exactCenterY() - d / 2
                    lastAction = "list step $dir"
                    dragExact(x, y0, x, y0 + d, cur.node) { collect(); rowAfterScroll(dir) }
                    return
                }
                // real start/end of the list -> toolbar above / tab bar below
                val bar = nav.next(from, dir)?.takeIf { it.verticalScroller == null }
                if (bar != null) { lastAction = "to bar -> ${describe(bar)}"; select(bar, scroll = false) } else lastAction = "list end $dir"
            }
        }
    }

    /** After a row/list page scroll: continue from where the selected card is now. */
    private fun rowAfterScroll(dir: Dir) {
        val node = selNode
        val origin = if (node != null && node.refresh()) Rect().also { node.getBoundsInScreen(it) } else selRect ?: return
        val cur = nav.targets.firstOrNull { it.node == node }
        val row = cur?.horizontalScroller
        val list = row?.container
        if (cur != null && row != null && list != null) {
            when (dir) {
                Dir.LEFT, Dir.RIGHT -> {
                    val items = rowItems(row); val i = items.indexOfFirst { it.node == cur.node }
                    items.getOrNull(if (dir == Dir.RIGHT) i + 1 else i - 1)?.let { selectInRow(it) }
                }
                else -> {
                    val rs = rows(list); val i = rs.indexOfFirst { it.node == row.node }
                    val r = rs.getOrNull(if (dir == Dir.DOWN) i + 1 else i - 1)
                    if (r != null) rowItems(r).minByOrNull { kotlin.math.abs(it.rect.centerX() - origin.centerX()) }?.let { selectShown(it) }
                    else nav.next(origin, dir)?.takeIf { it.verticalScroller?.node == list.node }?.let { select(it) }
                }
            }
        } else nav.next(origin, dir)?.let { selectShown(it) }
        updateOverlay()
    }

    /** Sideways in the same row: no vertical re-centering, only pull a cut-off card in. */
    private fun selectInRow(t: Target) {
        selNode = t.node; selRect = Rect(t.rect); selId = t.id; selVisible = Rect(t.visible)
        val tol = 6 * resources.displayMetrics.density
        if (t.clipped && (t.rect.left - t.full.left > tol || t.full.right - t.rect.right > tol)) bringIntoView(t, horizontalOnly = true)
    }

    private fun edgeMargin() = (24 * resources.displayMetrics.density).toInt()


    /** Select and let the app scroll the item fully into view (hide the frame while it moves). */
    private fun selectShown(t: Target) {
        selNode = t.node; selRect = Rect(t.rect); selId = t.id; selVisible = Rect(t.visible)
        if (t.verticalScroller != null) { keepCentered(t); return }
        val covered = t.visible != t.rect
        if (covered) {
            // under a fixed bar (the list thinks it is visible) -> drag it out exactly
            bringIntoView(t)
        } else if (t.node.performAction(AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)) {
            beginScroll(); endScroll(450) // smooth scroll of the list
        } else if (t.clipped) {
            bringIntoView(t)
        }
    }

    private fun select(t: Target, scroll: Boolean = true) {
        selNode = t.node; selRect = Rect(t.rect); selId = t.id; selVisible = Rect(t.visible)
        // keep the selection visible: ask the app to bring a cut-off target fully on screen (the
        // row/list scrolls like on a TV, the next item moves in); the frame follows via refresh()
        if (scroll) {
            // in a list: always center the selection vertically (exact drag)
            if (t.verticalScroller != null) { keepCentered(t); return }
            // the app scrolls the element into view itself if it can (RecyclerView); covered items
            // (under a fixed bar) and apps without that action get an exact drag
            if (t.visible != t.rect) bringIntoView(t)
            else if (t.node.performAction(AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)) { beginScroll(); endScroll(450) }
            else if (t.clipped) bringIntoView(t)
        }
    }

    /**
     * Scrolls so the selection sits in the vertical middle of its list (looks calmer while
     * scrolling, like a TV UI); horizontally only cut-off items are pulled in.
     */
    private fun keepCentered(t: Target) {
        val list = t.verticalScroller
        if (list != null) {
            val center = t.full.exactCenterY()
            val dy = list.rect.exactCenterY() - center
            val limit = list.rect.height() * 0.8f
            if (kotlin.math.abs(dy) > 12 * resources.displayMetrics.density) {
                val d = dy.coerceIn(-limit, limit)
                val x = list.rect.exactCenterX()
                val y0 = list.rect.exactCenterY() - d / 2
                val sideways = t.clipped && (t.full.left < t.rect.left || t.full.right > t.rect.right)
                expectCenterY = list.rect.centerY(); expectSign = if (d > 0) 1 else -1
                lastAction += " +center(${d.toInt()})"
                dragExact(x, y0, x, y0 + d, t.node) {
                    if (sideways) { collect(); nav.targets.firstOrNull { it.node == t.node }?.let { bringIntoView(it, horizontalOnly = true) } }
                }
                return
            }
        }
        if (t.clipped && (t.full.left < t.rect.left || t.full.right > t.rect.right)) bringIntoView(t, horizontalOnly = true)
    }

    /** Drags the target's row/list exactly so far that it is fully visible (no fling). */
    private fun bringIntoView(t: Target, horizontalOnly: Boolean = false) {
        val margin = edgeMargin()
        val screen = screenRect()
        val vis = if (t.visible.isEmpty) Rect(t.full.left, screen.centerY(), t.full.right, screen.centerY() + 1) else t.visible
        var dx = 0; var dy = 0
        if (t.full.right > vis.right) dx = -(t.full.right - vis.right + margin)
        else if (t.full.left < vis.left) dx = vis.left - t.full.left + margin
        if (!horizontalOnly && (t.full.bottom > vis.bottom || t.full.top < vis.top)) {
            // vertically cut off or under a bar: move it to the middle of its list
            val area = t.verticalScroller?.rect ?: screen
            dy = area.centerY() - t.full.centerY()
        }
        if (dx != 0) {
            val area = t.horizontalScroller?.rect ?: screen
            val y = vis.exactCenterY()
            val x0 = if (dx < 0) area.right - margin * 2f else area.left + margin * 2f
            dragExact(x0, y, x0 + dx, y, t.node)
        } else if (dy != 0) {
            val area = t.verticalScroller?.rect ?: screen
            val x = area.exactCenterX()
            val y0 = if (dy < 0) area.centerY() + area.height() / 4f else area.centerY() - area.height() / 4f
            dragExact(x, y0, x, y0 + dy, t.node)
        } else return
        lastAction += " +bringIntoView(${dx},${dy})"
    }

    /** Slow drag that pauses before lifting, so lists move exactly and do not fling. */
    /** Extra distance the app swallows at the start of a drag (learned from the centering result). */
    private var learnedSlopY = 0f
    /** Expected screen position of the selection center after the last centering drag. */
    private var expectCenterY: Int? = null
    /** Direction of that drag (+1 content moved down, -1 up). */
    private var expectSign = 0

    /**
     * Drags exactly (x1,y1)->(x2,y2). The app's content trails the finger, so [then] runs only
     * once [probe] (default: the selection) has stopped moving, not after a fixed time.
     */
    private var dbgMoveMs = 0L
    // finger rests this long before lifting: the velocity tracker then sees 0, so no fling
    private var HOLD_MS = 60L
    private var POLL_MS = 30L
    private var SETTLE_POLLS = 2
    private fun dragExact(x1: Float, y1: Float, x2: Float, y2: Float,
                          probe: AccessibilityNodeInfo? = selNode, then: (() -> Unit)? = null) {
        beginScroll()
        // lists only start moving after the touch slop: add it so the content moves exactly x2-x1/y2-y1
        val slop = android.view.ViewConfiguration.get(this).scaledTouchSlop.toFloat()
        val dx = x2 - x1; val dy = y2 - y1
        val len = kotlin.math.hypot(dx, dy).coerceAtLeast(1f)
        val vertical = kotlin.math.abs(dy) > kotlin.math.abs(dx)
        // Netflix rows lose ~6% (+ a few px) of a sideways drag, measured on the TV
        val extra = if (vertical) learnedSlopY else 8f + len * 0.05f
        val ex = x2 + dx / len * (slop + extra); val ey = y2 + dy / len * (slop + extra)
        // fast move (sideways rows lose movement above ~2 px/ms), then hold the finger still before lifting: no fling, no overshoot
        val move = Path().apply { moveTo(x1, y1); lineTo(ex, ey) }
        val first = GestureDescription.StrokeDescription(move, 0, if (dbgMoveMs > 0) dbgMoveMs else if (vertical) 150L else maxOf(150L, (hypot(ex - x1, ey - y1) / 1.8f).toLong()), true)
        val hold = Path().apply { moveTo(ex, ey); lineTo(ex, ey + 0.1f) }
        val second = first.continueStroke(hold, 0, HOLD_MS, false)
        val t0 = SystemClock.uptimeMillis()
        // where the probe will end up: the drag moves the content exactly (x2-x1, y2-y1)
        val start = probe?.let { p -> Rect().takeIf { p.refresh() }?.also { p.getBoundsInScreen(it) } }
        val expect = start?.let { Rect(it).apply { offset((x2 - x1).toInt(), (y2 - y1).toInt()) } }
        val predicted = probe?.let { p -> nav.targets.firstOrNull { it.node == p } }
            ?.let { t -> Rect(t.full).apply { offset((x2 - x1).toInt(), (y2 - y1).toInt()) } }
        var done = false
        val lifted = lifted@{
            // exactly once per drag, whichever callback (or a refused dispatch) gets here first
            if (done) return@lifted
            done = true
            scrollActivity = SystemClock.uptimeMillis()
            if (prefs.debug) Log.d(TAG, "lifted: key->drag ${t0 - keyAt}ms, gesture ${SystemClock.uptimeMillis() - t0}ms")
            // the app trails the finger by a few hundred ms: show the frame at the predicted end
            // position right away (selection scrolls only), correct it once the content stands still
            val show = then == null && probe != null && probe == selNode && predicted != null
            if (show) {
                val vis = Rect(predicted!!).also { if (!it.intersect(screenRect())) it.setEmpty() }
                selRect = Rect(vis); selVisible = Rect(vis)
                settling++
                if (scrolling > 0) scrolling--
                updateOverlay()
            }
            awaitSettle(probe, predicted ?: expect, then, show)
        }
        val ok = dispatchGesture(GestureDescription.Builder().addStroke(first).build(), object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                if (prefs.debug) Log.d(TAG, "move done after ${SystemClock.uptimeMillis() - t0}ms (planned ${first.duration})")
                // a newer drag may have taken over meanwhile: then the continuation is refused
                val ok2 = dispatchGesture(GestureDescription.Builder().addStroke(second).build(), object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) = lifted()
                    override fun onCancelled(gestureDescription: GestureDescription?) = lifted()
                }, null)
                if (!ok2) lifted()
            }
            override fun onCancelled(gestureDescription: GestureDescription?) = lifted()
        }, null)
        if (!ok) lifted()
    }

    /** Direction presses that came in while scrolling, run one by one afterwards. */
    private val pendingKeys = ArrayDeque<Dir>()

    private fun drainKeys() {
        if (scrolling > 0 || settling > 0) return
        val d = pendingKeys.removeFirstOrNull() ?: return
        keyAt = SystemClock.uptimeMillis()
        move(d)
        updateOverlay()
        Log.i(TAG, "key=queued $d action=$lastAction sel=${selRect?.toShortString()}")
        // the move did not scroll (frame only): go on with the next one right away
        if (scrolling == 0 && settling == 0) handler.post { drainKeys() }
    }

    /** Last time a drag started, ended or settled (for the watchdog). */
    private var scrollActivity = 0L

    /**
     * Safety net: the frame is hidden while scrolling/settling; if a counter ever stays up (a
     * callback that never came), show the frame again after a quiet moment instead of never.
     */
    private val scrollWatchdog = object : Runnable {
        override fun run() {
            if (scrolling == 0 && settling == 0) return
            if (SystemClock.uptimeMillis() - scrollActivity < 1500) { handler.postDelayed(this, 300); return }
            Log.w(TAG, "scroll state stuck (scrolling=$scrolling settling=$settling), reset")
            scrolling = 0; settling = 0
            refresh()
            drainKeys()
        }
    }

    /** > 0 while the frame shows a predicted position and the content is still catching up. */
    private var settling = 0

    /** Polls [probe]'s position until it is unchanged for a moment, then shows the frame again. */
    private fun awaitSettle(probe: AccessibilityNodeInfo?, expect: Rect?, then: (() -> Unit)?, shown: Boolean) {
        handler.removeCallbacks(scrollDone)
        val start = SystemClock.uptimeMillis()
        var last: Rect? = null
        var stable = 0
        val poll = object : Runnable {
            override fun run() {
                val r = if (probe != null && probe.refresh()) Rect().also { probe.getBoundsInScreen(it) } else null
                stable = if (r != null && r == last) stable + 1 else 0
                last = r
                val waited = SystemClock.uptimeMillis() - start
                // node gone (scrolled away/recycled): fall back to a fixed settle time
                // standing still is only trusted near the expected position (the app may not have
                // started moving yet), elsewhere only after a while
                // (one side may be cut off by the screen edge: either corner matching is enough)
                val near = r != null && expect != null &&
                    (kotlin.math.abs(r.left - expect.left) + kotlin.math.abs(r.top - expect.top) < 40 ||
                        kotlin.math.abs(r.right - expect.right) + kotlin.math.abs(r.bottom - expect.bottom) < 40)
                val done = if (r == null) waited >= 350
                    else (stable >= SETTLE_POLLS && (near || expect == null || waited >= 700)) || waited >= 1500
                if (!done) { handler.postDelayed(this, POLL_MS); return }
                scrollActivity = SystemClock.uptimeMillis()
                if (shown) { if (settling > 0) settling-- } else if (scrolling > 0) scrolling--
                if (then != null) then()
                if (prefs.debug) Log.d(TAG, "settled after ${waited}ms total ${SystemClock.uptimeMillis() - keyAt}ms probe=${r?.toShortString()} -> $lastAction sel=${selRect?.toShortString()}")
                if (scrolling == 0) refresh() else updateOverlay()
                drainKeys()
            }
        }
        handler.postDelayed(poll, POLL_MS)
    }

    private fun beginScroll() {
        scrolling++
        scrollActivity = SystemClock.uptimeMillis()
        handler.removeCallbacks(scrollWatchdog)
        handler.postDelayed(scrollWatchdog, 500)
        handler.removeCallbacks(scrollDone)
        updateOverlay()
    }

    /** Show the frame again once the list stopped moving (short settle time after the gesture). */
    private fun endScroll(settleMs: Long = 220) {
        handler.removeCallbacks(scrollDone)
        handler.postDelayed(scrollDone, settleMs)
    }

    private fun hideSelection() { selRect = null; selNode = null; updateOverlay() }

    private fun ensureOverlay() {
        if (overlay != null) return
        val view = Overlay(this)
        runCatching { windowManager.addView(view, Overlay.params()) }.onSuccess { overlay = view }
            .onFailure { Log.w(TAG, "overlay failed", it) }
    }

    private fun removeOverlay() {
        overlay?.let { runCatching { windowManager.removeView(it) } }
        overlay = null
    }

    private fun updateOverlay() {
        val o = overlay ?: return
        o.debug = prefs.debug
        o.candidates = nav.targets.map { it.rect }
        o.scrollables = nav.scrollables.map { it.rect }
        o.status = listOf(
            "pkg: $activePkg  active: ${isActive()}",
            "targets: ${nav.targets.size}  scroll: ${nav.scrollables.size}",
            "sel: ${selRect?.toShortString() ?: "-"} ${selId ?: ""}",
            "key: $lastKey",
            "action: $lastAction",
        )
        o.selection = if (scrolling > 0) null else (selVisible?.takeIf { !it.isEmpty } ?: selRect)
    }

    private fun showMessage(text: String) {
        val o = overlay ?: return
        o.message = text; o.invalidate()
        handler.postDelayed({ o.message = null; o.invalidate() }, 1800)
    }

    private fun screenRect(): Rect {
        val m = resources.displayMetrics
        return Rect(0, 0, m.widthPixels, m.heightPixels).also {
            if (Build.VERSION.SDK_INT >= 30) it.set(windowManager.maximumWindowMetrics.bounds)
        }
    }

    /** [r] lies in [dir] of [from] (by center). */
    private fun isBeyond(r: Rect, from: Rect, dir: Dir) = when (dir) {
        Dir.DOWN -> r.centerY() > from.centerY() + 10
        Dir.UP -> r.centerY() < from.centerY() - 10
        Dir.RIGHT -> r.centerX() > from.centerX() + 10
        Dir.LEFT -> r.centerX() < from.centerX() - 10
    }

    private fun d2(a: Rect, b: Rect): Long {
        val dx = (a.centerX() - b.centerX()).toLong(); val dy = (a.centerY() - b.centerY()).toLong()
        return dx * dx + dy * dy
    }

    private fun describe(t: Target) = (t.label ?: t.id?.substringAfterLast('/') ?: t.node.className?.toString()?.substringAfterLast('.') ?: "?").take(30)

    /** Checked/selected state of the node or a descendant (radio button inside a list row). */
    private fun subChecked(n: AccessibilityNodeInfo, d: Int = 0): Boolean {
        if (n.isChecked || n.isSelected) return true
        if (d >= 3) return false
        for (i in 0 until n.childCount) { val c = n.getChild(i) ?: continue; if (subChecked(c, d + 1)) return true }
        return false
    }

    private fun dump() {
        fun walk(n: AccessibilityNodeInfo, d: Int) {
            val r = Rect(); n.getBoundsInScreen(r)
            n.rangeInfo?.let { ri -> Log.i(TAG, "  ".repeat(d) + "RANGE type=${ri.type} min=${ri.min} max=${ri.max} cur=${ri.current} actions=${n.actionList.map { it.id }}") }
            Log.i(TAG, "  ".repeat(d) + "${n.className?.toString()?.substringAfterLast('.')} id=${n.viewIdResourceName} " +
                "text=${n.text?.take(30)} desc=${n.contentDescription?.take(30)} click=${n.isClickable} " +
                "focus=${n.isFocusable} scroll=${n.isScrollable} vis=${n.isVisibleToUser} $r")
            for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, d + 1) }
        }
        windows.forEach { w -> Log.i(TAG, "WINDOW type=${w.type} title=${w.title}"); w.root?.let { walk(it, 1) } }
        collect()
        Log.i(TAG, "STATE scrolling=$scrolling settling=$settling sel=${selRect?.toShortString()} frame=${overlay?.selection?.toShortString()}")
        nav.targets.forEach { Log.i(TAG, "TARGET ${describe(it)} rect=${it.rect.toShortString()} vis=${it.visible.toShortString()} container=${it.container?.rect?.toShortString()} h=${it.container?.horizontal} checked=${it.node.isChecked} selected=${it.node.isSelected} sub=${subChecked(it.node)}") }
    }

    companion object {
        const val TAG = "NavHelper"
        private const val REPEAT_DELAY_MS = 400L
        private val PLAY_WORDS = listOf("abspielen", "fortsetzen", "play", "resume")
        private val PLAY_PAUSE_IDS = setOf("pauseButtonTestTag", "playButtonTestTag")
        private const val SEEK_BACK = Navigator.SEEK_BACK
        private const val SEEK_FWD = Navigator.SEEK_FWD
        const val ACTION_PREFIX = "dev.adbremote.navhelper."
        private const val LONG_PRESS_MS = 600L
        private const val SCROLL_SETTLE_MS = 350L
    }
}
