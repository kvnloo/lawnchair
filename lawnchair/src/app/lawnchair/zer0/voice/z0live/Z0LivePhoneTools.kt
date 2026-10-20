package app.lawnchair.zer0.voice.z0live

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Process
import android.provider.AlarmClock
import android.provider.Settings
import android.view.KeyEvent
import app.lawnchair.zer0.voice.VoiceAppVisibility
import org.json.JSONArray
import org.json.JSONObject

/** Session-scoped phone actions. Screen access is opt-in; sending and calling require the owner. */
object Z0LivePhoneTools {
    private fun obj(vararg values: Pair<String, Any?>) = JSONObject().apply {
        values.forEach { (key, value) -> put(key, value ?: JSONObject.NULL) }
    }

    private val settingsPages = mapOf(
        "wifi" to Settings.ACTION_WIFI_SETTINGS,
        "bluetooth" to Settings.ACTION_BLUETOOTH_SETTINGS,
        "display" to Settings.ACTION_DISPLAY_SETTINGS,
        "sound" to Settings.ACTION_SOUND_SETTINGS,
        "apps" to Settings.ACTION_APPLICATION_SETTINGS,
        "battery" to Settings.ACTION_BATTERY_SAVER_SETTINGS,
        "accessibility" to Settings.ACTION_ACCESSIBILITY_SETTINGS,
        "notifications" to Settings.ACTION_NOTIFICATION_SETTINGS,
        "location" to Settings.ACTION_LOCATION_SOURCE_SETTINGS,
        "general" to Settings.ACTION_SETTINGS,
    )

    @JvmStatic
    fun schemas(context: Context): JSONArray = JSONArray().apply {
        fun tool(name: String, description: String, vararg properties: Pair<String, JSONObject>) {
            val fields = JSONObject()
            properties.forEach { (key, value) -> fields.put(key, value) }
            put(
                obj(
                    "type" to "function",
                    "name" to name,
                    "description" to description,
                    "parameters" to obj(
                        "type" to "object",
                        "properties" to fields,
                        "required" to JSONArray(properties.map { it.first }),
                        "additionalProperties" to false,
                    ),
                ),
            )
        }
        fun text() = obj("type" to "string", "minLength" to 1, "maxLength" to 1000)
        fun choice(vararg values: String) = obj("type" to "string", "enum" to JSONArray(values.toList()))
        tool("go_home", "Bring Lawnchair's home screen to the front.")
        tool("open_app", "Open a visible installed app by name. If ambiguous, ask the user to choose one of the returned candidates; never guess.", "name" to text())
        tool("list_apps", "List visible personal-profile launcher apps matching query. Empty query lists the first 50. Does not read app data.", "query" to obj("type" to "string", "maxLength" to 1000))
        tool("search_apps", "Find launcher apps matching query; ask before opening when ambiguous.", "query" to text())
        tool("web_search", "Open a web search in the browser. Does not retrieve or read search results.", "query" to text())
        tool("open_url", "Open an HTTPS or HTTP web URL in the browser; no file, intent or privileged schemes.", "url" to text())
        tool("open_settings", "Open the named Android Settings page; never change settings silently.", "page" to choice(*settingsPages.keys.toTypedArray()))
        tool("set_timer", "Ask the Clock app to create a timer. Clock UI may need confirmation. Never claim timer running from intent dispatch alone.", "seconds" to obj("type" to "integer", "minimum" to 1, "maximum" to 86400))
        tool("set_alarm", "Open Clock with alarm prefilled in local 24-hour time; user may need to confirm.", "hour" to obj("type" to "integer", "minimum" to 0, "maximum" to 23), "minute" to obj("type" to "integer", "minimum" to 0, "maximum" to 59))
        tool("navigate_to", "Open a maps search for a place; do not start navigation silently.", "place" to text())
        tool("start_call", "Open the dialer with a supplied phone number; never place a call or look up contacts.", "number" to text())
        tool("send_text", "Open SMS composer prefilled with number and text. User must tap Send; never auto-send or read messages.", "number" to text(), "text" to text())
        tool("media_control", "Send a public media key to the active media session. Dispatch is not proof media started.", "action" to choice("play", "pause", "next", "previous"))
        tool("flashlight", "Turn the camera torch on/off. Requires optional Camera permission, returns a structured failure if unavailable.", "enabled" to obj("type" to "boolean"))
        tool("volume", "Adjust media volume one step up/down; does not alter voice session routing.", "direction" to choice("up", "down"))
        tool("take_note", "Open the share chooser with a text note; user chooses the destination and saves it.", "text" to text())
        tool("ripple", "Open/close the configured trusted HTTPS Ripple panel. Not a screen-reading or automation tool.", "action" to choice("open", "close"))
        tool("z0live_mute", "Set microphone mute. Does not end the voice session.", "muted" to obj("type" to "boolean"))
        tool("z0live_end", "End the voice session after submitting its summary. No always-on restart.")
        tool("phone_navigation", "Back, home, recents or notifications. Requires owner accessibility enablement and consent for this voice session. Success means dispatched, not verified.", "action" to choice("back", "home", "recents", "notifications"))
        tool("phone_ui_tree", "Read a bounded text-only list of safe controls in the active app after owner consent. Returns package, window_id and snapshot needed for exact selection. Sensitive apps and password windows are blocked. Never request credentials.")
        fun targetProperties() = arrayOf(
            "package" to text(),
            "window_id" to obj("type" to "integer", "minimum" to 0),
            "snapshot" to text(),
            "by" to choice("text", "id"),
            "target" to text(),
        )
        tool("phone_tap", "Tap one exact text or resource id from a fresh phone_ui_tree in that package/window/snapshot. Ambiguity is rejected. Never send, call, purchase, confirm or grant permission; the owner must do those. Success means dispatched only.", *targetProperties())
        tool("phone_type_text", "Replace an editable field using ACTION_SET_TEXT, selected by exact id from a fresh phone_ui_tree. Never enter credentials or submit. Success means dispatched only.", *targetProperties(), "text" to text())
        tool("phone_scroll", "Scroll a single exact text/id target from a fresh phone_ui_tree. No coordinate fallback. Success means dispatched only.", *targetProperties(), "direction" to choice("forward", "backward"))
    }

