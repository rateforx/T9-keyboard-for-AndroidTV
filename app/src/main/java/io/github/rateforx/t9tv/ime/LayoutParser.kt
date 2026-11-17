package io.github.rateforx.t9tv.ime

import org.json.JSONObject
import java.util.Locale

/**
 * Parses a layout file from `assets/layouts/<id>.json`:
 *
 * ```
 * {
 *   "name": "Polski",              // shown in settings, in the language itself
 *   "locale": "pl",                // BCP 47 tag used for upper case (default: the file id)
 *   "keys": { "1": ".,?!", "2": "abcąć", …, "0": " " },   // characters only, no digits
 *   "webKeys": { "1": "@./:_-" }   // optional overrides for e-mail / URL fields
 * }
 * ```
 */
object LayoutParser {

    fun parse(id: String, json: String): KeyLayout {
        val root = JSONObject(json)
        return KeyLayout(
            id = id,
            name = root.getString("name"),
            locale = Locale.forLanguageTag(root.optString("locale", id)),
            keys = readKeys(id, root.getJSONObject("keys")),
            webKeys = root.optJSONObject("webKeys")?.let { readKeys(id, it) } ?: emptyMap(),
        )
    }

    private fun readKeys(id: String, obj: JSONObject): Map<Int, String> {
        val map = HashMap<Int, String>()
        for (name in obj.keys()) {
            val key = name.toIntOrNull()
            require(key != null && key in 0..9) { "layout '$id': unknown key \"$name\" (expected \"0\"-\"9\")" }
            map[key] = obj.getString(name)
        }
        return map
    }
}
