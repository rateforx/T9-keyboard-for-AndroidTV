package io.github.rateforx.t9tv.ime

import android.content.Context
import android.util.Log
import java.util.Locale

/** Loads the bundled layouts from `assets/layouts/` and remembers the selected one. */
class LayoutRepository(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private var cache: List<KeyLayout>? = null

    /** All valid bundled layouts, sorted by name. Invalid files are logged and skipped. */
    fun all(): List<KeyLayout> = cache ?: load().also { cache = it }

    /** The layout chosen in settings, else the one matching the system language, else the first one. */
    fun selected(): KeyLayout {
        val layouts = all()
        if (layouts.isEmpty()) return KeyLayout.digitsOnly()
        val id = prefs.getString(PREF_LAYOUT, null)
        return layouts.firstOrNull { it.id == id }
            ?: layouts.firstOrNull { it.locale.language == Locale.getDefault().language }
            ?: layouts.first()
    }

    fun select(layout: KeyLayout) {
        prefs.edit().putString(PREF_LAYOUT, layout.id).apply()
    }

    private fun load(): List<KeyLayout> {
        val assets = appContext.assets
        val files = assets.list(DIR).orEmpty().filter { it.endsWith(".json") }
        return files.mapNotNull { file ->
            try {
                val json = assets.open("$DIR/$file").bufferedReader().use { it.readText() }
                LayoutParser.parse(file.removeSuffix(".json"), json)
            } catch (e: Exception) {
                Log.e(TAG, "Skipping invalid layout $DIR/$file", e)
                null
            }
        }.sortedBy { it.name }
    }

    private companion object {
        const val TAG = "T9IME"
        const val DIR = "layouts"
        const val PREFS = "settings"
        const val PREF_LAYOUT = "layout"
    }
}
