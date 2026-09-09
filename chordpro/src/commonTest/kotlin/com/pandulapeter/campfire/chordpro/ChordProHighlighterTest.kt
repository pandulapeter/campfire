package com.pandulapeter.campfire.chordpro

import com.pandulapeter.campfire.chordpro.ChordProHighlighter.TokenType
import kotlin.test.Test
import kotlin.test.assertEquals

class ChordProHighlighterTest {

    /** The text each token covers, which is what a reader of a highlighting bug actually wants to see. */
    private fun spans(text: String) = ChordProHighlighter.tokenize(text).map { it.type to text.substring(it.start, it.end) }

    @Test
    fun `directive is split into its name and its value`() {
        assertEquals(
            listOf(TokenType.DIRECTIVE_NAME to "{title:", TokenType.DIRECTIVE_VALUE to " Song"),
            spans("{title: Song}")
        )
    }

    @Test
    fun `directive without a value is all name`() {
        assertEquals(listOf(TokenType.DIRECTIVE_NAME to "{start_of_chorus}"), spans("{start_of_chorus}"))
    }

    @Test
    fun `directive with an empty value is all name`() {
        // There is no value text to colour differently yet, so the whole thing reads as one directive.
        assertEquals(listOf(TokenType.DIRECTIVE_NAME to "{key: }"), spans("{key: }"))
    }

    @Test
    fun `chords and annotations are told apart`() {
        assertEquals(
            listOf(TokenType.CHORD to "[Am]", TokenType.ANNOTATION to "[*softly]", TokenType.CHORD to "[G/B]"),
            spans("[Am]word [*softly] more [G/B]end")
        )
    }

    @Test
    fun `hash lines are comments, wherever they are indented to`() {
        assertEquals(listOf(TokenType.COMMENT to "  # not sung [Am]"), spans("  # not sung [Am]"))
    }

    @Test
    fun `brackets inside a tab are left alone`() {
        val text = "{start_of_tab}\ne|--[3]--|\n{end_of_tab}\n[Am]after"
        assertEquals(
            listOf(
                TokenType.DIRECTIVE_NAME to "{start_of_tab}",
                TokenType.DIRECTIVE_NAME to "{end_of_tab}",
                TokenType.CHORD to "[Am]"
            ),
            spans(text)
        )
    }

    @Test
    fun `offsets are absolute across lines`() {
        val text = "one\n[Am]two"
        assertEquals(listOf(ChordProHighlighter.Token(TokenType.CHORD, 4, 8)), ChordProHighlighter.tokenize(text))
    }

    @Test
    fun `an unfinished line still highlights what it has`() {
        // Whatever is being typed has to look like what it is becoming, so a missing brace is not a reason to stop.
        assertEquals(listOf(TokenType.CHORD to "[Am]"), spans("{title: half typed\n[Am]word"))
    }

    @Test
    fun `windows line endings do not shift the offsets`() {
        assertEquals(
            listOf(TokenType.DIRECTIVE_NAME to "{title:", TokenType.DIRECTIVE_VALUE to " Song", TokenType.CHORD to "[Am]"),
            spans("{title: Song}\r\n[Am]word")
        )
    }

    @Test
    fun `empty text has nothing to highlight`() {
        assertEquals(emptyList(), ChordProHighlighter.tokenize(""))
    }
}
