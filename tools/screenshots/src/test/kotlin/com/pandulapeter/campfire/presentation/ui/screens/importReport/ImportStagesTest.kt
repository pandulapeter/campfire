/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.importReport

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.onNodeWithContentDescription
import com.pandulapeter.campfire.data.model.importing.ImportProgress
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.import_failed_no_space
import com.pandulapeter.campfire.presentation.resources.import_progress_bytes_unpacking
import com.pandulapeter.campfire.presentation.resources.import_retry
import com.pandulapeter.campfire.presentation.resources.settings_library_size_decimal_separator
import com.pandulapeter.campfire.presentation.resources.settings_library_size_gigabytes
import com.pandulapeter.campfire.presentation.resources.settings_library_size_megabytes
import com.pandulapeter.campfire.presentation.ui.state.importing.ImportFailure
import com.pandulapeter.campfire.presentation.ui.state.importing.ImportReport
import com.pandulapeter.campfire.presentation.ui.state.importing.ImportWork
import com.pandulapeter.campfire.screenshots.AppTestScope
import com.pandulapeter.campfire.screenshots.runAppTest
import org.koin.mp.KoinPlatform
import kotlin.test.Test

/**
 * The import screen's stages that nothing in the app reaches yet: no phase reports its bytes and no preparation runs out
 * of space until the import streams archives into its staging folder. Each is put on screen through the import's own
 * state, standing in for the work that will report it, and checked as the user sees it.
 */
@OptIn(ExperimentalTestApi::class)
internal class ImportStagesTest {

    @Test
    fun `an unpacking that counts bytes says how far it is and fills its line by them`() = runAppTest {
        runOnIdle {
            viewModel.importReportState.show(ImportReport.Loading(sourceName = "library.zip"))
            KoinPlatform.getKoin().get<ImportWork>().progress.value = ImportProgress(
                phase = ImportProgress.Phase.UNPACKING,
                completed = 1,
                total = 3,
                completedBytes = UNPACKED_BYTES,
                totalBytes = ARCHIVE_BYTES,
            )
        }
        val text = string(
            Res.string.import_progress_bytes_unpacking,
            string(Res.string.settings_library_size_megabytes, "340"),
            string(Res.string.settings_library_size_gigabytes, "1" + string(Res.string.settings_library_size_decimal_separator) + "2"),
        )
        awaitText(text)
        val fraction = (UNPACKED_BYTES.toDouble() / ARCHIVE_BYTES).toFloat()
        waitUntil(timeoutMillis = SCREEN_TIMEOUT_MILLIS) {
            onAllNodes(hasProgressBarRangeInfo(ProgressBarRangeInfo(fraction, 0f..1f))).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `an import without room says how much space it needs and offers Try again`() = runAppTest {
        runOnIdle {
            viewModel.importReportState.show(
                ImportReport.Failed(reason = ImportFailure.NO_SPACE, sourceName = "library.zip", neededBytes = NEEDED_BYTES),
            )
        }
        val needed = string(Res.string.settings_library_size_gigabytes, "2" + string(Res.string.settings_library_size_decimal_separator) + "4")
        awaitText(string(Res.string.import_failed_no_space, needed))
        onNodeWithContentDescription(string(Res.string.import_retry)).assertHasClickAction()
    }

    private fun AppTestScope.awaitText(text: String) = waitUntil(timeoutMillis = SCREEN_TIMEOUT_MILLIS) {
        onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
}

private const val UNPACKED_BYTES = 340_000_000L
private const val ARCHIVE_BYTES = 1_200_000_000L
private const val NEEDED_BYTES = 2_400_000_000L
private const val SCREEN_TIMEOUT_MILLIS = 10_000L
