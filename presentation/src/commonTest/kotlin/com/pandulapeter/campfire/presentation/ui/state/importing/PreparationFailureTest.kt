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

import com.pandulapeter.campfire.data.formats.zip.WrappedArchiveException
import com.pandulapeter.campfire.data.formats.zip.ZipReader
import com.pandulapeter.campfire.data.formats.zip.ZipSource
import com.pandulapeter.campfire.data.model.OutOfSpaceException
import com.pandulapeter.campfire.data.model.importing.StagingRefusedException
import com.pandulapeter.campfire.presentation.ui.messages.Message
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class PreparationFailureTest {

    @Test
    fun `staging refused for space is no space - naming what the import needs`() {
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
    fun `a write the storage refused for space is no space - of an amount it cannot say`() {
        val failure = preparationFailureOf(IllegalStateException("Could not write.", OutOfSpaceException()))
        assertEquals(ImportFailure.NO_SPACE, failure.reason)
        assertNull(failure.neededBytes)
    }

    @Test
    fun `staging refused for passing what one import may stage is too large`() {
        assertEquals(ImportFailure.TOO_LARGE, preparationFailureOf(StagingRefusedException.TooLarge(limit = 10)).reason)
    }

    @Test
    fun `an archive past 4 GB written without ZIP64 is a wrapped archive and not a damaged one`() = runTest {
        val wrapped = wrappedArchiveException()
        assertEquals(ImportFailure.WRAPPED_ARCHIVE, preparationFailureOf(wrapped).reason)
        assertEquals(ImportFailure.WRAPPED_ARCHIVE, preparationFailureOf(IllegalStateException("Could not unpack.", wrapped)).reason)
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

    /** What the zip reader throws for an archive past 4 GB without ZIP64, got by reading one: its end record, 5 GiB long. */
    private suspend fun wrappedArchiveException(): WrappedArchiveException {
        val endRecord = byteArrayOf(0x50, 0x4B, 5, 6) + ByteArray(18)
        val size = 5L shl 30
        val source = object : ZipSource {
            override val size = size

            override suspend fun read(position: Long, buffer: ByteArray, offset: Int, length: Int) {
                for (index in 0 until length) {
                    val at = position + index - (size - endRecord.size)
                    buffer[offset + index] = if (at >= 0) endRecord[at.toInt()] else 0
                }
            }
        }
        return assertFailsWith<WrappedArchiveException> { ZipReader.open(source) }
    }
}