    @JvmStatic
    fun supports(context: Context, name: String): Boolean = (0 until schemas(context).length()).any {
        schemas(context).getJSONObject(it).getString("name") == name
    }

    private fun string(args: JSONObject, key: String, empty: Boolean = false): String {
        val value = args.get(key)
        require(value is String && value.length <= 1000 && (empty || value.isNotBlank())) { "Invalid $key" }
        require(value.none { it == '\u0000' || it == '\r' || it == '\n' && key != "text" }) { "Invalid $key" }
        return value
    }

    private fun integer(args: JSONObject, key: String, range: IntRange): Int {
        val raw = args.get(key)
        require(raw is Number && raw.toDouble() == raw.toInt().toDouble() && raw.toInt() in range) { "Invalid $key" }
        return raw.toInt()
    }

    private fun launch(context: Context, intent: Intent): JSONObject {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return obj("ok" to true, "status" to "dispatched", "confirmation" to "Android accepted the intent; destination UI may require user confirmation")
    }

    private fun apps(context: Context): List<LauncherActivityInfo> {
        val launcher = context.getSystemService(LauncherApps::class.java)
        return VoiceAppVisibility.visibleApps(context, launcher.getActivityList(null, Process.myUserHandle()))
    }

    private fun candidates(context: Context, query: String): List<LauncherActivityInfo> {
        val all = apps(context).sortedBy { it.label.toString() }
        return Z0LiveAppMatcher.candidates(query, all.map { it.label.toString() }, all.map { it.componentName.packageName }).map { all[it] }
    }

    private fun appResult(matches: List<LauncherActivityInfo>) = obj(
        "ok" to true,
        "status" to "matches",
        "count" to matches.size,
        "apps" to JSONArray(matches.take(50).map { obj("name" to it.label.toString(), "package" to it.componentName.packageName) }),
        "has_more" to (matches.size > 50),
    )

    /** Must execute on the main thread, including Android's public activity-launch APIs. */
    @JvmStatic
    fun execute(context: Context, name: String, args: JSONObject): JSONObject = try {
        require(supports(context, name)) { "Tool not declared or no longer available" }
        val schema = schemas(context).let { array -> (0 until array.length()).map { array.getJSONObject(it) }.first { it.getString("name") == name } }
        val parameters = schema.getJSONObject("parameters")
        val keys = parameters.getJSONObject("properties")
        require(args.keys().asSequence().all { keys.has(it) }) { "Unknown argument" }
        val required = parameters.getJSONArray("required")
        require((0 until required.length()).all { args.has(required.getString(it)) && !args.isNull(required.getString(it)) }) { "Missing argument" }
        when (name) {
            "go_home" -> launch(context, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(context.packageName))

            "list_apps", "search_apps" -> appResult(candidates(context, string(args, "query", true)))

            "open_app" -> {
                val matches = candidates(context, string(args, "name"))
                when {
                    matches.isEmpty() -> obj("ok" to false, "status" to "not_found")

                    matches.size > 1 -> appResult(matches).put("ok", false).put("status", "ambiguous").put("ask_user", true)

                    else -> {
                        val target = matches.single()
                        context.getSystemService(LauncherApps::class.java).startMainActivity(target.componentName, target.user, null, null)
                        obj("ok" to true, "status" to "dispatched", "name" to target.label.toString(), "package" to target.componentName.packageName)
                    }
                }
            }

            "web_search" -> launch(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(string(args, "query")))))

