/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.source

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Imports an archive as the app imports one: received into the staging folder, through the review's plan and Import into
 * an empty library of its own ([ImportPathApp]), twice, on two devices, which must come to the same plan, result, and
 * library, since content decides what an import makes of an archive and never the session staging it. The preparation
 * never fails for an archive within a phone's free space, and the staging folder is empty once the import settles. A ZIP64
 * archive must import as the same content without ZIP64 does ([Zip64Twin]), unless it is skipped whole as damaged.
 */
internal class ArchivePathParity {

    enum class Verdict { IMPORTED, ZIP64_READ_AS_TWIN }

    val verdicts = mutableMapOf<Verdict, Int>()

    /** What each archive checked imported to, by its label. */
    val outcomes = mutableMapOf<String, ImportOutcome>()

    /** How many songs were planned across the archives checked, so a run where nothing imports shows. */
    var songsPlanned = 0
        private set

    suspend fun check(archive: ParityArchive): Verdict = judge(archive).also { verdicts[it] = (verdicts[it] ?: 0) + 1 }

    /** Every one of [archives] checked, failing once all have been, with each failure found, so one run shows them all. */
    suspend fun checkAll(archives: List<ParityArchive>) {
        val failures = archives.mapNotNull { archive ->
            try {
                check(archive)
                null
            } catch (failure: AssertionError) {
                failure
            }
        }
        if (failures.isNotEmpty()) {
            fail("${failures.size} of ${archives.size} archives failed:\n" + failures.joinToString("\n\n") { it.message.orEmpty().take(MAX_REPORTED_LENGTH) })
        }
    }

    private suspend fun judge(archive: ParityArchive): Verdict {
        val label = archive.label
        val outcome = imported(label, archive.name, archive.bytes).also { outcomes[label] = it }
        songsPlanned += outcome.plan?.songs?.size ?: 0
        if (!hasZip64EndRecord(archive.bytes)) return Verdict.IMPORTED
        val plan = outcome.plan
        val isSkippedWhole = plan != null && plan.skippedFileNames == listOf(archive.name) && plan.songs.isEmpty() && outcome.library.isEmpty()
        val twin = archive.twin ?: Zip64Twin.of(archive.bytes)
        if (twin == null) {
            assertTrue(isSkippedWhole, "$label: ZIP64 that cannot be written again without it, and not skipped whole\n$outcome")
            return Verdict.IMPORTED
        }
        assertTrue(!hasZip64EndRecord(twin), "$label: its twin has no ZIP64")
        val twinArchive = ParityArchive(label = "$label, its twin without ZIP64", name = archive.name, bytes = twin)
        judge(twinArchive)
        val twinOutcome = imported(twinArchive.label, archive.name, twin)
        if (isSkippedWhole && twinOutcome != outcome) return Verdict.IMPORTED
        assertEquals(twinOutcome, outcome, "$label: ZIP64 imports as its twin without it")
        if (archive.twin != null) {
            // The real writer's twin also proves the twin this test makes of a ZIP64 archive it has none for.
            val made = Zip64Twin.of(archive.bytes)?.let { imported("$label, its made twin", archive.name, it) }
            assertEquals(twinOutcome, made, "$label: the twin made without ZIP64 imports as the real writer's twin")
        }
        return Verdict.ZIP64_READ_AS_TWIN
    }

    /** [bytes] imported on two devices, which must agree, and that the preparation did not fail. */
    private suspend fun imported(label: String, name: String, bytes: ByteArray): ImportOutcome {
        val outcome = ImportPathApp().use { it.import(name = name, archive = bytes, isStaged = true) }
        assertNull(outcome.failure, "$label: the preparation failed")
        val again = ImportPathApp().use { it.import(name = name, archive = bytes, isStaged = true) }
        assertEquals(outcome, again, "$label: two imports of the same archive differ")
        return outcome
    }

    /**
     * Whether the end of central directory record of [archive] has a field at its ZIP64 marker, which only a ZIP64
     * archive has: what an archive without ZIP64 cannot say, so its twin is what it must import as.
     */
    private fun hasZip64EndRecord(archive: ByteArray): Boolean {
        val end = (archive.size - END_SIZE downTo maxOf(0, archive.size - END_SIZE - 65_535)).firstOrNull { archive.u32(it) == END_SIGNATURE } ?: return false
        return archive.u16(end + 10) == 0xFFFF || archive.u32(end + 12) == 0xFFFFFFFFL || archive.u32(end + 16) == 0xFFFFFFFFL
    }

    private fun ByteArray.u16(offset: Int) = (this[offset].toInt() and 0xFF) or ((this[offset + 1].toInt() and 0xFF) shl 8)

    private fun ByteArray.u32(offset: Int) = u16(offset).toLong() or (u16(offset + 2).toLong() shl 16)

    private companion object {
        const val MAX_REPORTED_LENGTH = 4_000
        const val END_SIZE = 22
        const val END_SIGNATURE = 0x06054B50L
    }
}
