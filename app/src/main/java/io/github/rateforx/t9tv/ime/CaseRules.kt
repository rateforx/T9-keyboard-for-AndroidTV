package io.github.rateforx.t9tv.ime

/**
 * Sentence-case rule for fields that don't request capitalisation themselves
 * (fields with CAP_* flags are asked via `InputConnection.getCursorCapsMode`).
 * Pure Kotlin (unit-testable on the JVM).
 */
object CaseRules {
    private const val SENTENCE_END = ".!?"

    /** How much text before the cursor [isSentenceStart] needs. */
    const val LOOKBEHIND = 3

    /**
     * True when a letter typed after [before] (the text before the cursor) starts a sentence:
     * at the start of the field or a line, or after `.`, `!` or `?` followed by a space.
     * Null (text unavailable) counts as not a sentence start.
     */
    fun isSentenceStart(before: CharSequence?): Boolean {
        if (before == null) return false
        if (before.isEmpty() || before.last() == '\n') return true
        if (before.last() != ' ') return false
        val trimmed = before.trimEnd()
        return trimmed.isEmpty() || trimmed.last() in SENTENCE_END
    }
}
