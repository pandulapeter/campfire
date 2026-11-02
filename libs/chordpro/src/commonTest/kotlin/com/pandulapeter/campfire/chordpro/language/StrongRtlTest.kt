/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.language

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StrongRtlTest {

    @Test
    fun `the letters of every right-to-left script are strong`() {
        mapOf(
            "Hebrew alef" to 0x05D0,
            "Hebrew tav" to 0x05EA,
            "Hebrew maqaf" to 0x05BE,
            "Yiddish double vav" to 0x05F0,
            "Arabic beh" to 0x0628,
            "Arabic Supplement" to 0x0750,
            "Arabic Extended-A" to 0x08A0,
            "Persian peh" to 0x067E,
            "Syriac alaph" to 0x0710,
            "Thaana haa" to 0x0780,
            "N'Ko a" to 0x07CA,
            "Samaritan alaf" to 0x0800,
            "Mandaic halqa" to 0x0840,
            "Hebrew presentation form" to 0xFB2A,
            "Arabic Presentation Forms-A" to 0xFB50,
            "Arabic Presentation Forms-B" to 0xFE8F,
            "Phoenician alf" to 0x10900,
            "Adlam alif" to 0x1E900,
            "Arabic mathematical alef" to 0x1EE00,
        ).forEach { (name, codePoint) -> assertTrue(isStrongRtl(codePoint), name) }
    }

    @Test
    fun `marks, digits, punctuation, and left-to-right letters are not strong right-to-left`() {
        mapOf(
            "Latin A" to 'A'.code,
            "digit one" to '1'.code,
            "space" to ' '.code,
            "Greek alpha" to 0x03B1,
            "Cyrillic a" to 0x0430,
            "Hebrew qamats" to 0x05B8,
            "Hebrew dagesh" to 0x05BC,
            "Hebrew cantillation" to 0x0591,
            "Arabic comma" to 0x060C,
            "Arabic fatha" to 0x064E,
            "Arabic-Indic digit one" to 0x0661,
            "Extended Arabic-Indic digit one" to 0x06F1,
            "Syriac pthaha" to 0x0730,
            "Thaana abafili" to 0x07A6,
            "Hebrew presentation plus" to 0xFB29,
            "Arabic ornate parenthesis" to 0xFD3E,
            "byte order mark" to 0xFEFF,
            "Devanagari ka" to 0x0915,
            "CJK ideograph" to 0x4E00,
        ).forEach { (name, codePoint) -> assertFalse(isStrongRtl(codePoint), name) }
    }
}
