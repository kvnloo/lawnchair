package app.lawnchair.zer0.voice.z0live

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/** No event contents are retained. Screen access requires a live session and owner consent. */
class Z0LiveAccessibilityService : AccessibilityService() {
    private var consent = false
    private var snapshot = ""
    private var observed = emptyList<Target>()
    private val observer = Z0LiveStatus.Observer { if (!it.active) revoke() }

    private data class Target(
        val text: String,
        val id: String,
        val clickable: Boolean,
        val editable: Boolean,
        val scrollable: Boolean,
        val safe: Boolean,
    )

    override fun onServiceConnected() {
        instance = this
        Z0LiveStatus.observe(observer)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        snapshot = ""
        observed = emptyList()
    }

    override fun onInterrupt() = revoke()

    override fun onUnbind(intent: Intent?): Boolean {
        revoke()
        instance = null
        Z0LiveStatus.remove(observer)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        revoke()
        if (instance === this) instance = null
        Z0LiveStatus.remove(observer)
        super.onDestroy()
    }

    private fun revoke() {
        consent = false
        snapshot = ""
        observed = emptyList()
    }

    private fun describe(node: AccessibilityNodeInfo): Target {
        val text = if (node.isEditable) "" else node.text?.take(1001)?.toString().orEmpty().ifBlank { node.contentDescription?.take(1001)?.toString().orEmpty() }
        val id = node.viewIdResourceName.orEmpty()
        return Target(
            text,
            id,
            node.isClickable,
            node.isEditable,
            node.isScrollable,
            node.isVisibleToUser && node.isEnabled && text.length <= 120 && id.length <= 200 &&
                !Z0LiveAccessibilityPolicy.sensitiveTarget(text + " " + node.hintText?.take(1001)?.toString().orEmpty(), id),
        )
    }

