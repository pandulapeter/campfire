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

/** [archive] read through a [ByteArrayZipSource], as [ZipReader.read] reads one; see [assertReadsOrRefuses]. */
internal suspend fun readArchive(
    archive: ByteArray,
    maxTotalSize: Long = ImportLimits.MAX_IMPORT_SIZE,
    limitOf: (name: String, declaredSize: Long) -> EntryPolicy = { _, _ -> EntryPolicy.Read(Long.MAX_VALUE) },
    sniffedLimitOf: (name: String, prefix: ByteArray) -> Long? = { _, _ -> null },
): ZipContent = ZipReader.read(ByteArrayZipSource(archive), maxTotalSize, limitOf, sniffedLimitOf)

/** [archive] read, or refused with a [ZipException], never failing in any other way. */
internal suspend fun assertReadsOrRefuses(archive: ByteArray, message: String = "") {
    try {
        readArchive(archive)
    } catch (_: ZipException) {
    } catch (exception: Exception) {
        fail("$message failed with $exception", exception)
    }
}

/** Two reads of archives that hold the same: the same entries, unread entries, and cost, or a [ZipException] from both. */
internal fun assertAlike(expected: Result<ZipContent>, actual: Result<ZipContent>, message: String = "") {
    listOf(expected, actual).forEach { result ->
        result.exceptionOrNull()?.let { if (it !is ZipException) fail("$message failed with $it", it) }
    }
    assertEquals(expected.isSuccess, actual.isSuccess, "$message: ${expected.exceptionOrNull()} / ${actual.exceptionOrNull()}")
    if (expected.isFailure) return
    val one = expected.getOrThrow()
    val other = actual.getOrThrow()
    assertEquals(one.entries.map { it.name }, other.entries.map { it.name }, message)
    one.entries.zip(other.entries).forEach { (first, second) -> assertContentEquals(first.bytes, second.bytes, "$message, ${first.name}") }
    assertEquals(one.unread, other.unread, message)
    assertEquals(one.chargedSize, other.chargedSize, message)
}
