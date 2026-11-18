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

import com.pandulapeter.campfire.data.model.importing.ImportPlan
import kotlinx.coroutines.runBlocking
import java.io.File
import java.text.Normalizer
import kotlin.test.Test
import kotlin.test.fail

/**
 * Other apps' real backups, under the private corpus's `backups/` (`campfire.importCorpus`; blank skips it, so never in
 * CI), each picked as a file is picked and imported into an empty library, held to two properties that need no stored
 * expectation:
 * - **nothing is dropped silently**: every song and set the backup lists ([BackupListing], read without the importer) is
 *   in the import's plan as a song or a setlist (a set with as many songs as it names), or named among its problems, by
 *   its own entry or by the file it came in;
 * - **a round trip keeps everything**: the library exported whole and imported into a second empty library gives the
 *   same files by hash.
 *
 * Every file is checked before the test fails, and a line per file goes into the corpus's `.runs/backups.txt`, never
 * into the repository, since the backups hold other people's songs: its failures, else what was listed, planned, and
 * reported, which is where a format the import does not take shows, reported rather than dropped.
 */
class BackupCorpusTest {

    @Test
    fun `every backup in the private import corpus imports whole and survives a round trip`() {
        val path = System.getProperty("campfire.importCorpus").orEmpty()
        if (path.isBlank()) {
            println("Import corpus not configured (campfire.importCorpus)")
            return
        }
        val backups = File(path, BACKUPS)
        if (!backups.isDirectory) fail("The import corpus $path has no $BACKUPS folder")
        val files = backups.walkTopDown().onEnter { !it.name.startsWith(".") }
            .filter { it.isFile && !it.name.startsWith(".") && it.name != SOURCES }.sortedBy { it.path }.toList()
        val lines = files.map { file -> file.relativeTo(backups).invariantSeparatorsPath to runBlocking { check(file) } }
        val report = lines.joinToString("\n") { (name, result) -> "$name: " + (result.failures.ifEmpty { null }?.let { "FAILED: " + it.joinToString("; ") } ?: "passed, ${result.summary}") }
        File(File(path, RUNS), "backups.txt").apply { parentFile.mkdirs() }.writeText(report + "\n")
        println(report)
        val failures = lines.filter { it.second.failures.isNotEmpty() }
        if (failures.isNotEmpty()) fail("${failures.size} of ${files.size} backups fail:\n$report")
    }

    private class Result(val failures: List<String>, val summary: String)

    private suspend fun check(file: File): Result {
        val bytes = file.readBytes()
        val listing = BackupListing.of(file.name, bytes)
        return ImportPathApp().use { first ->
            val outcome = first.importPicked(name = file.name, bytes = bytes)
            val plan = outcome.plan ?: return Result(failures = listOf("the import failed: ${outcome.failure}"), summary = "")
            val summary = (listing?.let { "${it.songs.size} songs and ${it.sets.size} sets listed, " }.orEmpty()) + "${plan.songs.size} songs and " +
                "${plan.setlists.size} setlists planned, reported ${plan.skippedFileNames + plan.oversizedFileNames + plan.unreadableDocumentFileNames}"
            val failures = listing?.let { missing(file.name, it, plan) }.orEmpty().toMutableList()
            if (outcome.library.isNotEmpty()) {
                val export = first.exportLibrary()
                val again = ImportPathApp().use { second -> second.importPicked(name = "export.zip", bytes = export) }
                if (again.library != outcome.library) {
                    val lost = outcome.library.keys - again.library.keys
                    val gained = again.library.keys - outcome.library.keys
                    val changed = outcome.library.keys.intersect(again.library.keys).filter { outcome.library[it] != again.library[it] }
                    failures += "the round trip differs: lost $lost, gained $gained, changed $changed"
                }
            }
            Result(failures = failures, summary = summary)
        }
    }

    /** What [listing] names that [plan] neither plans nor names among its problems. */
    private fun missing(fileName: String, listing: BackupListing, plan: ImportPlan): List<String> {
        val reported = (plan.skippedFileNames + plan.oversizedFileNames + plan.unreadableDocumentFileNames).toSet()
        if (fileName in reported) return emptyList()
        val songTitles = plan.songs.map { it.title.folded() }.toMutableList()
        val setlists = plan.setlists.toMutableList()
        val missingSongs = listing.songs.filterNot { song -> songTitles.remove(song.title.folded()) || song.entryName in reported }.map { it.title }
        val missingSets = listing.sets.filterNot { set ->
            val planned = setlists.firstOrNull { set.title == null || it.setlist.title.folded() == set.title.folded() }
            if (planned != null) setlists.remove(planned)
            (planned != null && (set.songCount == null || planned.setlist.entries.size == set.songCount)) || set.entryName in reported
        }.map { set -> "${set.title ?: set.entryName} (${set.songCount ?: "?"} songs)" }
        return listOfNotNull(
            missingSongs.takeIf { it.isNotEmpty() }?.let { "${it.size} songs listed but neither planned nor reported: $it" },
            missingSets.takeIf { it.isNotEmpty() }?.let { "sets listed but neither planned whole nor reported: $it, planned ${plan.setlists.map { it.setlist.title to it.setlist.entries.size }}" },
        )
    }

    private fun String.folded() = Normalizer.normalize(trim(), Normalizer.Form.NFC)

    private companion object {
        const val BACKUPS = "backups"
        const val SOURCES = "sources.txt"
        const val RUNS = ".runs"
    }
}
