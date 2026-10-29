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

/**
 * The code points whose bidirectional class is R or AL, as inclusive ranges in ascending order: Hebrew, Arabic, Syriac,
 * Thaana, N'Ko, Samaritan, Mandaic, the Arabic supplements and extensions, and the Hebrew and Arabic presentation
 * forms, each without its combining marks (class NSM), Arabic's own digits and number signs (AN), and its neutral
 * punctuation. A code point still unassigned inside these blocks counts, since the Unicode standard gives it the
 * block's class in advance. The supplementary planes' right-to-left areas (U+10800–U+10FFF and U+1E800–U+1EFFF, from
 * Phoenician to Adlam) are taken whole: their marks counted with their letters cannot outvote a line's letters.
 */
private val strongRtlRanges = intArrayOf(
    0x0590, 0x0590, 0x05BE, 0x05BE, 0x05C0, 0x05C0, 0x05C3, 0x05C3, 0x05C6, 0x05C6, 0x05C8, 0x05FF,
    0x0608, 0x0608, 0x060B, 0x060B, 0x060D, 0x060D, 0x061B, 0x064A, 0x066D, 0x066F, 0x0671, 0x06D5,
    0x06E5, 0x06E6, 0x06EE, 0x06EF, 0x06FA, 0x070D, 0x070F, 0x0710, 0x0712, 0x072F, 0x074B, 0x07A5,
    0x07B1, 0x07EA, 0x07F4, 0x07F5, 0x07FA, 0x07FC, 0x07FE, 0x0815, 0x081A, 0x081A, 0x0824, 0x0824,
    0x0828, 0x0828, 0x082E, 0x0858, 0x085C, 0x088F, 0x0892, 0x0896, 0x08A0, 0x08C9,
    0xFB1D, 0xFB1D, 0xFB1F, 0xFB28, 0xFB2A, 0xFD3D, 0xFD40, 0xFDCF, 0xFDF0, 0xFDFC, 0xFE70, 0xFEFE,
    0x10800, 0x10FFF, 0x1E800, 0x1EFFF,
)

/**
 * Whether [codePoint] is a strong right-to-left character: one whose bidirectional class is R (Hebrew, N'Ko,
 * Samaritan, Mandaic) or AL (Arabic, Syriac, Thaana), which is what makes a paragraph that starts with it, or a line
 * mostly made of it, read from the right.
 *
 * The one test of a character's direction the app makes, for every right-to-left script alike: a song's direction,
 * the editor's paragraphs, and the runs of a PDF's text are all decided by it, so no code names a script. Combining
 * marks (Hebrew points, Arabic harakat), digits, and punctuation are not strong, and answer false, as do Latin and
 * every other left-to-right script.
 */
public fun isStrongRtl(codePoint: Int): Boolean {
    if (codePoint < strongRtlRanges[0] || codePoint > strongRtlRanges[strongRtlRanges.lastIndex]) return false
    var low = 0
    var high = strongRtlRanges.size / 2 - 1
    while (low <= high) {
        val middle = (low + high) ushr 1
        when {
            codePoint < strongRtlRanges[2 * middle] -> high = middle - 1
            codePoint > strongRtlRanges[2 * middle + 1] -> low = middle + 1
            else -> return true
        }
    }
    return false
}
