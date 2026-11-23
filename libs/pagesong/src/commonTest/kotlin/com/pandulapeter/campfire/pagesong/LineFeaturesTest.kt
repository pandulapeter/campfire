/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.pagesong

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The line rules on every platform's regular expression engine, against what `tools/page-model/lines.py` answers for the
 * same lines (the fixture page of `export.py`), since only the desktop runs the golden file's test.
 */
class LineFeaturesTest {

    @Test
    fun `each line reads as the trainer reads it as a chord row or tab or heading or labelled value or address`() {
        for ((text, expected) in rows) {
            val features = lineFeaturesOf(PageLine(text), PageLineSource.PAGE)
            val actual = listOf(2, 3, 4, 5, 9).joinToString("") { if (features[it] == 1f) "X" else "." }

            assertEquals(expected, actual, text)
        }
    }

    @Test
    fun `lengths and letters are counted in code points without format characters`() {
        val features = lineFeaturesOf(PageLine("⁠𠀀😀 a"), PageLineSource.PAGE)

        assertEquals(4 / 200f, features[6])
        assertEquals(2 / 4f, features[7])
    }

    /** Each line, then the trainer's answers in the order the test reads them: chord row, tab, heading, labelled, address. */
    private val rows = listOf(
        "Home  Songs  Artists  Log in" to ".....",
        "Accept all cookies" to ".....",
        "" to ".....",
        "Amazing Grace chords by John Newton" to ".....",
        "Key: G   Capo: 2" to "...X.",
        "[Verse 1]" to "..X..",
        "G           G7        C        G" to "X....",
        "Amazing grace, how sweet the sound" to ".....",
        "      G               D" to "X....",
        "That saved a wretch like me" to ".....",
        "" to ".....",
        "e|-----0-----3-----|" to ".X...",
        "B|---1---1-------3-|" to ".X...",
        "Chorus:" to "..X..",
        "Refrén" to "..X..",
        "Ref." to "..X..",
        "Strofa 2" to "..X..",
        "C x32010   Am x02210" to "X....",
        "Árvíztűrő tükörfúrógép" to ".....",
        "שלום עולם" to ".....",
        "Žluťoučký kůň úpěl ďábelské ódy" to ".....",
        "Zażółć gęślą jaźń" to ".....",
        "\u2060G   \u2060D" to "X....",
        "Em7b5  D#maj7  Bb/F" to "X....",
        "https://example.com/song" to "....X",
        "Report a correction" to ".....",
        "Words & Music: John Newton" to ".....",
        "Tempo: 90 bpm" to "...X.",
        "    indented line" to ".....",
        "\uD83D\uDE00 emoji line \uD83C\uDFB8" to ".....",
        "||: G / / D :|| (x2) N.C. nc % . 4x |" to "X....",
        "Do Re Mi Fa Sol La Si Ré/Sol" to "X....",
        "Cis Fes Hmaj7 Asus4 Bbadd9 C° Bø7 D+ F#m7(b5) A/C#" to "X....",
        "G  Dm  keyboard" to ".....",
        "VERSE 2:" to "..X..",
        "(Pre-Chorus)" to "..X..",
        "[PRECHORUS 2]" to "..X..",
        "Ritornello:" to "..X..",
        "R." to "..X..",
        "V.1" to "..X..",
        "\u0131ntro" to "..X..",
        "\u0130NTRO" to "..X..",
        "[Chorus                                      ]" to ".....",
        "Intro: G D" to ".....",
        "CAPOTRASTE: 3" to "...X.",
        "Kapodaszter = 1" to "...X.",
        "keyboard: Yamaha" to ".....",
        "key2: x" to ".....",
        "Written by - Someone" to "...X.",
        "Słowa: ktoś" to "...X.",
        "Keyé: x" to ".....",
        "Tonalité : Ré" to "...X.",
        "\u212Aey: A" to "...X.",
        "www.campfire.org" to "....X",
        "site.community" to ".....",
        "visit example.com." to "....X",
        "example.comé" to ".....",
        "HTTP://EXAMPLE" to ".....",
        "A|--2--2--0--2--|" to ".X...",
        "D#|-------5h7p5--|" to ".X...",
        "|-0-0-0-0-0-0-|" to ".X...",
        "e|0 1 2 3 4 5 6 7|" to ".....",
        "Ve\u200Brse 1" to "..X..",
        "G\u00A0\u00A0D" to "X....",
        "\u3000Chorus" to "..X..",
        "G\u2003Am" to "X....",
        "\u001FG D" to "X....",
        "\uD840\uDC00\uD840\uDC01 CJK" to ".....",
        "\uD83C\uDFF4\uDB40\uDC67\uDB40\uDC62\uDB40\uDC73\uDB40\uDC63\uDB40\uDC74\uDB40\uDC7F flag" to ".....",
        "\u00AD" to ".....",
        "   " to ".....",
        "\u0009\u0009G\u0009D" to "X....",
    )
}
