package io.github.rateforx.t9tv.ime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaseRulesTest {

    @Test
    fun startOfFieldAndLineStartASentence() {
        assertTrue(CaseRules.isSentenceStart(""))
        assertTrue(CaseRules.isSentenceStart("ok\n"))
    }

    @Test
    fun sentenceEndFollowedBySpaceStartsASentence() {
        assertTrue(CaseRules.isSentenceStart("ok. "))
        assertTrue(CaseRules.isSentenceStart("k? "))
        assertTrue(CaseRules.isSentenceStart("!  "))
    }

    @Test
    fun middleOfASentenceIsNotAStart() {
        assertFalse(CaseRules.isSentenceStart("ab "))
        assertFalse(CaseRules.isSentenceStart("abc"))
        assertFalse(CaseRules.isSentenceStart("ok.")) // e.g. "3.5" or "www.x"
        assertFalse(CaseRules.isSentenceStart("a, "))
    }

    @Test
    fun unknownTextIsNotAStart() {
        assertFalse(CaseRules.isSentenceStart(null))
    }
}
