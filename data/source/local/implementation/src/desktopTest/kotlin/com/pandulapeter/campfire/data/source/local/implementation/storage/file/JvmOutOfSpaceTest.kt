/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.storage.file

import com.pandulapeter.campfire.data.model.isOutOfSpace
import java.io.IOException
import java.nio.file.AccessDeniedException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JvmOutOfSpaceTest {

    @Test
    fun `a write refused for space is out of space in every platform's wording`() {
        assertTrue(IOException("No space left on device").asStorageCause().isOutOfSpace())
        assertTrue(IOException("write failed", IOException("write failed: ENOSPC (No space left on device)")).asStorageCause().isOutOfSpace())
        assertTrue(IOException("There is not enough space on the disk").asStorageCause().isOutOfSpace())
    }

    @Test
    fun `any other failed write is not`() {
        assertFalse(AccessDeniedException("a.cho").asStorageCause().isOutOfSpace())
        assertFalse(IOException("Input/output error").asStorageCause().isOutOfSpace())
    }
}
