package io.github.rateforx.t9tv.ime

/**
 * Multi-tap state machine with at most one pending (not yet committed) character.
 *
 * The caller mirrors the pending character in the editor (as composing text), commits it
 * on timeout via [commit], and must [commit] before pressing a different key.
 * Pure Kotlin (unit-testable on the JVM).
 */
class MultiTapEngine(layout: KeyLayout) {

    /** Lower-case cycle of every key (index = key); the digit is the last element. */
    var cycles: Array<String> = layout.cycles(web = false)
        private set

    /** Upper-case counterpart of [cycles]. */
    var upperCycles: Array<String> = layout.upperCycles(web = false)
        private set

    /** Key (0–9) of the pending character, or -1 when nothing is pending. */
    var activeKey = -1
        private set

    /** Index of the pending character within the active key's cycle. */
    var activeIndex = 0
        private set

    /** Whether the pending cycle is upper case (captured when the cycle started). */
    var activeUpper = false
        private set

    private var previousIndex = -1

    val hasPending: Boolean get() = activeKey >= 0

    /** Switches the layout; [web] selects the e-mail/URL variant. Drops a pending character. */
    fun setLayout(layout: KeyLayout, web: Boolean) {
        reset()
        cycles = layout.cycles(web)
        upperCycles = layout.upperCycles(web)
    }

    /**
     * Pressing the pending key advances its cycle; any other key starts a new cycle
     * (a different pending key is dropped, so commit it first). [upperCase] applies to
     * the whole new cycle and is ignored while cycling. Returns the new pending character.
     */
    fun press(key: Int, upperCase: Boolean): Char {
        require(key in 0..9) { "key must be 0-9, was $key" }
        if (key == activeKey) {
            previousIndex = activeIndex
            activeIndex = (activeIndex + 1) % cycles[key].length
        } else {
            activeKey = key
            activeIndex = 0
            previousIndex = -1
            activeUpper = upperCase
        }
        return charAt(activeIndex)
    }

    /**
     * Long press of [key]: the press that turned into the long press is replaced by the digit.
     * Returns the text that should replace the pending (composing) character; nothing stays pending.
     */
    fun longPress(key: Int): String {
        val digit = ('0' + key).toString()
        if (key != activeKey) return digit
        val before = if (previousIndex >= 0) charAt(previousIndex).toString() else ""
        reset()
        return before + digit
    }

    /** Commits the pending character. Returns it, or null when nothing was pending. */
    fun commit(): Char? {
        if (!hasPending) return null
        val c = charAt(activeIndex)
        reset()
        return c
    }

    fun cancel() = reset()

    private fun charAt(index: Int): Char = (if (activeUpper) upperCycles else cycles)[activeKey][index]

    private fun reset() {
        activeKey = -1
        activeIndex = 0
        previousIndex = -1
        activeUpper = false
    }
}
