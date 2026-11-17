package io.github.rateforx.t9tv.ime

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class KeyLayoutTest {

    /** Guards every bundled layout file: a broken file fails the build instead of the keyboard. */
    @Test
    fun everyBundledLayoutIsValid() {
        val ids = TestLayouts.ids()
        assertTrue("expected bundled layouts, found $ids", ids.containsAll(listOf("pl", "en")))
        for (id in ids) {
            val layout = TestLayouts.load(id) // throws on invalid content
            assertFalse("$id: blank name", layout.name.isBlank())
            for (web in listOf(false, true)) for (key in 0..9) {
                val cycle = layout.cycles(web)[key]
                assertEquals("$id key $key must end with its digit", '0' + key, cycle.last())
                assertEquals("$id key $key upper/lower length", cycle.length, layout.upperCycles(web)[key].length)
            }
            for (key in 2..9) assertTrue("$id key $key has no letters", layout.cycles(false)[key].length > 1)
        }
    }

    @Test
    fun caseLabelsComeFromKeyTwo() {
        assertArrayEquals(arrayOf("abc", "Abc", "ABC"), TestLayouts.load("pl").caseLabels)
    }

    @Test
    fun upperCaseUsesTheLayoutLocale() {
        val tr = KeyLayout("tr", "Türkçe", Locale.forLanguageTag("tr"), mapOf(4 to "ghıi"))
        assertEquals("GHIİ4", tr.upperCycles(false)[4])
    }

    @Test
    fun webKeysOverrideOnlyTheirKeys() {
        val layout = LayoutParser.parse("x", """{"name":"X","keys":{"1":".,","2":"ab"},"webKeys":{"1":"@."}}""")
        assertEquals(".,1", layout.cycles(false)[1])
        assertEquals("@.1", layout.cycles(true)[1])
        assertEquals("ab2", layout.cycles(true)[2])
        assertEquals("3", layout.cycles(false)[3]) // missing key = digit only
    }

    @Test(expected = IllegalArgumentException::class)
    fun digitsInAKeyAreRejected() {
        LayoutParser.parse("x", """{"name":"X","keys":{"2":"abc2"}}""")
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicateCharactersAreRejected() {
        LayoutParser.parse("x", """{"name":"X","keys":{"2":"aba"}}""")
    }

    @Test(expected = IllegalArgumentException::class)
    fun unknownKeysAreRejected() {
        LayoutParser.parse("x", """{"name":"X","keys":{"*":"abc"}}""")
    }
}
