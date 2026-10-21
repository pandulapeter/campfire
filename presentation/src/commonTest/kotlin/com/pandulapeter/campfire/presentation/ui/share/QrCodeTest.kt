/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.share

import com.pandulapeter.campfire.presentation.ui.share.QrCode.Level
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** The sizes chosen, against the capacities ISO/IEC 18004's table 7 lists for byte mode. Decoding is in desktopTest. */
internal class QrCodeTest {
    @Test fun `a short text takes version 1 at the strongest level`() {
        val code = assertNotNull(QrCode.encode("HELLO"))
        assertEquals(1, code.version)
        assertEquals(Level.H, code.level)
        assertEquals(21, code.size)
    }

    @Test fun `the level is never below the minimum`() {
        val code = assertNotNull(QrCode.encode("a".repeat(15), minimumLevel = Level.M))
        assertEquals(2, code.version)
        assertEquals(Level.Q, code.level)
    }

    @Test fun `a link of 130 characters takes version 8 at level M`() {
        val code = assertNotNull(QrCode.encode("a".repeat(130)))
        assertEquals(8, code.version)
        assertEquals(Level.M, code.level)
        assertEquals(49, code.size)
    }

    @Test fun `the length field widens from version 10`() {
        assertEquals(230, qrByteCapacity(9, Level.L))
        assertEquals(271, qrByteCapacity(10, Level.L))
    }

    @Test fun `version 40 holds the standard's largest texts`() {
        assertEquals(40, QrCode.encode("a".repeat(2953), minimumLevel = Level.L)?.version)
        assertEquals(40, QrCode.encode("a".repeat(1273), minimumLevel = Level.H)?.version)
    }

    @Test fun `a text no version holds gives nothing`() {
        assertNull(QrCode.encode("a".repeat(2954), minimumLevel = Level.L))
        assertNull(QrCode.encode("a".repeat(2332), minimumLevel = Level.M))
    }

    @Test fun `alignment patterns sit where table E1 puts them`() {
        assertEquals(listOf(6, 18), qrAlignmentPositions(2).toList())
        assertEquals(listOf(6, 34, 60, 86, 112, 138), qrAlignmentPositions(32).toList())
        assertEquals(listOf(6, 30, 58, 86, 114, 142, 170), qrAlignmentPositions(40).toList())
    }
}
