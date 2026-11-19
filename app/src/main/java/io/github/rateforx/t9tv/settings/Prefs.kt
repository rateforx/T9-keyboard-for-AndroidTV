package io.github.rateforx.t9tv.settings

import android.content.Context

/** Where the keyboard panel sits on the screen: -1/0/+1 = left/center/right and top/center/bottom. */
enum class PanelPosition(val horizontal: Int, val vertical: Int) {
    BOTTOM_RIGHT(1, 1),
    BOTTOM_CENTER(0, 1),
    BOTTOM_LEFT(-1, 1),
    TOP_LEFT(-1, -1),
    TOP_CENTER(0, -1),
    TOP_RIGHT(1, -1),
    CENTER(0, 0),
}

/**
 * User settings. The setup screen and the keyboard run in one process and share the same
 * SharedPreferences instance, so the keyboard sees a change on the next text field it starts on.
 */
class Prefs(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** Id of the chosen key layout (file name in `assets/layouts/`), or null for the default. */
    var layoutId: String?
        get() = prefs.getString(KEY_LAYOUT, null)
        set(value) = prefs.edit().putString(KEY_LAYOUT, value).apply()

    /** How long a multi-tap letter stays pending before it is confirmed. */
    var timeoutMs: Int
        get() = prefs.getInt(KEY_TIMEOUT, DEFAULT_TIMEOUT_MS).let { if (it in TIMEOUTS_MS) it else DEFAULT_TIMEOUT_MS }
        set(value) = prefs.edit().putInt(KEY_TIMEOUT, value).apply()

    var panelPosition: PanelPosition
        get() = prefs.getString(KEY_POSITION, null)
            ?.let { name -> PanelPosition.entries.firstOrNull { it.name == name } }
            ?: PanelPosition.BOTTOM_RIGHT
        set(value) = prefs.edit().putString(KEY_POSITION, value.name).apply()

    companion object {
        /** The choices offered on the setup screen. */
        val TIMEOUTS_MS = intArrayOf(600, 800, 1000, 1300, 1600, 2000)
        const val DEFAULT_TIMEOUT_MS = 1000

        private const val NAME = "settings"
        private const val KEY_LAYOUT = "layout"
        private const val KEY_TIMEOUT = "timeout_ms"
        private const val KEY_POSITION = "panel_position"
    }
}
