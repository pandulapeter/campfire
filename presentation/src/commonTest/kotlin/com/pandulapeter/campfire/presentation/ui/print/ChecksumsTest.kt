/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.print

import kotlin.test.Test
import kotlin.test.assertEquals

/** The check values the two algorithms' specifications publish. */
internal class ChecksumsTest {
    @Test fun `crc32 gives the standard check value`() {
        assertEquals(0xCBF43926.toInt(), crc32("123456789".encodeToByteArray()))
    }

    @Test fun `crc32 of nothing is zero`() {
        assertEquals(0, crc32(ByteArray(0)))
    }

    @Test fun `adler32 gives the known value`() {
        assertEquals(0x11E60398, adler32("Wikipedia".encodeToByteArray()))
    }
}
