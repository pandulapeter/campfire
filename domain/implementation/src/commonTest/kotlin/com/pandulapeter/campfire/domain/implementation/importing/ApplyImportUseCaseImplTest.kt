/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.implementation.importing

import com.pandulapeter.campfire.data.model.Logger
import com.pandulapeter.campfire.data.model.importing.ImportBookkeeping
import com.pandulapeter.campfire.data.model.importing.ImportChoices
import com.pandulapeter.campfire.data.model.importing.ImportPlan
import com.pandulapeter.campfire.data.model.importing.ImportProgress
import com.pandulapeter.campfire.data.model.importing.ImportResult
import com.pandulapeter.campfire.data.model.importing.SharedLinkPart
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.data.repository.api.DemoLibraryRepository
import com.pandulapeter.campfire.domain.api.importing.ImportFilesUseCase
import com.pandulapeter.campfire.domain.implementation.TEST_PREFERENCES
import com.pandulapeter.campfire.domain.implementation.UserPreferencesRepositoryStub
import com.pandulapeter.campfire.domain.implementation.library.RememberDemoLibraryFilesUseCaseImpl
import com.pandulapeter.campfire.domain.implementation.library.RememberSharedLinkImportUseCaseImpl
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApplyImportUseCaseImplTest {

    private var written = ImportResult()
    private var onImport: suspend (onProgress: (ImportProgress) -> Unit) -> Unit = {}
    private val demoRecords = mutableListOf<Pair<List<String>, List<String>>>()
    private val preferences = object : UserPreferencesRepositoryStub() {
        var current = TEST_PREFERENCES

        override suspend fun updateUserPreferences(transform: (UserPreferences) -> UserPreferences) {
            current = transform(current)
        }
    }
    private val useCase = ApplyImportUseCaseImpl(
        importFiles = object : ImportFilesUseCase {
            override suspend fun invoke(plan: ImportPlan, choices: ImportChoices, onProgress: (ImportProgress) -> Unit): ImportResult {
                onImport(onProgress)
                return written
            }
        },
        rememberDemoLibraryFiles = RememberDemoLibraryFilesUseCaseImpl(
            object : DemoLibraryRepository {
                override suspend fun rememberDemoLibraryFiles(songFileNames: Collection<String>, setlistFileNames: Collection<String>) {
                    demoRecords += songFileNames.toList() to setlistFileNames.toList()
                }
            },
        ),
        rememberSharedLinkImport = RememberSharedLinkImportUseCaseImpl(preferences, Logger {}),
    )

    @Test
    fun `a demo import records the demo files it wrote under their own names`() = runTest {
        written = ImportResult(importedSongFileNames = listOf("a.cho", "b_2.cho"), importedSetlistFileNames = listOf("demo.setlist.json"))
        useCase(
            plan = ImportPlan(),
            choices = ImportChoices(),
            bookkeeping = ImportBookkeeping(demoSongFileNames = setOf("a.cho", "b.cho"), demoSetlistFileNames = setOf("demo.setlist.json")),
        )
        assertEquals(listOf(listOf("a.cho") to listOf("demo.setlist.json")), demoRecords)
    }

    @Test
    fun `each shared link records only the names it wrote, not a numbered copy`() = runTest {
        val plan = ImportPlan(songs = listOf(song("riverlight.cho", ImportPlan.Status.CONFLICTING), song("mine.cho", ImportPlan.Status.NEW)))
        written = ImportResult(importedSongFileNames = listOf("riverlight_2.cho", "mine.cho"))
        useCase(
            plan = plan,
            choices = ImportChoices(),
            bookkeeping = ImportBookkeeping(
                sharedLinkParts = listOf(SharedLinkPart("first", 0..0, IntRange.EMPTY), SharedLinkPart("second", 1..1, IntRange.EMPTY)),
            ),
        )
        assertEquals(mapOf("second" to listOf("songs/mine.cho")), preferences.current.sharedLinkImports)
        assertTrue(demoRecords.isEmpty())
    }

    @Test
    fun `cancelling the caller does not stop the import or its records`() = runTest {
        val plan = ImportPlan(songs = listOf(song("mine.cho", ImportPlan.Status.NEW)))
        written = ImportResult(importedSongFileNames = listOf("mine.cho"))
        val progressed = CompletableDeferred<Unit>()
        onImport = { onProgress ->
            onProgress(ImportProgress(phase = ImportProgress.Phase.IMPORTING, total = 1))
            delay(1_000)
        }
        val caller = launch {
            useCase(
                plan = plan,
                choices = ImportChoices(),
                bookkeeping = ImportBookkeeping(
                    demoSongFileNames = setOf("mine.cho"),
                    demoSetlistFileNames = emptySet(),
                    sharedLinkParts = listOf(SharedLinkPart("link", 0..0, IntRange.EMPTY)),
                ),
                onProgress = { progressed.complete(Unit) },
            )
        }
        progressed.await()
        caller.cancel()
        caller.join()
        assertEquals(listOf(listOf("mine.cho") to emptyList<String>()), demoRecords)
        assertEquals(mapOf("link" to listOf("songs/mine.cho")), preferences.current.sharedLinkImports)
    }

    private fun song(fileName: String, status: ImportPlan.Status) = ImportPlan.SongEntry(
        fileName = fileName,
        text = "{title: $fileName}",
        status = status,
        sourceFileName = fileName,
    )
}
