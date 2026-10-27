package app.lawnchair.zer0.voice.z0live

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject

/**
 * In-app control for z0live. The service already holds the live accessibility tree in-process,
 * so a compact snapshot is one in-process walk (no uiautomator server, no adb). Actions go by the
 * snapshot's stable node id: performAction on the node, else its nearest clickable ancestor, else
 * a gesture tap on its bounds. Each action waits for the app's own accessibility events and a
 * fresh snapshot, then reports landed / not landed with evidence.
 *
 * Owner gates: Android accessibility enablement, the persistent "in-app control" switch in z0live
 * settings, and a live voice session. Taps the phone_goal denylist calls consequential (send,
 * pay, delete, install, allow...) return confirm_required until the owner says yes out loud.
 * No event contents are retained between calls.
 */
class Z0LiveAccessibilityService : AccessibilityService() {
    private val lock = Object()

    @Volatile private var changeSeq = 0L

    @Volatile private var clickSeq = 0L
    private var latest: Captured? = null
    private val observer = Z0LiveStatus.Observer { if (!it.active) clear() }

    private class Captured(val screen: Z0LiveScreen, val infos: List<AccessibilityNodeInfo>, val seq: Long, val ms: Double)

    override fun onServiceConnected() {
        instance = this
        Z0LiveStatus.observe(observer)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.packageName?.toString() == packageName) return
        val type = event.eventType
        if (type == AccessibilityEvent.TYPE_VIEW_CLICKED || type == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED) clickSeq++
        changeSeq++
        synchronized(lock) { lock.notifyAll() }
    }

    override fun onInterrupt() = clear()

    override fun onUnbind(intent: Intent?): Boolean {
        clear()
        instance = null
        Z0LiveStatus.remove(observer)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        clear()
        if (instance === this) instance = null
        Z0LiveStatus.remove(observer)
        super.onDestroy()
    }

    private fun clear() {
        synchronized(lock) { latest = null }
    }

    // ---------------------------------------------------------------- snapshot ----

    @Suppress("DEPRECATION") // isChecked: the tri-state replacement needs API 36; minSdk is 26.
    private fun capture(): Captured? {
        val started = SystemClock.elapsedRealtimeNanos()
        val seq = changeSeq
        val root = rootInActiveWindow ?: return null
        val pkg = root.packageName?.toString().orEmpty()
        val nodes = ArrayList<Z0LiveScreen.Node>()
        val infos = ArrayList<AccessibilityNodeInfo>()
        val stack = ArrayList<Triple<AccessibilityNodeInfo, Int, Int>>()
        stack.add(Triple(root, -1, 0))
        val rect = Rect()
        var visited = 0
        while (stack.isNotEmpty() && visited < MAX_VISITED && nodes.size < Z0LiveScreen.MAX_NODES) {
            val (info, parent, depth) = stack.removeAt(stack.size - 1)
            visited++
            if (!info.isVisibleToUser) continue
            val password = Z0LiveAccessibilityPolicy.passwordField(info.isPassword, info.inputType)
            val editable = info.isEditable
            val text = Z0LiveScreen.readable(info.text?.toString())
            val desc = Z0LiveScreen.readable(info.contentDescription?.toString())
            val hint = Z0LiveScreen.readable(info.hintText?.toString())
            // Never read an editable field's value: its label is the hint or description only.
            val label = when {
                password -> ""
                editable -> hint.ifEmpty { if (desc != text) desc else "" }
                text.isNotEmpty() -> text
                else -> desc
            }.replace('\n', ' ').take(120)
            val keep = label.isNotEmpty() || password || info.isClickable || info.isLongClickable || editable ||
                info.isScrollable || info.isCheckable
            var me = parent
            if (keep) {
                info.getBoundsInScreen(rect)
                val node = Z0LiveScreen.Node()
                node.parent = parent
                node.label = label
                node.viewId = info.viewIdResourceName.orEmpty().take(200)
                node.className = info.className?.toString().orEmpty()
                node.pkg = info.packageName?.toString().orEmpty()
                node.left = rect.left
                node.top = rect.top
                node.right = rect.right
                node.bottom = rect.bottom
                node.clickable = info.isClickable
                node.longClickable = info.isLongClickable
                node.editable = editable
                node.scrollable = info.isScrollable
                node.checkable = info.isCheckable
                node.checked = info.isChecked
                node.selected = info.isSelected
                node.password = password
                node.role = Z0LiveScreen.role(node.className, node.clickable, editable, node.scrollable, node.checkable)
                nodes.add(node)
                infos.add(info)
                me = nodes.size - 1
            }
            if (depth < MAX_DEPTH) {
                for (i in info.childCount - 1 downTo 0) info.getChild(i)?.let { stack.add(Triple(it, me, depth + 1)) }
            }
        }
        val screen = Z0LiveScreen(pkg, nodes)
        val captured = Captured(screen, infos, seq, (SystemClock.elapsedRealtimeNanos() - started) / 1e6)
        synchronized(lock) { latest = captured }
        return captured
    }

    /** Latest snapshot if nothing changed since, else a fresh one. */
    private fun current(): Captured? {
        val cached = synchronized(lock) { latest }
        return if (cached != null && cached.seq == changeSeq) cached else capture()
    }

    private fun screenJson(captured: Captured, limit: Int = TREE_LIMIT): JSONObject = JSONObject()
        .put("package", captured.screen.pkg)
        .put("snapshot", captured.screen.hash)
        .put("nodes", captured.screen.toJson(limit))
        .put("snapshot_ms", round(captured.ms))

    // ------------------------------------------------------------------ actions ----

    private fun blocked(captured: Captured): JSONObject? = if (Z0LiveAccessibilityPolicy.blockedPackage(captured.screen.pkg)) result("blocked_app").put("package", captured.screen.pkg) else null

    /** Snapshot; with `since` equal to the current hash only says unchanged (send on change). */
    private fun tree(args: JSONObject): JSONObject {
        val captured = capture() ?: return result("unavailable")
        blocked(captured)?.let { return it }
        if (args.optString("since") == captured.screen.hash) {
            return result("ui_tree", true).put("package", captured.screen.pkg).put("snapshot", captured.screen.hash)
                .put("unchanged", true).put("snapshot_ms", round(captured.ms))
        }
        return merge(result("ui_tree", true), screenJson(captured))
    }

    /** Tier 0: on-phone lexical choice. Acts when confident, else returns ranked candidates. */
    private fun doGoal(args: JSONObject): JSONObject {
        val started = SystemClock.elapsedRealtimeNanos()
        val goal = args.getString("goal")
        val captured = current() ?: return result("unavailable")
        blocked(captured)?.let { return it }
        val decideStart = SystemClock.elapsedRealtimeNanos()
        val choice = Z0LiveTargetMatcher.choose(captured.screen, goal)
        val decideMs = (SystemClock.elapsedRealtimeNanos() - decideStart) / 1e6
        val decision = JSONObject().put("tier", "phone_lexical").put("query", choice.query)
            .put("confidence", round(choice.confidence)).put("margin", round(choice.margin)).put("ms", round(decideMs))
        if (!choice.confident()) {
            val candidates = JSONArray()
            for (c in choice.ranked.take(CANDIDATES)) {
                val n = captured.screen.nodes[c.index]
                val entry = JSONObject().put("id", n.id).put("l", n.label.take(80)).put("r", n.role).put("score", round(c.score))
                val words = Z0LiveScreen.idWords(n.viewId)
                if (words.isNotEmpty()) entry.put("v", words.take(40))
                candidates.put(entry)
            }
            return merge(result("choose", true), screenJson(captured)).put("goal", goal.take(300))
                .put("candidates", candidates).put("decision", decision)
                .put("ms", JSONObject().put("total", round((SystemClock.elapsedRealtimeNanos() - started) / 1e6)))
        }
        val node = captured.screen.nodes[choice.best()]
        val action = if (node.editable) "focus" else "click"
        return act(captured, node.id, action, null, args.optBoolean("confirm", false), decision, started)
    }

    private fun actById(args: JSONObject): JSONObject {
        val started = SystemClock.elapsedRealtimeNanos()
        val captured = current() ?: return result("unavailable")
        blocked(captured)?.let { return it }
        val text = if (args.has("text")) args.getString("text") else null
        return act(captured, args.getString("id"), args.optString("action", "click"), text, args.optBoolean("confirm", false), null, started)
    }

    private fun act(captured: Captured, id: String, action: String, text: String?, confirm: Boolean, decision: JSONObject?, started: Long): JSONObject {
        val screen = captured.screen
        val index = screen.indexOf(id)
        if (index < 0) return merge(result("not_found"), screenJson(captured)).put("id", id)
        val node = screen.nodes[index]
        val target = JSONObject().put("id", node.id).put("label", node.label).put("role", node.role)
        if (node.password || Z0LiveAccessibilityPolicy.secretField(node.label, node.viewId) && action == "set_text") {
            return result("sensitive_target").put("target", target)
        }
        val reason = Z0LiveAccessibilityPolicy.consequential(node.label, node.checkable, node.pkg, screen.screenText())
        if (reason != null && !confirm) {
            val out = result("confirm_required").put("target", target).put("reason", reason)
                .put("ask", "Ask the owner out loud before tapping '${node.label.take(60)}', then repeat with confirm true.")
            if (decision != null) out.put("decision", decision)
            return out
        }
        val seqBefore = changeSeq
        val clicksBefore = clickSeq
        val actStart = SystemClock.elapsedRealtimeNanos()
        var via = "node"
        val accepted: Boolean = when (action) {
            "click", "long_click" -> {
                val op = if (action == "click") AccessibilityNodeInfo.ACTION_CLICK else AccessibilityNodeInfo.ACTION_LONG_CLICK
                val owner = if (action == "click") screen.clickTarget(index) else index
                if (owner >= 0 && fresh(captured.infos[owner]) && captured.infos[owner].performAction(op)) {
                    via = if (owner == index) "node" else "ancestor"
                    true
                } else {
                    via = "gesture"
                    tap(node, action == "long_click")
                }
            }

            "focus" -> fresh(captured.infos[index]) && (
                captured.infos[index].performAction(AccessibilityNodeInfo.ACTION_CLICK) ||
                    captured.infos[index].performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                )

            "set_text" -> {
                if (!node.editable) return result("not_actionable").put("target", target)
                val value = text.orEmpty()
                if (value.isBlank() || value.length > 1000 || value.any { it == '\u0000' }) return result("invalid_argument")
                fresh(captured.infos[index]) && captured.infos[index].performAction(
                    AccessibilityNodeInfo.ACTION_SET_TEXT,
                    Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value) },
                )
            }

            "scroll_forward", "scroll_backward" -> {
                val op = if (action == "scroll_forward") AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                var i = index
                var hops = 0
                while (i >= 0 && !screen.nodes[i].scrollable && hops++ < 12) i = screen.nodes[i].parent
                if (i < 0 || !screen.nodes[i].scrollable) return result("not_actionable").put("target", target)
                via = if (i == index) "node" else "ancestor"
                fresh(captured.infos[i]) && captured.infos[i].performAction(op)
            }

            else -> return result("invalid_argument")
        }
        val actMs = (SystemClock.elapsedRealtimeNanos() - actStart) / 1e6
        if (!accepted) return result("not_dispatched").put("target", target).put("via", via)
        val verifyStart = SystemClock.elapsedRealtimeNanos()
        val evidence = verify(seqBefore, clicksBefore, action)
        val after = capture()
        val changed = after != null && after.screen.hash != screen.hash
        val landed = changed || evidence == "clicked_event" || evidence == "text_event"
        val out = result("done", true).put("landed", landed).put("via", via).put("target", target)
            .put("evidence", if (changed) "screen_changed" else evidence)
            .put("changed", changed)
            .put(
                "ms",
                JSONObject().put("snapshot", round(captured.ms)).put("act", round(actMs))
                    .put("confirm", round((SystemClock.elapsedRealtimeNanos() - verifyStart) / 1e6))
                    .put("total", round((SystemClock.elapsedRealtimeNanos() - started) / 1e6)),
            )
        if (decision != null) out.put("decision", decision)
        if (after != null) {
            out.put("package", after.screen.pkg).put("snapshot", after.screen.hash)
            if (changed) out.put("appeared", JSONArray(after.screen.appeared(screen, 6)))
        }
        return out
    }

    private fun fresh(info: AccessibilityNodeInfo): Boolean = try {
        info.refresh()
    } catch (_: RuntimeException) {
        false
    }

    private fun tap(node: Z0LiveScreen.Node, long: Boolean): Boolean {
        if (node.right <= node.left || node.bottom <= node.top) return false
        val path = Path().apply { moveTo(node.centerX().toFloat(), node.centerY().toFloat()) }
        val stroke = GestureDescription.StrokeDescription(path, 0, if (long) 650 else 40)
        return dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    /**
     * Wait (<= VERIFY_MS) for the app's first own event after an action, then let the burst
     * settle for at most SETTLE_CAP_MS so the after-snapshot shows the result, not a frame of
     * the animation. Animations that never go quiet are cut off at the cap.
     */
    private fun verify(seqBefore: Long, clicksBefore: Long, action: String): String {
        val deadline = SystemClock.uptimeMillis() + VERIFY_MS
        synchronized(lock) {
            while (changeSeq == seqBefore) {
                val left = deadline - SystemClock.uptimeMillis()
                if (left <= 0) return "none"
                lock.wait(left)
            }
        }
        val settleEnd = minOf(deadline, SystemClock.uptimeMillis() + SETTLE_CAP_MS)
        var seen = changeSeq
        while (SystemClock.uptimeMillis() < settleEnd) {
            synchronized(lock) { lock.wait(SETTLE_MS) }
            if (changeSeq == seen) break
            seen = changeSeq
        }
        return when {
            clickSeq > clicksBefore -> "clicked_event"
            action == "set_text" -> "text_event"
            else -> "content_event"
        }
    }

    private fun navigation(args: JSONObject): JSONObject {
        val action = when (args.getString("action")) {
            "back" -> GLOBAL_ACTION_BACK
            "home" -> GLOBAL_ACTION_HOME
            "recents" -> GLOBAL_ACTION_RECENTS
            "notifications" -> GLOBAL_ACTION_NOTIFICATIONS
            else -> return result("invalid_argument")
        }
        val before = changeSeq
        if (!performGlobalAction(action)) return result("not_dispatched")
        val evidence = verify(before, clickSeq, "navigation")
        return result("done", true).put("landed", evidence != "none").put("evidence", evidence)
    }

    companion object {
        private const val MAX_VISITED = 1500
        private const val MAX_DEPTH = 48
        private const val TREE_LIMIT = 60
        private const val CANDIDATES = 8
        private const val VERIFY_MS = 250L
        private const val SETTLE_MS = 30L
        private const val SETTLE_CAP_MS = 110L
        private const val PREFS = "z0live_control"
        private const val KEY_ALLOWED = "in_app_control"

        @Volatile private var instance: Z0LiveAccessibilityService? = null

        /** Accessibility work runs here so it can wait for events, which arrive on main. */
        private val worker = Executors.newSingleThreadExecutor { Thread(it, "z0live-a11y").apply { isDaemon = true } }

        private fun result(status: String, ok: Boolean = false) = JSONObject().put("ok", ok).put("status", status)

        private fun round(value: Double) = Math.round(value * 10.0) / 10.0

        private fun merge(into: JSONObject, from: JSONObject): JSONObject {
            from.keys().forEach { into.put(it, from.get(it)) }
            return into
        }

        @JvmStatic
        fun enabled() = instance != null

        @JvmStatic
        fun controlAllowed(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ALLOWED, false)

        /** Owner-only: set from the z0live settings consent dialog. Persists across sessions. */
        @JvmStatic
        fun setControlAllowed(context: Context, allowed: Boolean) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ALLOWED, allowed).apply()
            instance?.clear()
        }

        /** Which owner step is missing, or null when tools may run. */
        @JvmStatic
        fun missingStep(context: Context): String? = when {
            instance == null -> "service_disabled"
            !controlAllowed(context) -> "control_off"
            !Z0LiveStatus.get().active -> "no_session"
            else -> null
        }

        private fun permissionRequired(step: String) = result("permission_required").put("missing", step)
            .put(
                "instruction",
                when (step) {
                    "service_disabled" -> "Turn on z0live in Settings > Accessibility > Installed apps. If Android says the setting is restricted, open App info > menu > Allow restricted settings first."
                    "control_off" -> "Turn on In-app control once in z0live settings; it stays on."
                    else -> "Start a z0live voice session first."
                },
            )

        /** Blocks the caller (never the main thread) for at most a few hundred milliseconds. */
        @JvmStatic
        fun execute(context: Context, name: String, args: JSONObject): JSONObject {
            missingStep(context)?.let { return permissionRequired(it) }
            return run(name, args)
        }

        /** adb-only latency probe (Z0LiveControlProbe, DUMP-guarded): no voice-session gate. */
        @JvmStatic
        fun probe(name: String, args: JSONObject): JSONObject = run(name, args)

        private fun run(name: String, args: JSONObject): JSONObject {
            val service = instance ?: return permissionRequired("service_disabled")
            val task = worker.submit<JSONObject> {
                when (name) {
                    "phone_navigation" -> service.navigation(args)
                    "phone_ui_tree" -> service.tree(args)
                    "phone_do" -> service.doGoal(args)
                    "phone_act" -> service.actById(args)
                    else -> result("invalid_argument")
                }
            }
            return try {
                task.get(5, TimeUnit.SECONDS)
            } catch (exception: Exception) {
                task.cancel(true)
                result("failed").put("error", exception.javaClass.simpleName)
            }
        }
    }
}
