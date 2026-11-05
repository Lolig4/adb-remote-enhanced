package dev.adbremote.navhelper

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.abs
import kotlin.math.max

enum class Dir { UP, DOWN, LEFT, RIGHT }

/** A focusable target on screen. */
class Target(val node: AccessibilityNodeInfo, var rect: Rect, val container: Target? = null) {
    /** Cut off by the screen edge or a fixed bar -> scrolled fully into view when selected. */
    var clipped = false
    /** Unclipped bounds. */
    var full: Rect = Rect(rect)
    /** True size from the bounds in the parent (View UIs report it unclipped), 0 = unknown. */
    var trueW = 0
    var trueH = 0
    /** Part not covered by fixed bars (empty when fully covered); the frame is drawn around it. */
    var visible: Rect = Rect(rect)

    /** Nearest vertical scroll container (a row's list), null for fixed UI. */
    val verticalScroller: Target?
        get() { var c = container; while (c != null && c.horizontal) c = c.container; return c }
    val horizontalScroller: Target? get() = container?.takeIf { it.horizontal }
    /** Orientation from the item layout inside (set by [Navigator.collect]); null = unknown. */
    var layoutHorizontal: Boolean? = null

    /**
     * Horizontal scroll container (e.g. a Netflix row)? Decided by the app's collection info
     * (rows × columns), then by how the items inside are laid out (side by side = horizontal),
     * then by the scroll actions, then: a scroller inside another one scrolls the other way.
     */
    val horizontal: Boolean by lazy {
        val ci = node.collectionInfo
        val ids = node.actionList.map { it.id }
        when {
            ci != null && ci.rowCount == 1 && ci.columnCount > 1 -> true
            ci != null && ci.columnCount == 1 && ci.rowCount > 1 -> false
            layoutHorizontal != null -> layoutHorizontal!!
            android.R.id.accessibilityActionScrollRight in ids || android.R.id.accessibilityActionScrollLeft in ids -> true
            android.R.id.accessibilityActionScrollDown in ids || android.R.id.accessibilityActionScrollUp in ids -> false
            container != null -> !container.horizontal
            else -> rect.width() > rect.height() * 3
        }
    }
    val id: String? get() = node.viewIdResourceName
    val label: String?
        get() = ((node.contentDescription ?: node.text)?.toString() ?: composeLabel(node)
            ?: if (isPlayerSeekbar(node)) "Fortschritt" else null)?.take(40)
}

/**
 * Netflix player buttons (Compose, test tag "...ButtonTestTag") carry their label on a child:
 * the description of the first labeled descendant.
 */
internal fun composeLabel(node: AccessibilityNodeInfo): String? {
    if (node.viewIdResourceName?.endsWith("ButtonTestTag") != true) return null
    fun find(n: AccessibilityNodeInfo, d: Int): String? {
        for (i in 0 until n.childCount) {
            val c = n.getChild(i) ?: continue
            c.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { return it }
            if (d < 3) find(c, d + 1)?.let { return it }
        }
        return null
    }
    return find(node, 0)
}

/** The progress bar of the Netflix player (a SeekBar in ms inside "playerSeekbar"). */
internal fun isPlayerSeekbar(node: AccessibilityNodeInfo): Boolean =
    node.className == "android.widget.SeekBar" && node.parent?.viewIdResourceName == "playerSeekbar"

/**
 * Collects clickable targets / scroll containers from the window tree and picks the best target in
 * a direction (spatial navigation like Android's FocusFinder: in-beam candidates first, then the
 * nearest by 13·major² + minor²).
 */
class Navigator {

    var targets: List<Target> = emptyList(); private set
    var scrollables: List<Target> = emptyList(); private set
    /** Netflix video player: its full-screen tap area (shows/hides the controls), else null. */
    var player: AccessibilityNodeInfo? = null; private set
    /** Player buttons that are clicked by key, never selected (10 s back/forward), by test tag. */
    val playerKeys = HashMap<String, AccessibilityNodeInfo>()

