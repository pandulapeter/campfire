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

import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The gate for switching every archive to the streaming import, kept until the old path is removed: each
 * archive is imported held in memory and received into the staging folder, through the review's plan and Import into
 * an empty library, and the two must agree (see [ArchivePathParity] for the differences allowed, each checked). The
 * committed set runs with every build; the private corpus runs where `campfire.importCorpus` names it, by hand.
 */
class ArchivePathParityTest {

    private val parity = ArchivePathParity()

    @Test
    fun `every archive the unpack tests build imports alike on both paths`() {
        val archives = ParityArchives.recorded()
        assertTrue(archives.size > 50, "Only ${archives.size} archives recorded from the unpack tests")
        runBlocking { parity.checkAll(archives) }
        report("Unpack tests' archives", archives.size)
    }

    @Test
    fun `libraries and backups and archives past the in-memory budgets import alike on both paths but for those budgets`() = runBlocking {
        val archives = ParityArchives.built()
        val verdicts = archives.associate { it.label to parity.check(it) }
        assertEquals(ArchivePathParity.Verdict.PAST_IN_MEMORY_BUDGET, verdicts["pictures past the in-memory budget"])
        assertEquals(ArchivePathParity.Verdict.PAST_IN_MEMORY_BUDGET, verdicts["attachments past the in-memory budget"])
        assertEquals(ArchivePathParity.Verdict.IDENTICAL, verdicts["a nested archive lying about its size"])
        assertEquals(ArchivePathParity.Verdict.PAST_IN_MEMORY_BUDGET, verdicts["pictures lying about their size"])
        report("Built archives", archives.size)
    }

    @Test
    fun `ZIP64 archives real tools wrote import on the streaming path as their twins without ZIP64`() = runBlocking {
        val archives = ParityArchives.realWriters()
        val verdicts = archives.map { parity.check(it) }
        assertTrue(ArchivePathParity.Verdict.ZIP64_READ_AS_TWIN in verdicts, "No ZIP64 archive was read only by the streaming path")
        report("Real writers' archives", archives.size)
    }

    @Test
    fun `seeded mutants of every small archive import alike on both paths`() {
        val archives = (ParityArchives.recorded() + runBlocking { ParityArchives.built() } + ParityArchives.realWriters()).filter { it.bytes.size <= MAX_MUTATED_SIZE }
        val mutants = archives.flatMapIndexed { index, archive -> ArchiveMutants.of(archive, count = MUTANTS_PER_ARCHIVE, seed = index) }
        runBlocking { parity.checkAll(mutants) }
        report("Mutants of ${archives.size} archives", mutants.size)
    }

    @Test
    fun `the archives of the private import corpus import alike on both paths`() {
        val path = System.getProperty("campfire.importCorpus").orEmpty()
        if (path.isBlank()) {
            println("Import corpus not configured (campfire.importCorpus)")
            return
        }
        val corpus = File(path)
        if (!corpus.isDirectory) fail("The import corpus $path is not a folder")
        val archives = runBlocking { ParityArchives.corpus(corpus) }
        val mutants = archives.flatMapIndexed { index, archive -> ArchiveMutants.of(archive, count = CORPUS_MUTANTS_PER_ARCHIVE, seed = index) }
        runBlocking { parity.checkAll(archives + mutants) }
        report("Corpus archives (${archives.size}) and their mutants", archives.size + mutants.size)
    }

    private fun report(what: String, count: Int) = println("$what through both paths: $count, ${parity.verdicts}, ${parity.songsPlanned} songs planned")

    private companion object {
        const val MAX_MUTATED_SIZE = 256 * 1024
        const val MUTANTS_PER_ARCHIVE = 5
        const val CORPUS_MUTANTS_PER_ARCHIVE = 20
    }
}
