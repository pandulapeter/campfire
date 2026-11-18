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

import com.pandulapeter.campfire.data.model.importing.ImportedImage
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Archives imported as the app imports them, received into the staging folder, through the review's plan and Import
 * into an empty library (see [ArchiveImportCheck] for what each is held to). The committed set runs with every build;
 * the private corpus runs where `campfire.importCorpus` names it, by hand.
 */
class ArchiveImportTest {

    private val imports = ArchiveImportCheck()

    @Test
    fun `every archive the unpack tests build imports`() {
        val archives = ArchiveCases.recorded()
        assertTrue(archives.size > 50, "Only ${archives.size} archives recorded from the unpack tests")
        runBlocking { imports.checkAll(archives) }
        report("Unpack tests' archives", archives.size)
    }

    @Test
    fun `libraries and backups and archives of many megabytes of pictures and attachments import`() = runBlocking {
        val archives = ArchiveCases.built()
        imports.checkAll(archives)
        val outcomes = imports.outcomes
        fun problems(label: String) = outcomes.getValue(label).plan?.songs.orEmpty().flatMap { song -> song.images.filter { !it.source.startsWith("https:") }.map { it.problem } }
        assertEquals(List(9) { null }, problems("pictures past 64 MiB"))
        assertEquals(List(3) { null }, problems("attachments past 50 MiB"))
        val lie = outcomes.getValue("a nested archive lying about its size").plan
        assertEquals(listOf("a.cho"), lie?.songs?.map { it.sourceFileName })
        assertTrue("inner.zip" in lie?.oversizedFileNames.orEmpty() + lie?.skippedFileNames.orEmpty(), "inner.zip is reported: $lie")
        assertEquals(List(150) { ImportedImage.Problem.UNREADABLE }, problems("pictures lying about their size"))
        report("Built archives", archives.size)
    }

    @Test
    fun `ZIP64 archives real tools wrote import as their twins without ZIP64`() = runBlocking {
        val archives = ArchiveCases.realWriters()
        val verdicts = archives.map { imports.check(it) }
        assertTrue(ArchiveImportCheck.Verdict.ZIP64_READ_AS_TWIN in verdicts, "No ZIP64 archive was held to its twin")
        report("Real writers' archives", archives.size)
    }

    @Test
    fun `seeded mutants of every small archive import or are skipped`() {
        val archives = (ArchiveCases.recorded() + runBlocking { ArchiveCases.built() } + ArchiveCases.realWriters()).filter { it.bytes.size <= MAX_MUTATED_SIZE }
        val mutants = archives.flatMapIndexed { index, archive -> ArchiveMutants.of(archive, count = MUTANTS_PER_ARCHIVE, seed = index) }
        runBlocking { imports.checkAll(mutants) }
        report("Mutants of ${archives.size} archives", mutants.size)
    }

    @Test
    fun `the archives of the private import corpus import`() {
        val path = System.getProperty("campfire.importCorpus").orEmpty()
        if (path.isBlank()) {
            println("Import corpus not configured (campfire.importCorpus)")
            return
        }
        val corpus = File(path)
        if (!corpus.isDirectory) fail("The import corpus $path is not a folder")
        val archives = runBlocking { ArchiveCases.corpus(corpus) }
        val mutants = archives.flatMapIndexed { index, archive -> ArchiveMutants.of(archive, count = CORPUS_MUTANTS_PER_ARCHIVE, seed = index) }
        runBlocking { imports.checkAll(archives + mutants) }
        report("Corpus archives (${archives.size}) and their mutants", archives.size + mutants.size)
    }

    private fun report(what: String, count: Int) = println("$what imported: $count, ${imports.verdicts}, ${imports.songsPlanned} songs planned")

    private companion object {
        const val MAX_MUTATED_SIZE = 256 * 1024
        const val MUTANTS_PER_ARCHIVE = 5
        const val CORPUS_MUTANTS_PER_ARCHIVE = 20
    }
}