    fun collect(roots: List<AccessibilityNodeInfo>, screen: Rect) {
        val found = ArrayList<Target>()
        val scroll = ArrayList<Target>()
        val screenArea = screen.width().toLong() * screen.height()
        player = null
        playerKeys.clear()
        for (root in roots) walk(root, screen, found, scroll, 0, null)
        // drop full-screen containers (>= 95 % of the screen; a Netflix billboard has ~92 %)
        val small = found.filter { it.rect.width().toLong() * it.rect.height() < screenArea * 95 / 100 }
        val chosen = removeOccluded(small.ifEmpty { found }, screen)
        estimateClipped(chosen, screen)
        val seen = HashSet<String>()
        targets = chosen.filter { seen.add(it.rect.flattenToString()) }
        scrollables = scroll
        // orientation hint: items of a row share one band, a list has items in several bands
        for (sc in scroll) {
            val all = found.filter { t -> var c = t.container; while (c != null && c.node != sc.node) c = c.container; c != null }
            // only card-sized items (small buttons inside cards would look like extra bands)
            val hs = all.map { it.rect.height() }.sorted()
            val typical = if (hs.isEmpty()) 0 else hs[hs.size / 2]
            val items = all.filter { it.rect.height() >= typical / 2 && it.rect.height() <= typical * 2 }
            if (items.size < 2) continue
            val bands = items.map { (it.rect.centerY()) / maxOf(1, items.minOf { i -> i.rect.height() } / 2) }.toSet()
            sc.layoutHorizontal = bands.size == 1
        }
    }

