/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.sync.implementation.scheduling

import com.pandulapeter.campfire.data.model.OutOfSpaceException
import com.pandulapeter.campfire.data.model.sync.SyncFailureReason
import com.pandulapeter.campfire.data.source.local.api.LibraryStorageException
import kotlin.test.Test
import kotlin.test.assertEquals

class SyncFailureReasonTest {

    @Test
    fun `a run that could not write for lack of room says this device is full`() =
        assertEquals(SyncFailureReason.LOCAL_STORAGE_FULL, LibraryStorageException("Could not write.", OutOfSpaceException()).toFailureReason())

    @Test
    fun `a run that could not read or write the library otherwise says only that`() =
        assertEquals(SyncFailureReason.STORAGE, LibraryStorageException("Could not read.").toFailureReason())
}