    private fun runTool(name: String, args: JSONObject): JSONObject {
        if (!consent || !Z0LiveStatus.get().active) return permissionRequired()
        val root = rootInActiveWindow ?: return result("unavailable")
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        try {
            val pkg = root.packageName?.toString().orEmpty()
            val window = root.windowId
            val inspection = name == "phone_ui_tree"
            val admission = Z0LiveAccessibilityPolicy.admission(
                true,
                consent,
                if (inspection) pkg else args.getString("package"),
                if (inspection) window else args.getInt("window_id"),
                pkg,
                window,
            )
            if (admission != "allowed") return result(admission)
            if (!inspection && (snapshot.isEmpty() || snapshot != args.getString("snapshot"))) return result("stale_window")
            val queue = ArrayDeque<AccessibilityNodeInfo>()
            queue.add(root)
            var sensitive = false
            var tooLarge = false
            try {
                while (queue.isNotEmpty()) {
                    val node = queue.removeFirst()
                    nodes.add(node)
                    if (nodes.size > 200) {
                        tooLarge = true
                        break
                    }
                    if (node.packageName?.toString() != pkg || node.windowId != window) {
                        sensitive = true
                        break
                    }
                    if (Z0LiveAccessibilityPolicy.passwordField(node.isPassword, node.inputType)) {
                        sensitive = true
                        break
                    }
                    for (i in 0 until node.childCount) {
                        if (queue.size + nodes.size >= 201) {
                            tooLarge = true
                            break
                        }
                        node.getChild(i)?.let { queue.add(it) }
                    }
                }
            } finally {
                queue.forEach { if (it !== root) it.recycle() }
            }
            if (sensitive) return result("sensitive_window")
            if (tooLarge) return result("tree_too_large")
            val targets = nodes.map { describe(it) }
            if (inspection) {
                snapshot = UUID.randomUUID().toString()
                observed = targets
                val entries = JSONArray()
                targets.forEach { target ->
                    if (target.safe && (target.clickable || target.editable || target.scrollable) && entries.length() < 60) {
                        // Never return an editable field's current value.
                        entries.put(
                            JSONObject().put("text", if (target.editable) "" else target.text.take(120))
                                .put("id", target.id.take(200)).put("clickable", target.clickable)
                                .put("editable", target.editable).put("scrollable", target.scrollable),
                        )
                    }
                }
                return result("ui_tree", true).put("package", pkg).put("window_id", window)
                    .put("snapshot", snapshot).put("nodes", entries).put("truncated", targets.count { it.safe && (it.clickable || it.editable || it.scrollable) } > 60)
            }
            if (targets != observed) return result("stale_window")
            val index = Z0LiveAccessibilityPolicy.select(
                targets.map { if (it.editable) "" else it.text }.toTypedArray(),
                targets.map { it.id }.toTypedArray(),
                targets.map { it.safe }.toBooleanArray(),
                args.getString("by"),
                args.getString("target"),
            )
            if (index < 0) {
                return result(
                    when (index) {
                        -2 -> "ambiguous"
                        -3 -> "sensitive_target"
                        else -> "not_found"
                    },
                )
            }
            val target = targets[index]
            val node = nodes[index]
            val action = when (name) {
                "phone_tap" -> {
                    if (!target.clickable) return result("not_actionable")
                    AccessibilityNodeInfo.ACTION_CLICK
                }

                "phone_type_text" -> {
                    if (!target.editable) return result("not_actionable")
                    AccessibilityNodeInfo.ACTION_SET_TEXT
                }

                "phone_scroll" -> {
                    if (!target.scrollable) return result("not_actionable")
                    when (args.getString("direction")) {
                        "forward" -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                        "backward" -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                        else -> return result("invalid_argument")
                    }
                }

                else -> return result("invalid_argument")
            }
            // Recheck the active window immediately before dispatch, after selection.
            val current = rootInActiveWindow ?: return result("stale_window")
            try {
                if (current.packageName?.toString() != pkg || current.windowId != window || !node.refresh()) return result("stale_window")
                if (node.packageName?.toString() != pkg || node.windowId != window || describe(node) != target) return result("stale_window")
                if (Z0LiveAccessibilityPolicy.passwordField(node.isPassword, node.inputType)) return result("sensitive_window")
            } finally {
                current.recycle()
            }
            snapshot = ""
            observed = emptyList()
            val accepted = if (name == "phone_type_text") {
                val text = args.getString("text")
                if (text.isBlank() || text.length > 1000 || text.any { it == '\u0000' || it == '\r' || it == '\n' }) return result("invalid_argument")
                node.performAction(action, Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) })
            } else {
                node.performAction(action)
            }
            return result(if (accepted) "dispatched" else "unavailable", accepted)
                .put("verified", false)
        } finally {
            nodes.forEach { if (it !== root) it.recycle() }
            root.recycle()
        }
    }

    companion object {
        private var instance: Z0LiveAccessibilityService? = null

        private fun result(status: String, ok: Boolean = false) = JSONObject().put("ok", ok).put("status", status)

        private fun permissionRequired() = result("permission_required")
            .put("instruction", "Enable z0live in Settings > Accessibility, start a voice session, then allow in-app control for this session in z0live settings. Settings only open when you choose them.")

        fun sessionAllowed() = instance?.let { it.consent && Z0LiveStatus.get().active } == true

        fun allowCurrentSession(enabled: Boolean): Boolean {
            val service = instance ?: return false
            service.revoke()
            service.consent = enabled && Z0LiveStatus.get().active
            return service.consent
        }

        fun execute(name: String, args: JSONObject): JSONObject {
            val service = instance ?: return permissionRequired()
            if (!service.consent || !Z0LiveStatus.get().active) return permissionRequired()
            if (name == "phone_navigation") {
                val action = when (args.getString("action")) {
                    "back" -> GLOBAL_ACTION_BACK
                    "home" -> GLOBAL_ACTION_HOME
                    "recents" -> GLOBAL_ACTION_RECENTS
                    "notifications" -> GLOBAL_ACTION_NOTIFICATIONS
                    else -> return result("invalid_argument")
                }
                val accepted = service.performGlobalAction(action)
                service.snapshot = ""
                service.observed = emptyList()
                return result(if (accepted) "dispatched" else "unavailable", accepted).put("verified", false)
            }
            return service.runTool(name, args)
        }
    }
}
