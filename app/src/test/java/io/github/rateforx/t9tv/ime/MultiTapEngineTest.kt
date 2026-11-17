package io.github.rateforx.t9tv.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiTapEngineTest {
    private val pl = TestLayouts.load("pl")
    private val engine = MultiTapEngine(pl)

    private fun pressN(key: Int, n: Int, upper: Boolean = false): List<Char> =
        List(n) { engine.press(key, upper) }

    @Test
    fun repeatedPressesCycleThroughLettersThenDigitAndWrap() {
        assertEquals(listOf('a', 'b', 'c', 'ą', 'ć', '2', 'a'), pressN(2, 7))
    }

    @Test
    fun commitReturnsPendingCharAndClearsState() {
        pressN(4, 2)
        assertTrue(engine.hasPending)
        assertEquals('h', engine.commit())
        assertFalse(engine.hasPending)
        assertNull(engine.commit())
    }

    @Test
    fun englishLayoutHasNoPolishLetters() {
        engine.setLayout(TestLayouts.load("en"), web = false)
        assertEquals(listOf('a', 'b', 'c', '2', 'a'), pressN(2, 5))
    }

    @Test
    fun upperCaseIsCapturedAtCycleStart() {
        assertEquals('A', engine.press(2, upperCase = true))
        assertEquals('B', engine.press(2, upperCase = false))
        assertEquals('C', engine.press(2, upperCase = false))
        assertEquals('Ą', engine.press(2, upperCase = false))
    }

    @Test
    fun polishLettersOnTheirKeys() {
        assertEquals('ł', pressN(5, 4).last())
        engine.cancel()
        assertEquals('ń', pressN(6, 5).last())
        engine.cancel()
        assertEquals('ż', pressN(9, 6).last())
    }

    @Test
    fun zeroIsSpaceThenDigit() {
        assertEquals(listOf(' ', '0', ' '), pressN(0, 3))
    }

    @Test
    fun longPressOnFirstPressGivesTheDigit() {
        engine.press(5, false)
        assertEquals("5", engine.longPress(5))
        assertFalse(engine.hasPending)
    }

    @Test
    fun longPressAfterCyclingKeepsThePreviousLetter() {
        engine.press(5, false) // j
        engine.press(5, false) // k — this press is held down
        assertEquals("j5", engine.longPress(5))
        assertFalse(engine.hasPending)
    }

    @Test
    fun longPressWithoutPendingGivesTheDigit() {
        assertEquals("7", engine.longPress(7))
    }

    @Test
    fun pressingAnotherKeyStartsAFreshCycle() {
        engine.press(2, false)
        assertEquals('d', engine.press(3, false))
        assertEquals(3, engine.activeKey)
        assertEquals(0, engine.activeIndex)
    }

    @Test
    fun webPunctuationStartsWithAt() {
        engine.setLayout(pl, web = true)
        assertEquals(listOf('@', '.', '/'), pressN(1, 3))
    }

    @Test
    fun textPunctuationStartsWithPeriod() {
        assertEquals(listOf('.', ',', '?'), pressN(1, 3))
    }
}
