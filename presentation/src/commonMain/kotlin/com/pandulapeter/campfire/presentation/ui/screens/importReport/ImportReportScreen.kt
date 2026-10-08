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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.data.model.domain.ImportConflictResolution
import com.pandulapeter.campfire.data.model.domain.ImportProgress
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.done
import com.pandulapeter.campfire.presentation.resources.ic_check
import com.pandulapeter.campfire.presentation.resources.ic_import
import com.pandulapeter.campfire.presentation.resources.import_conflicts
import com.pandulapeter.campfire.presentation.resources.import_conflicts_confirm
import com.pandulapeter.campfire.presentation.resources.import_failed
import com.pandulapeter.campfire.presentation.resources.import_progress_title
import com.pandulapeter.campfire.presentation.resources.import_status_stopped
import com.pandulapeter.campfire.presentation.resources.import_status_title
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.only
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.dialogs.ImportProgressContent
import org.jetbrains.compose.resources.painterResource

/**
 * Everything about an import that went anywhere but the one way a progress dialog and a snackbar are enough for (see
 * `CampfireViewModel.importReport`), on one screen that goes through the import's stages in place rather than as a
 * run of dialogs replacing one another: the question about names that are taken, the import its answer decides on
 * being written, and every file of what it came to, grouped by what became of it. It is a screen of the back stack,
 * so a song opened from its list comes back to it, and a dialog that asks for one confirmation goes over it.
 *
 * Leaving it while it asks is cancelling the import, and needs no confirmation since nothing has been written yet;
 * leaving it while the import is written lets that finish on its own, see `CampfireViewModel.onImportReportLeft`.
 */
@Composable
internal fun ImportReportScreen(
    viewModel: CampfireViewModel,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    val current by viewModel.importReport.collectAsStateWithLifecycle()
    // The screen is still composed while it slides away, after the view model has let go of what it showed.
    var report by remember { mutableStateOf(current) }
    current?.let { report = it }
    val shown = report ?: return
    val progress by viewModel.importProgress.collectAsStateWithLifecycle()
    val songs by viewModel.allSongs.collectAsStateWithLifecycle()
    val setlists by viewModel.setlists.collectAsStateWithLifecycle()
    val isPerformanceModeEnabled by viewModel.isPerformanceModeEnabled.collectAsStateWithLifecycle()
    val searchState = viewModel.importReportSearch
    val query by searchState.activeQuery.collectAsStateWithLifecycle("")
    var resolution by rememberSaveable { mutableStateOf(ImportConflictResolution.KEEP_BOTH) }
    val sections = remember(shown, songs, setlists) {
        when (shown) {
            is ImportReport.Review -> importReportSections(shown.summary)
            ImportReport.Importing -> emptyList()
            is ImportReport.Finished -> shown.result?.let { importReportSections(it, songs, setlists) }.orEmpty()
        }
    }
    val matchingSections = remember(sections, query) { sections.matching(query, viewModel.songRenderer::normalizeForSearch) }
    val isSearchAvailable = sections.isNotEmpty()
    val layoutDirection = LocalLayoutDirection.current
    // Narrowed rather than taken apart, so that the keyboard of the search field lays the list out again as it slides
    // instead of recomposing the screen on every frame of it.
    val listPadding = contentPadding.only(bottom = true, extraBottom = FLOATING_BUTTON_CLEARANCE)
    Column(Modifier.fillMaxSize()) {
        ImportReportTopAppBar(
            title = shown.title(),
            onClose = onBack,
        )
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(start = contentPadding.calculateStartPadding(layoutDirection), end = contentPadding.calculateEndPadding(layoutDirection)),
        ) {
            AnimatedContent(
                targetState = shown,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                // The stage, so that a result arriving for the same stage, or the library renaming a song in it, is
                // the same list going on rather than a second one fading in over it.
                contentKey = { it::class },
            ) { stage ->
                when (stage) {
                    is ImportReport.Review -> ReportList(
                        contentPadding = listPadding,
                        searchFieldIndex = REVIEW_HEADER_ITEM_COUNT.takeIf { isSearchAvailable },
                    ) { listState ->
                        reviewHeader(
                            summary = stage.summary,
                            resolution = resolution,
                            onResolutionSelected = { resolution = it },
                        )
                        if (isSearchAvailable) searchField(searchState = searchState, listState = listState, index = REVIEW_HEADER_ITEM_COUNT)
                        sections(
                            sections = matchingSections,
                            isFiltered = query.isNotBlank(),
                            isPerformanceModeEnabled = isPerformanceModeEnabled,
                            onOpenSong = viewModel::openReportedSong,
                            onEditSong = { viewModel.openEditor(it) },
                        )
                    }

                    ImportReport.Importing -> Box(
                        modifier = Modifier.fillMaxSize().padding(contentPadding.only(bottom = true)),
                        contentAlignment = Alignment.Center,
                    ) {
                        ImportProgressContent(
                            modifier = Modifier.widthIn(max = PROGRESS_MAX_WIDTH).fillMaxWidth().padding(24.dp),
                            // The comparison before the first entry is reported is part of writing the import too.
                            progress = progress ?: ImportProgress(ImportProgress.Phase.IMPORTING),
                        )
                    }

                    is ImportReport.Finished -> ReportList(
                        contentPadding = listPadding,
                        searchFieldIndex = FINISHED_HEADER_ITEM_COUNT.takeIf { isSearchAvailable },
                    ) { listState ->
                        finishedHeader(stage.result)
                        if (isSearchAvailable) searchField(searchState = searchState, listState = listState, index = FINISHED_HEADER_ITEM_COUNT)
                        sections(
                            sections = matchingSections,
                            isFiltered = query.isNotBlank(),
                            isPerformanceModeEnabled = isPerformanceModeEnabled,
                            onOpenSong = viewModel::openReportedSong,
                            onEditSong = { viewModel.openEditor(it) },
                        )
                    }
                }
            }
            ImportReportButton(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(contentPadding.only(bottom = true, extraBottom = 16.dp))
                    .padding(end = 16.dp),
                report = shown,
                onImport = {
                    val summary = (shown as? ImportReport.Review)?.summary
                    if (summary != null && resolution == ImportConflictResolution.REPLACE) {
                        viewModel.showDialog(DialogType.ConfirmImportReplace(summary.conflictingFileNames.size))
                    } else {
                        viewModel.resolveImport(resolution)
                    }
                },
                onDone = onBack,
            )
        }
    }
}

