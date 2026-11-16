package com.goodbrainrot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Règle du rappel de soutien : 10e lancement, puis tous les 100, jamais après « Ne plus me le demander ». */
class SupportPromptTest {
    @Test
    fun tenth_launch_shows_the_prompt() {
        assertFalse(shouldShowSupportPrompt(9, false))
        assertTrue(shouldShowSupportPrompt(10, false))
    }

    @Test
    fun eleventh_launch_does_not_show_it() {
        assertFalse(shouldShowSupportPrompt(11, false))
    }

    @Test
    fun every_hundredth_launch_shows_the_prompt() {
        assertFalse(shouldShowSupportPrompt(99, false))
        assertTrue(shouldShowSupportPrompt(100, false))
        assertFalse(shouldShowSupportPrompt(101, false))
        assertFalse(shouldShowSupportPrompt(199, false))
        assertTrue(shouldShowSupportPrompt(200, false))
    }

    @Test
    fun a_thousand_launches_show_eleven_prompts() {
        // le 10e, puis 100, 200 … 1000 ; ni le 20e ni le 110e
        assertEquals(listOf(10) + (100..1000 step 100), (0..1000).filter { shouldShowSupportPrompt(it, false) })
    }

    @Test
    fun opt_out_suppresses_every_prompt() {
        assertTrue((0..1000).none { shouldShowSupportPrompt(it, true) })
    }
}
