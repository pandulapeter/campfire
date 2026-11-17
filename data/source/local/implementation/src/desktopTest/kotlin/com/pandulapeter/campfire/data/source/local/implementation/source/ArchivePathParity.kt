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

import com.pandulapeter.campfire.data.formats.zip.ZipException
import com.pandulapeter.campfire.data.formats.zip.ZipReader
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Imports an archive through both paths, each into an empty library of its own ([ImportPathApp]), and holds them to
 * the same plan, result, and library, but for the differences step 11 of the streaming archive import plan allows, each
 * checked rather than skipped: files past the in-memory budgets ([BudgetDifference]), and a ZIP64 archive, which only
 * the streaming path reads and which must then import as the same content without ZIP64 does on both ([Zip64Twin]).
 * One more comes from the plan's decisions rather than step 11's list: an archive whose nested archives together pass
 * the caps on records and held directories that only the streaming reader has ([StreamingCaps]).
 */
internal class ArchivePathParity {

    enum class Verdict { IDENTICAL, PAST_IN_MEMORY_BUDGET, PAST_STREAMING_CAPS, ZIP64_READ_AS_TWIN }

    val verdicts = mutableMapOf<Verdict, Int>()

    /** How many songs the in-memory path planned across the archives checked, so a run where nothing imports shows. */
    var songsPlanned = 0
        private set

    suspend fun check(archive: ParityArchive): Verdict = judge(archive).also { verdicts[it] = (verdicts[it] ?: 0) + 1 }

    /** Every one of [archives] checked, failing once all have been, with each difference found, so one run shows them all. */
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
            fail("${failures.size} of ${archives.size} archives differ between the paths:\n" + failures.joinToString("\n\n") { it.message.orEmpty().take(MAX_REPORTED_LENGTH) })
        }
    }

    private suspend fun judge(archive: ParityArchive): Verdict {
        val label = archive.label
        ImportPathApp().use { oldApp ->
            ImportPathApp().use { newApp ->
                val old = oldApp.import(name = archive.name, archive = archive.bytes, isStaged = false)
                val new = newApp.import(name = archive.name, archive = archive.bytes, isStaged = true)
                songsPlanned += old.plan?.songs?.size ?: 0
                if (old == new) return Verdict.IDENTICAL
                if (StreamingCaps.isPassed(archive.bytes)) {
                    StreamingCaps.check(label, old, new)
                    return Verdict.PAST_STREAMING_CAPS
                }
                if (!isRefusedAsZip64(archive.bytes)) {
                    BudgetDifference.check(label, old, new) { isNew, path -> (if (isNew) newApp else oldApp).libraryText(path) }
                    return Verdict.PAST_IN_MEMORY_BUDGET
                }
                assertEquals(listOf(archive.name), old.plan?.skippedFileNames, "$label: the in-memory path refuses ZIP64 as an archive it cannot unpack")
                assertTrue(old.plan?.songs.orEmpty().isEmpty() && old.library.isEmpty(), "$label: the in-memory path refuses ZIP64 whole")
                val twin = archive.twin ?: Zip64Twin.of(archive.bytes) ?: fail("$label: ZIP64 that cannot be written again without it\nstreamed: $new")
                assertTrue(!isRefusedAsZip64(twin), "$label: its twin has no ZIP64")
                val twinArchive = ParityArchive(label = "$label, its twin without ZIP64", name = archive.name, bytes = twin)
                // The twin is itself held to both paths, then the ZIP64 archive on the streaming path to the twin on it.
                judge(twinArchive)
                val newTwin = ImportPathApp().use { it.import(name = archive.name, archive = twin, isStaged = true) }
                assertEquals(newTwin, new, "$label: the streaming path reads ZIP64 as its twin without it")
                if (archive.twin != null) {
                    // The real writer's twin also proves the twin this test makes of a ZIP64 archive it has none for.
                    val made = ImportPathApp().use { app -> Zip64Twin.of(archive.bytes)?.let { app.import(name = archive.name, archive = it, isStaged = false) } }
                    assertEquals(oldTwinOf(archive.name, twin), made, "$label: the twin made without ZIP64 imports as the real writer's twin")
                }
                return Verdict.ZIP64_READ_AS_TWIN
            }
        }
    }

    /** Whether the in-memory reader refuses [archive] whole for being ZIP64, which is all it knows of ZIP64. */
    private fun isRefusedAsZip64(archive: ByteArray) = try {
        ZipReader.read(archive)
        false
    } catch (exception: ZipException) {
        exception.message.orEmpty().contains("ZIP64")
    }

    private suspend fun oldTwinOf(name: String, twin: ByteArray) = ImportPathApp().use { it.import(name = name, archive = twin, isStaged = false) }

    private companion object {
        const val MAX_REPORTED_LENGTH = 4_000
    }
}
