/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.formats.zip

import com.pandulapeter.campfire.data.model.importing.ImportLimits
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * [archive] read by both readers, the one in memory and the one through a [ZipSource], which must come to the same
 * entries, the same unread ones, and the same cost, or both refuse it with a [ZipException]; the in-memory one's
 * outcome is returned, or thrown. The callbacks are asked by both, so they must answer the same each time.
 */
internal suspend fun readAlike(
    archive: ByteArray,
    maxTotalSize: Long = ImportLimits.MAX_IMPORT_SIZE,
    limitOf: (name: String, declaredSize: Long) -> EntryPolicy = { _, _ -> EntryPolicy.Read(Long.MAX_VALUE) },
    sniffedLimitOf: (name: String, prefix: ByteArray) -> Long? = { _, _ -> null },
): ZipContent {
    val inMemory = runCatching { ZipReader.read(archive, maxTotalSize, limitOf, sniffedLimitOf) }
    val streamed = runCatching { ZipReader.read(ByteArrayZipSource(archive), maxTotalSize, limitOf, sniffedLimitOf) }
    assertAlike(inMemory, streamed)
    return inMemory.getOrThrow()
}

/** [readAlike] of an archive that both readers may refuse, as long as they refuse it alike. */
internal suspend fun assertReadsAlike(archive: ByteArray, message: String = "") = assertAlike(
    expected = runCatching { ZipReader.read(archive) },
    actual = runCatching { ZipReader.read(ByteArrayZipSource(archive)) },
    message = message,
)

/** The same entries, unread entries, and cost, or a [ZipException] from both, never another failure. */
internal fun assertAlike(expected: Result<ZipContent>, actual: Result<ZipContent>, message: String = "") {
    listOf(expected, actual).forEach { result ->
        result.exceptionOrNull()?.let { if (it !is ZipException) fail("$message failed with $it", it) }
    }
    assertEquals(expected.isSuccess, actual.isSuccess, "$message: ${expected.exceptionOrNull()} / ${actual.exceptionOrNull()}")
    if (expected.isFailure) return
    val inMemory = expected.getOrThrow()
    val streamed = actual.getOrThrow()
    assertEquals(inMemory.entries.map { it.name }, streamed.entries.map { it.name }, message)
    inMemory.entries.zip(streamed.entries).forEach { (one, other) -> assertContentEquals(one.bytes, other.bytes, "$message, ${one.name}") }
    assertEquals(inMemory.unread, streamed.unread, message)
    assertEquals(inMemory.chargedSize, streamed.chargedSize, message)
}
