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

import com.pandulapeter.campfire.data.model.importing.ImportProgress
import com.pandulapeter.campfire.presentation.ui.platform.ImportNotification
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImportNotificationTextTest {

    @Test
    fun `an idle import has nothing to say`() {
        assertNull(WORDS.textOf(ImportWorkState.IDLE, null))
    }

    @Test
    fun `a phase says its line until it counts bytes`() {
        assertEquals("Downloading the file", WORDS.textOf(ImportWorkState.DOWNLOADING, null))
        assertEquals(
            "Unpacking the archive",
            WORDS.textOf(ImportWorkState.UNPACKING, ImportProgress(ImportProgress.Phase.READING, completed = 3, total = 10)),
        )
    }

    @Test
    fun `counted bytes are written in the screen's sizes, with the separator handed over`() {
        val progress = ImportProgress(ImportProgress.Phase.UNPACKING, completedBytes = 1_250_000, totalBytes = 3_400_000_000)
        assertEquals("Unpacking 1,3 MB of 3,4 GB", WORDS.textOf(ImportWorkState.UNPACKING, progress))
        assertEquals(ImportBytes(completed = 1_250_000, total = 3_400_000_000), importBytesOf(ImportWorkState.UNPACKING, progress))
    }

    @Test
    fun `a download of unknown length says how much has arrived so far`() {
        val progress = ImportProgress(ImportProgress.Phase.DOWNLOADING, completedBytes = 512)
        assertEquals("512 B downloaded so far", WORDS.textOf(ImportWorkState.DOWNLOADING, progress))
        assertNull(importBytesOf(ImportWorkState.DOWNLOADING, progress)?.total)
    }

    @Test
    fun `bytes of a phase that has ended are not shown under the next one`() {
        val progress = ImportProgress(ImportProgress.Phase.DOWNLOADING, completedBytes = 5_000, totalBytes = 10_000)
        assertEquals("Unpacking the archive", WORDS.textOf(ImportWorkState.UNPACKING, progress))
        assertNull(importBytesOf(ImportWorkState.UNPACKING, progress))
    }

    @Test
    fun `the write says its line and counts no bytes`() {
        val progress = ImportProgress(ImportProgress.Phase.IMPORTING, completed = 2, total = 5)
        assertEquals("Adding files", WORDS.textOf(ImportWorkState.WRITING, progress))
        assertNull(importBytesOf(ImportWorkState.WRITING, progress))
    }

    @Test
    fun `Cancel is offered for everything before the write`() {
        assertTrue(ImportWorkState.RECEIVING.isCancellable)
        assertTrue(ImportWorkState.DOWNLOADING.isCancellable)
        assertTrue(ImportWorkState.UNPACKING.isCancellable)
        assertFalse(ImportWorkState.WRITING.isCancellable)
        assertFalse(ImportWorkState.IDLE.isCancellable)
    }

    @Test
    fun `the words survive being sent as strings`() {
        assertEquals(WORDS, ImportNotification.fromWords(WORDS.toWords()))
    }

    @Test
    fun `words of another shape are not read`() {
        assertNull(ImportNotification.fromWords(WORDS.toWords().drop(1)))
    }

    private companion object {
        val WORDS = ImportNotification(
            channelName = "Imports",
            title = "Importing files",
            receiving = ImportNotification.Phase("Copying the files", "Copying {completed} of {total}", "{completed} copied so far"),
            downloading = ImportNotification.Phase("Downloading the file", "Downloading {completed} of {total}", "{completed} downloaded so far"),
            unpacking = ImportNotification.Phase("Unpacking the archive", "Unpacking {completed} of {total}", "{completed} unpacked so far"),
            writing = "Adding files",
            sizeUnits = listOf("{size} B", "{size} KB", "{size} MB", "{size} GB"),
            decimalSeparator = ",",
            cancelLabel = "Cancel",
        )
    }
}