    private fun walk(node: AccessibilityNodeInfo, screen: Rect, out: MutableList<Target>, scroll: MutableList<Target>, depth: Int, container: Target?) {
        if (depth > 60 || !node.isVisibleToUser) return
        val r = Rect()
        node.getBoundsInScreen(r)
        val clipped = !screen.contains(r)
        val full = Rect(r)
        if (!r.intersect(screen) || r.width() < 8 || r.height() < 8) return
        var inner = container
        if (node.isScrollable) { inner = Target(node, Rect(r), container); scroll.add(inner) }
        val clickable = node.isClickable ||
            node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK }
        if (node.viewIdResourceName == PLAYER_TAP_AREA) player = node
        else if (node.viewIdResourceName in PLAYER_KEY_BUTTONS) playerKeys[node.viewIdResourceName] = node
        else if (isPlayerSeekbar(node)) out.add(Target(node, Rect(r), container))
        else if (clickable && node.isEnabled && !ignored(node)) out.add(Target(node, Rect(r), container).also {
            it.clipped = clipped; it.full = full
            @Suppress("DEPRECATION") val pb = Rect().also { b -> node.getBoundsInParent(b) }
            it.trueW = pb.width(); it.trueH = pb.height()
        })
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            walk(child, screen, out, scroll, depth + 1, inner)
        }
    }

    /**
     * Netflix elements that are never navigation targets: unlabeled ones, the trailer controls,
     * rating and share buttons.
     */
    private fun ignored(node: AccessibilityNodeInfo): Boolean {
        // own label only: a container that just holds a text (Netflix info badge) is not a target
        val label = ((node.contentDescription ?: node.text)?.toString() ?: composeLabel(node))?.trim()
        if (label.isNullOrEmpty()) return true
        val l = label.lowercase()
        return IGNORE_WORDS.any { it in l } || IGNORE_PREFIXES.any { l.startsWith(it) }
    }

    /** Text/description of the node or (for containers) of its first labeled descendant. */
    private fun labelOf(node: AccessibilityNodeInfo, depth: Int): String? {
        (node.contentDescription ?: node.text)?.toString()?.takeIf { it.isNotBlank() }?.let { return it }
        if (depth >= 3) return null
        for (i in 0 until node.childCount) {
            val c = node.getChild(i) ?: continue
            labelOf(c, depth + 1)?.let { return it }
        }
        return null
    }

    /**
     * Apps (e.g. Compose) report only the visible part of a partly scrolled-out item. An item at the
     * edge of its scroll container that is clearly smaller than its siblings is cut off: estimate
     * its full bounds from the typical sibling size.
     */
    private fun estimateClipped(all: List<Target>, screen: Rect) {
        // typical item size over all rows: a row cut at the screen edge has no intact item itself
        val inRows = all.filter { it.container != null && it.container.horizontal }
        val allH = inRows.map { it.rect.height() }.sorted()
        val allW = inRows.map { it.rect.width() }.sorted()
        val globalH = if (allH.isEmpty()) 0 else allH[allH.size * 3 / 4]
        val globalW = if (allW.isEmpty()) 0 else allW[allW.size * 3 / 4]
        for ((container, items) in inRows.groupBy { it.container!!.node }) {
            val c = items.first().container!!.rect
            val widths = items.map { it.rect.width() }.sorted()
            val heights = items.map { it.rect.height() }.sorted()
            val w = maxOf(widths.last(), if (items.size < 2) globalW else widths[widths.size / 2])
            val h = maxOf(heights.last(), if (heights.last() < globalH * 6 / 10) globalH else heights[heights.size / 2])
            for (t in items) {
                val f = Rect(t.full)
                // exact size known (View UI): extend the cut side to it, no guessing
                if (t.trueH > t.rect.height() + 2 || t.trueW > t.rect.width() + 2) {
                    if (t.trueH > t.rect.height() + 2) {
                        if (t.rect.top <= screen.top + 2 || (t.rect.top <= c.top + 2 && t.rect.bottom < c.bottom - 2)) f.top = f.bottom - t.trueH
                        else f.bottom = f.top + t.trueH
                    }
                    if (t.trueW > t.rect.width() + 2) {
                        if (t.rect.left <= screen.left + 2 || (t.rect.left <= c.left + 2 && t.rect.right < c.right - 2)) f.left = f.right - t.trueW
                        else f.right = f.left + t.trueW
                    }
                    t.full = f; t.clipped = true
                    continue
                }
                if (t.rect.width() < w * 9 / 10) {
                    if (t.rect.right >= c.right - 2) f.right = f.left + w else if (t.rect.left <= c.left + 2) f.left = f.right - w
                }
                if (t.rect.height() < h * 9 / 10) {
                    // cut at the screen edge it touches (the container itself may be cut as well)
                    if (t.rect.top <= screen.top + 2) f.top = f.bottom - h
                    else if (t.rect.bottom >= screen.bottom - 2) f.bottom = f.top + h
                    else if (t.rect.bottom >= c.bottom - 2) f.bottom = f.top + h else if (t.rect.top <= c.top + 2) f.top = f.bottom - h
                }
                if (t.rect.width() < w * 9 / 10 && (t.rect.left <= screen.left + 2 || t.rect.right >= screen.right - 2)) {
                    if (t.rect.left <= screen.left + 2) f.left = f.right - w else f.right = f.left + w
                }
                if (f != t.full) { t.full = f; t.clipped = true }
            }
        }
    }

    /**
     * Scrolled content can lie under fixed bars (toolbar, tab bar). Rows of fixed targets are such
     * bars: cut them out of scrolled targets and drop targets that are mostly hidden.
     */
    private fun removeOccluded(all: List<Target>, screen: Rect): List<Target> {
        val fixed = all.filter { it.container == null }
        val bands = ArrayList<IntArray>()
        for (f in fixed.sortedBy { it.rect.top }) {
            val sameRow = fixed.count { it.rect.top < f.rect.bottom && it.rect.bottom > f.rect.top }
            if (sameRow < 2 && f.rect.width() < screen.width() / 2) continue
            val b = bands.firstOrNull { it[0] < f.rect.bottom && it[1] > f.rect.top }
            if (b != null) { b[0] = minOf(b[0], f.rect.top); b[1] = maxOf(b[1], f.rect.bottom) } else bands.add(intArrayOf(f.rect.top, f.rect.bottom))
        }
        if (bands.isEmpty()) return all
        // covered items stay navigation targets (geometry uses the real bounds); they are only
        // marked, and scrolled out from under the bar when selected
        for (t in all) {
            if (t.container == null) continue
            val r = Rect(t.rect)
            var covered = false
            for (b in bands) {
                if (b[0] >= r.bottom || b[1] <= r.top) continue
                if (b[0] <= r.top && b[1] >= r.bottom) { covered = true; break }
                when {
                    b[0] <= r.top -> r.top = b[1]
                    b[1] >= r.bottom -> r.bottom = b[0]
                    b[0] - r.top >= r.bottom - b[1] -> r.bottom = b[0]
                    else -> r.top = b[1]
                }
            }
            t.visible = if (covered) Rect() else r
            if (covered || r != t.rect) t.clipped = true
        }
        return all
    }

    /** Target matching [rect] / [id] best (to keep the selection after the screen changed). */
    fun find(rect: Rect, id: String?, node: AccessibilityNodeInfo? = null): Target? {
        if (targets.isEmpty()) return null
        // the same element (node identity survives scrolling, also without a view id)
        if (node != null) targets.firstOrNull { it.node == node }?.let { return it }
        // a unique id follows the element when it moved (scrolled rows); the position is only a fallback
        val sameId = if (id != null) targets.filter { it.id == id } else emptyList()
        if (sameId.size == 1) return sameId[0]
        targets.firstOrNull { it.rect == rect && (id == null || it.id == id) }?.let { return it }
        val pool = sameId.ifEmpty { targets }
        val best = pool.minByOrNull { dist2(it.rect, rect) } ?: return null
        // only accept a nearby match; far away means the old target is gone
        return if (dist2(best.rect, rect) < 200L * 200L || sameId.isNotEmpty()) best else null
    }

    /** Targets lying inside [outer] (e.g. the buttons of a billboard). */
    fun inside(outer: Target): List<Target> =
        targets.filter { it !== outer && it.rect != outer.rect && it.container?.node == outer.container?.node && outer.rect.contains(it.rect) }

    /** Smallest target that contains [inner] (its clickable parent area), if any. */
    fun around(inner: Target): Target? =
        targets.filter { it !== inner && it.rect != inner.rect && it.container?.node == inner.container?.node && it.rect.contains(inner.rect) }
            .minByOrNull { it.rect.width().toLong() * it.rect.height() }

    /** First target in reading order (initial selection). */
    fun first(): Target? = targets.minWithOrNull(compareBy<Target>({ it.rect.top / 40 }, { it.rect.left }))

    fun nearest(rect: Rect): Target? = targets.minByOrNull { dist2(it.rect, rect) }

    /**
     * Best target from [from] in [dir]. Left/right stay in the row (in-beam only, like TV focus);
     * with [sameContainer] only targets of that scroll container count (after scrolling it).
     */
    fun next(from: Rect, dir: Dir, exclude: Target? = null, sameContainer: AccessibilityNodeInfo? = null): Target? {
        var best: Target? = null
        var bestScore = Long.MAX_VALUE
        val horizontal = dir == Dir.LEFT || dir == Dir.RIGHT
        var bestOut: Target? = null
        var bestOutScore = Long.MAX_VALUE
        for (t in targets) {
            if (t === exclude || t.rect == from || !isCandidate(from, t.rect, dir)) continue
            val inBeam = beamOverlap(from, t.rect, dir)
            if (horizontal && !inBeam) continue
            if (sameContainer != null && t.container?.node != sameContainer) continue
            val major = majorDistance(from, t.rect, dir).toLong()
            val minor = minorDistance(from, t.rect, dir).toLong()
            val score = 13 * major * major + minor * minor
            if (inBeam) { if (score < bestScore) { bestScore = score; best = t } }
            else if (score < bestOutScore) { bestOutScore = score; bestOut = t }
        }
        // up/down: an in-column target wins, unless an off-column one is much nearer (a band in between)
        if (best == null) return bestOut
        if (bestOut != null && majorDistance(from, bestOut.rect, dir) * 2 < majorDistance(from, best.rect, dir)) return bestOut
        return best
    }

    /** Smallest scroll container around [rect] that can scroll in [dir]'s axis. */
    fun scrollableFor(rect: Rect, dir: Dir): Target? {
        val cx = rect.centerX(); val cy = rect.centerY()
        val around = scrollables.filter { it.rect.contains(cx, cy) }
        val horizontal = dir == Dir.LEFT || dir == Dir.RIGHT
        // prefer containers whose shape matches the axis (rows scroll horizontally)
        val matching = around.filter { it.horizontal == horizontal }
        return (matching.ifEmpty { around }).minByOrNull { it.rect.width().toLong() * it.rect.height() }
    }

    private fun isCandidate(src: Rect, dst: Rect, dir: Dir) = when (dir) {
        Dir.RIGHT -> (src.right < dst.right || src.left >= dst.right) && src.left < dst.left
        Dir.LEFT -> (src.left > dst.left || src.right <= dst.left) && src.right > dst.right
        Dir.DOWN -> (src.bottom < dst.bottom || src.top >= dst.bottom) && src.top < dst.top
        Dir.UP -> (src.top > dst.top || src.bottom <= dst.top) && src.bottom > dst.bottom
    }

    private fun beamOverlap(src: Rect, dst: Rect, dir: Dir) = when (dir) {
        Dir.LEFT, Dir.RIGHT -> dst.bottom > src.top && dst.top < src.bottom
        Dir.UP, Dir.DOWN -> dst.right > src.left && dst.left < src.right
    }

    private fun majorDistance(src: Rect, dst: Rect, dir: Dir) = max(0, when (dir) {
        Dir.RIGHT -> dst.left - src.right
        Dir.LEFT -> src.left - dst.right
        Dir.DOWN -> dst.top - src.bottom
        Dir.UP -> src.top - dst.bottom
    })

    private fun minorDistance(src: Rect, dst: Rect, dir: Dir) = when (dir) {
        Dir.LEFT, Dir.RIGHT -> abs(src.centerY() - dst.centerY())
        Dir.UP, Dir.DOWN -> abs(src.centerX() - dst.centerX())
    }

    private fun dist2(a: Rect, b: Rect): Long {
        val dx = (a.centerX() - b.centerX()).toLong(); val dy = (a.centerY() - b.centerY()).toLong()
        return dx * dx + dy * dy
    }

    companion object {
        const val PLAYER_TAP_AREA = "playerTappableContainerTestTag"
        const val SEEK_BACK = "backwardSeekButtonTestTag"
        const val SEEK_FWD = "forwardSeekButtonTestTag"
        val PLAYER_KEY_BUTTONS = setOf(SEEK_BACK, SEEK_FWD)
        private val IGNORE_WORDS = listOf(
            "steuerelemente anzeigen", "show controls", "steuerelemente sperren", "lock controls", "lock screen", "vorschau abspielen", "play preview", "stumm", "lautstärke", "mute", "volume",
            "bewertung", "bewerten", "gefällt mir", "daumen", "rate ", "rating", "like this", "teilen", "share", "wird geladen", "loading",
        )
        /** Cast / creator lines on the detail page. */
        private val IGNORE_PREFIXES = listOf("mit:", "regie:", "drehbuch:", "von:", "starring:", "cast:", "creators:", "director:")
        private const val OUT_OF_BEAM_PENALTY = 4_000_000_000L
    }
}
