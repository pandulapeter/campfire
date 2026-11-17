/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.pandulapeter.campfire.data.model.importing.ImportDownload
import com.pandulapeter.campfire.domain.api.importing.DownloadImportFileUseCase
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.import_exit_confirm
import com.pandulapeter.campfire.presentation.resources.import_exit_keep
import com.pandulapeter.campfire.presentation.resources.import_exit_title
import com.pandulapeter.campfire.presentation.ui.state.importing.ImportSource
import com.pandulapeter.campfire.presentation.ui.state.importing.ImportWork
import com.pandulapeter.campfire.presentation.ui.state.importing.ImportWorkState
import com.pandulapeter.campfire.screenshots.AppTestScope
import com.pandulapeter.campfire.screenshots.runAppTest
import kotlinx.coroutines.awaitCancellation
import org.koin.dsl.module
import org.koin.mp.KoinPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Closing the app while an import is still being read, as the desktop's window close and Cmd+Q do through `requestExit`:
 * an import downloading an address that never answers stands for any read that takes long.
 */
@OptIn(ExperimentalTestApi::class)
internal class ImportExitTest {

    private var isDownloadCancelled = false

    private val neverAnsweringDownload = module {
        factory<DownloadImportFileUseCase> {
            object : DownloadImportFileUseCase {
                override suspend fun invoke(url: String, onProgress: (Long, Long?) -> Unit): ImportDownload = try {
                    awaitCancellation()
                } finally {
                    isDownloadCancelled = true
                }
            }
        }
    }

    @Test
    fun `an exit while an import is read asks first, and Stop cancels the import before the exit runs`() =
        runAppTest(extraFakes = listOf(neverAnsweringDownload)) {
            startDownload()
            var exits = 0
            var cancellations = 0
            runOnIdle { viewModel.appExitController.requestExit(onExit = { exits++ }, onCancelled = { cancellations++ }) }
            waitForIdle()
            onNodeWithText(string(Res.string.import_exit_title)).assertExists()
            assertEquals(0, exits)
            onNode(hasText(string(Res.string.import_exit_confirm)) and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
            waitForIdle()
            assertEquals(1, exits)
            assertEquals(0, cancellations)
            waitUntil(timeoutMillis = TIMEOUT_MILLIS) { work.state.value == ImportWorkState.IDLE }
            assertTrue(isDownloadCancelled)
        }

    @Test
    fun `keeping the import cancels the exit and lets the import go on`() = runAppTest(extraFakes = listOf(neverAnsweringDownload)) {
        startDownload()
        var exits = 0
        var cancellations = 0
        runOnIdle { viewModel.appExitController.requestExit(onExit = { exits++ }, onCancelled = { cancellations++ }) }
        waitForIdle()
        onNode(hasText(string(Res.string.import_exit_keep)) and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
        waitForIdle()
        assertEquals(0, exits)
        assertEquals(1, cancellations)
        assertEquals(ImportWorkState.DOWNLOADING, work.state.value)
    }

    @Test
    fun `an exit with no import being read asks nothing about one`() = runAppTest {
        var exits = 0
        runOnIdle { viewModel.appExitController.requestExit(onExit = { exits++ }) }
        waitForIdle()
        assertEquals(1, exits)
    }

    private val work get() = KoinPlatform.getKoin().get<ImportWork>()

    private fun AppTestScope.startDownload() {
        runOnIdle { viewModel.importController.enqueueImport(ImportSource.Address("https://example.com/library.zip")) }
        waitUntil(timeoutMillis = TIMEOUT_MILLIS) { work.state.value == ImportWorkState.DOWNLOADING }
    }
}

private const val TIMEOUT_MILLIS = 10_000L
