package io.github.rateforx.t9tv.ime

import java.util.Locale

/**
 * A keypad layout: the multi-tap character cycle of every key 0–9, loaded from
 * `assets/layouts/<id>.json` (see [LayoutParser]). The digit itself is appended as the last
 * element of every cycle. Upper-case cycles use the layout's [locale] (e.g. Turkish ı/İ).
 * Pure Kotlin (unit-testable on the JVM).
 */
class KeyLayout(
    val id: String,
    val name: String,
    val locale: Locale,
    keys: Map<Int, String>,
    /** Overrides for e-mail and URL fields (typically key 1 starting with @ . /). */
    webKeys: Map<Int, String> = emptyMap(),
) {
    private val text: Array<String>
    private val textUpper: Array<String>
    private val web: Array<String>
    private val webUpper: Array<String>

    /** Labels of the lower / sentence / upper case modes, derived from key 2 (e.g. abc, Abc, ABC). */
    val caseLabels: Array<String>

    init {
        require(id.isNotBlank()) { "layout id must not be blank" }
        require(keys.keys.all { it in 0..9 } && webKeys.keys.all { it in 0..9 }) { "layout '$id': keys must be 0-9" }
        text = Array(10) { cycleFor(it, keys[it]) }
        web = Array(10) { cycleFor(it, webKeys[it] ?: keys[it]) }
        textUpper = Array(10) { upper(text[it]) }
        webUpper = Array(10) { upper(web[it]) }
        val sample = keys[2].orEmpty().take(3).ifEmpty { "abc" }.lowercase(locale)
        caseLabels = arrayOf(sample, sample.replaceFirstChar { it.titlecase(locale) }, sample.uppercase(locale))
    }

    /** Lower-case cycles (index = key). */
    fun cycles(web: Boolean): Array<String> = if (web) this.web else text

    /** Upper-case cycles (index = key), same lengths as [cycles]. */
    fun upperCycles(web: Boolean): Array<String> = if (web) webUpper else textUpper

    private fun cycleFor(key: Int, chars: String?): String {
        val c = chars.orEmpty()
        require(c.none { it in '0'..'9' }) { "layout '$id', key $key: \"$c\" must not contain digits (they are added automatically)" }
        require(c.none { it.isSurrogate() }) { "layout '$id', key $key: only single-char (BMP) characters are supported" }
        require(c.toSet().size == c.length) { "layout '$id', key $key: duplicate characters in \"$c\"" }
        return c + ('0' + key)
    }

    /** Upper-cases every character on its own, keeping the cycle length (ß stays ß instead of SS). */
    private fun upper(cycle: String): String = buildString(cycle.length) {
        for (ch in cycle) {
            val u = ch.toString().uppercase(locale)
            append(if (u.length == 1) u[0] else ch)
        }
    }

    override fun toString() = "KeyLayout($id, $name)"

    companion object {
        /** Emergency layout (digits only) used if no layout file could be loaded. */
        fun digitsOnly() = KeyLayout("digits", "123", Locale.ROOT, emptyMap())
    }
}
