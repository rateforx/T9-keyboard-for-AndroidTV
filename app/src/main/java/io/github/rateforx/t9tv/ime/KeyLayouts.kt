package io.github.rateforx.t9tv.ime

/** Character cycles of the 12-key keypad for multi-tap input. Pure Kotlin (unit-testable on the JVM). */
object KeyLayouts {
    private val LATIN = arrayOf("", "", "abc", "def", "ghi", "jkl", "mno", "pqrs", "tuv", "wxyz")
    private val POLISH = arrayOf("", "", "abcąć", "defę", "ghi", "jklł", "mnoóń", "pqrsś", "tuv", "wxyzźż")

    private const val PUNCT_TEXT = ".,?!'\"-():;/@_"
    private const val PUNCT_WEB_FIRST = "@./:_-"
    private val PUNCT_WEB = PUNCT_WEB_FIRST + PUNCT_TEXT.filterNot { it in PUNCT_WEB_FIRST }

    /**
     * The full cycle of [key] (0–9). The digit itself is always the last element.
     * [web] puts e-mail/URL characters first on key 1.
     */
    fun cycle(key: Int, polish: Boolean, web: Boolean): String = when (key) {
        0 -> " 0"
        1 -> (if (web) PUNCT_WEB else PUNCT_TEXT) + '1'
        in 2..9 -> (if (polish) POLISH else LATIN)[key] + ('0' + key)
        else -> throw IllegalArgumentException("key must be 0-9, was $key")
    }

    fun cycles(polish: Boolean, web: Boolean): Array<String> = Array(10) { cycle(it, polish, web) }
}