            "open_url" -> {
                val url = Uri.parse(string(args, "url"))
                require(url.scheme in setOf("https", "http") && !url.host.isNullOrBlank() && url.userInfo == null) { "Only web URLs without credentials are supported" }
                launch(context, Intent(Intent.ACTION_VIEW, url))
            }

            "open_settings" -> launch(context, Intent(settingsPages[string(args, "page")] ?: error("Unknown Settings page")))

            "set_timer" -> launch(context, Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, integer(args, "seconds", 1..86400)).putExtra(AlarmClock.EXTRA_SKIP_UI, false))

            "set_alarm" -> launch(context, Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, integer(args, "hour", 0..23)).putExtra(AlarmClock.EXTRA_MINUTES, integer(args, "minute", 0..59)).putExtra(AlarmClock.EXTRA_SKIP_UI, false))

            "navigate_to" -> launch(context, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(string(args, "place")))))

            "start_call", "send_text" -> {
                val number = string(args, "number")
                require(number.matches(Regex("[+0-9 ()-]{1,40}"))) { "Supply a phone number; contacts are not read" }
                if (name == "start_call") {
                    launch(context, Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)))
                } else {
                    launch(context, Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null)).putExtra("sms_body", string(args, "text")))
                }
            }

            "media_control" -> {
                val key = mapOf("play" to KeyEvent.KEYCODE_MEDIA_PLAY, "pause" to KeyEvent.KEYCODE_MEDIA_PAUSE, "next" to KeyEvent.KEYCODE_MEDIA_NEXT, "previous" to KeyEvent.KEYCODE_MEDIA_PREVIOUS)[string(args, "action")] ?: error("Invalid media action")
                val audio = context.getSystemService(AudioManager::class.java)
                audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key))
                audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, key))
                obj("ok" to true, "status" to "dispatched")
            }

            "flashlight" -> {
                require(args.get("enabled") is Boolean) { "enabled must be boolean" }
                if (context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    obj("ok" to false, "status" to "permission_required", "permission" to "camera", "instruction" to "Grant optional Camera permission in z0live settings for torch access")
                } else {
                    val camera = context.getSystemService(CameraManager::class.java)
                    val id = camera.cameraIdList.firstOrNull {
                        camera.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                            camera.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
                    } ?: error("No rear flashlight available")
                    camera.setTorchMode(id, args.getBoolean("enabled"))
                    obj("ok" to true, "status" to "requested", "enabled" to args.getBoolean("enabled"))
                }
            }

            "volume" -> {
                val direction = when (string(args, "direction")) {
                    "up" -> AudioManager.ADJUST_RAISE
                    "down" -> AudioManager.ADJUST_LOWER
                    else -> error("Invalid volume direction")
                }
                val audio = context.getSystemService(AudioManager::class.java)
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
                obj("ok" to true, "status" to "applied", "level" to audio.getStreamVolume(AudioManager.STREAM_MUSIC))
            }

            "take_note" -> launch(context, Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, string(args, "text")), null))

            "ripple" -> when (string(args, "action")) {
                "open" -> {
                    require(Z0LiveStore(context).rippleUrl() != null) { "Configure a trusted Ripple HTTPS URL first" }
                    launch(context, Intent(context, RippleActivity::class.java))
                }

                "close" -> {
                    context.sendBroadcast(Intent("app.lawnchair.z0live.CLOSE_RIPPLE").setPackage(context.packageName))
                    obj("ok" to true, "status" to "dispatched")
                }

                else -> error("Invalid Ripple action")
            }

            "z0live_mute" -> {
                require(args.get("muted") is Boolean) { "muted must be boolean" }
                if (Z0LiveStatus.get().muted != args.getBoolean("muted")) Z0LiveService.toggleMute(context)
                obj("ok" to true, "status" to "requested", "muted" to args.getBoolean("muted"))
            }

            "z0live_end" -> obj("ok" to true, "status" to "ending")

            "phone_navigation", "phone_ui_tree", "phone_tap", "phone_type_text", "phone_scroll" -> {
                if (name != "phone_ui_tree" && name != "phone_navigation") {
                    string(args, "package")
                    integer(args, "window_id", 0..Int.MAX_VALUE)
                    string(args, "snapshot")
                    string(args, "by")
                    string(args, "target")
                    if (name == "phone_type_text") string(args, "text")
                }
                Z0LiveAccessibilityService.execute(name, args)
            }

            else -> error("Unknown tool")
        }
    } catch (exception: Exception) {
        obj("ok" to false, "status" to "failed", "error" to (exception.message?.take(200) ?: exception.javaClass.simpleName))
    }
}