@Composable
private fun ImportReport.title() = stringResource(
    when (this) {
        is ImportReport.Review -> Res.string.import_conflicts
        ImportReport.Importing -> Res.string.import_progress_title
        is ImportReport.Finished -> when {
            result == null -> Res.string.import_failed
            result.isFailed -> Res.string.import_status_stopped
            else -> Res.string.import_status_title
        }
    },
)

/**
 * Import while the screen asks, Done once it has an answer, and nothing while the import is being written: there is
 * nothing to do then but wait, and Close is still there for leaving it to finish on its own.
 */
@Composable
private fun ImportReportButton(
    modifier: Modifier = Modifier,
    report: ImportReport,
    onImport: () -> Unit,
    onDone: () -> Unit,
) = AnimatedContent(
    modifier = modifier,
    targetState = report::class,
    transitionSpec = { (fadeIn() + scaleIn()) togetherWith (fadeOut() + scaleOut()) },
    contentAlignment = Alignment.BottomEnd,
) { stage ->
    when (stage) {
        ImportReport.Review::class -> ExtendedFloatingActionButton(
            onClick = onImport,
            icon = { Icon(painter = painterResource(Res.drawable.ic_import), contentDescription = null) },
            text = { Text(stringResource(Res.string.import_conflicts_confirm)) },
        )

        ImportReport.Finished::class -> ExtendedFloatingActionButton(
            onClick = onDone,
            icon = { Icon(painter = painterResource(Res.drawable.ic_check), contentDescription = null) },
            text = { Text(stringResource(Res.string.done)) },
        )

        else -> Box(Modifier)
    }
}

private val PROGRESS_MAX_WIDTH = 480.dp

/** How many items [reviewHeader] and [finishedHeader] put above the search field, which is where it pins from. */
private const val REVIEW_HEADER_ITEM_COUNT = 2
private const val FINISHED_HEADER_ITEM_COUNT = 1

/** Room under the last row for the floating button to sit beside rather than over it, as the export screen leaves. */
private val FLOATING_BUTTON_CLEARANCE: Dp = 88.dp
