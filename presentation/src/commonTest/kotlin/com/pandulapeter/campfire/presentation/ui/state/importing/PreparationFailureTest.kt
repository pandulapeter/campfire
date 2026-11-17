/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state.importing

import com.pandulapeter.campfire.data.model.OutOfSpaceException
import com.pandulapeter.campfire.data.model.importing.StagingRefusedException
import com.pandulapeter.campfire.presentation.ui.messages.Message
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PreparationFailureTest {

    @Test
    fun `staging refused for space is no space, naming what the import needs`() {
        val failure = preparationFailureOf(StagingRefusedException.NoSpace(neededBytes = 1_500_000_000))
        assertEquals(ImportFailure.NO_SPACE, failure.reason)
        assertEquals(1_500_000_000, failure.neededBytes)
    }

    @Test
    fun `staging refused for space under another failure still names what the import needs`() {
        val failure = preparationFailureOf(IllegalStateException("Could not unpack.", StagingRefusedException.NoSpace(neededBytes = 42)))
        assertEquals(ImportFailure.NO_SPACE, failure.reason)
        assertEquals(42, failure.neededBytes)
    }

    @Test
    fun `a write the storage refused for space is no space, of an amount it cannot say`() {
        val failure = preparationFailureOf(IllegalStateException("Could not write.", OutOfSpaceException()))
        assertEquals(ImportFailure.NO_SPACE, failure.reason)
        assertNull(failure.neededBytes)
    }

    @Test
    fun `staging refused for passing what one import may stage is too large`() {
        assertEquals(ImportFailure.TOO_LARGE, preparationFailureOf(StagingRefusedException.TooLarge(limit = 10)).reason)
    }

    @Test
    fun `any other failure is files that could not be read`() {
        assertEquals(ImportFailure.UNREADABLE, preparationFailureOf(IllegalStateException("Not a zip archive.")).reason)
    }

    @Test
    fun `no space is told as the device being full where no import screen says it`() {
        assertEquals(Message.StorageFull, Preparation.Failed(ImportFailure.NO_SPACE).message)
        assertEquals(Message.ImportFailed, Preparation.Failed(ImportFailure.UNREADABLE).message)
    }
}
