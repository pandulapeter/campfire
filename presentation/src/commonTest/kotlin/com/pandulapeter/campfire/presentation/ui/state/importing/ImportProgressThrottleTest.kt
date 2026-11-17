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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class ImportProgressThrottleTest {

    private val clock = TestTimeSource()
    private val delivered = mutableListOf<ImportProgress>()
    private val throttle = ImportProgressThrottle(clock) { delivered += it }

    @Test
    fun `a second of chunks reaches the screens a few times rather than once per chunk`() {
        // 64 KiB a millisecond: a fast download or unpack, reporting every chunk it moves.
        repeat(1_000) { chunk ->
            throttle.offer(unpacking(completedBytes = (chunk + 1) * CHUNK, totalBytes = 10_000 * CHUNK))
            clock += 1.milliseconds
        }

        assertEquals((1.seconds / ImportProgressThrottle.INTERVAL).toInt(), delivered.size)
        assertEquals(CHUNK, delivered.first().completedBytes)
    }

    @Test
    fun `the last bytes of a known total arrive at once`() {
        throttle.offer(unpacking(completedBytes = CHUNK, totalBytes = 2 * CHUNK))
        throttle.offer(unpacking(completedBytes = 2 * CHUNK, totalBytes = 2 * CHUNK))

        assertEquals(listOf(CHUNK, 2 * CHUNK), delivered.map { it.completedBytes })
    }

    @Test
    fun `a value held back arrives once the phase ends without its total`() {
        throttle.offer(downloading(completedBytes = CHUNK))
        clock += 10.milliseconds
        throttle.offer(downloading(completedBytes = 2 * CHUNK))
        throttle.offer(downloading(completedBytes = 3 * CHUNK))
        assertEquals(listOf(CHUNK), delivered.map { it.completedBytes })

        throttle.flush()
        throttle.flush()

        assertEquals(listOf(CHUNK, 3 * CHUNK), delivered.map { it.completedBytes })
    }

    @Test
    fun `a value held back arrives with the next offer once the interval has passed`() {
        throttle.offer(downloading(completedBytes = CHUNK))
        throttle.offer(downloading(completedBytes = 2 * CHUNK))
        clock += ImportProgressThrottle.INTERVAL
        throttle.offer(downloading(completedBytes = 3 * CHUNK))

        assertEquals(listOf(CHUNK, 3 * CHUNK), delivered.map { it.completedBytes })
    }

    @Test
    fun `a new phase or file and progress counted in files pass at once`() {
        throttle.offer(downloading(completedBytes = CHUNK))
        throttle.offer(unpacking(completedBytes = CHUNK, totalBytes = 4 * CHUNK))
        throttle.offer(unpacking(completedBytes = CHUNK, totalBytes = 4 * CHUNK, fileName = "nested.zip"))
        throttle.offer(ImportProgress(ImportProgress.Phase.READING, completed = 1, total = 3))
        throttle.offer(ImportProgress(ImportProgress.Phase.READING, completed = 2, total = 3))

        assertEquals(5, delivered.size)
    }

    private fun downloading(completedBytes: Long) =
        ImportProgress(ImportProgress.Phase.DOWNLOADING, fileName = "library.zip", completedBytes = completedBytes)

    private fun unpacking(completedBytes: Long, totalBytes: Long, fileName: String = "library.zip") =
        ImportProgress(ImportProgress.Phase.UNPACKING, fileName = fileName, completedBytes = completedBytes, totalBytes = totalBytes)

    private companion object {
        const val CHUNK = 64 * 1024L
    }
}
