/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.implementation.songs

import com.pandulapeter.campfire.data.model.song.SongContent
import com.pandulapeter.campfire.domain.implementation.SongRepositoryStub
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class SaveSongContentUseCaseImplTest {

    private lateinit var caller: Job
    private var isWriteFinished = false

    /** Cancels the caller from inside the write, and goes on writing only if nothing cancels the write itself. */
    private suspend fun cancelCallerAndWrite() {
        caller.cancel()
        delay(1)
        isWriteFinished = true
    }

    @Test
    fun `a song save finishes when its caller is cancelled`() = runTest {
        val useCase = SaveSongContentUseCaseImpl(
            object : SongRepositoryStub() {
                override suspend fun saveSong(content: SongContent, expectedText: String?): Boolean {
                    cancelCallerAndWrite()
                    return true
                }
            },
        )
        caller = launch { useCase(SongContent(fileName = "a.cho", text = "[C]One")) }
        caller.join()
        assertTrue(isWriteFinished)
    }
}
