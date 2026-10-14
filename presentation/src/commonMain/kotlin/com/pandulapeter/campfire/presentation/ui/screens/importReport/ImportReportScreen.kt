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

import com.pandulapeter.campfire.presentation.ui.screens.importReport.finished.finishedHeader
import com.pandulapeter.campfire.presentation.ui.screens.importReport.finished.importReportSections
import com.pandulapeter.campfire.presentation.ui.screens.importReport.finished.matching
import com.pandulapeter.campfire.presentation.ui.screens.importReport.finished.sections
import com.pandulapeter.campfire.presentation.ui.screens.importReport.review.hasEntriesToChoose
import com.pandulapeter.campfire.presentation.ui.screens.importReport.review.importReviewSections
import com.pandulapeter.campfire.presentation.ui.screens.importReport.review.importReviewSummary
import com.pandulapeter.campfire.presentation.ui.screens.importReport.review.matchingReview
import com.pandulapeter.campfire.presentation.ui.screens.importReport.review.reviewHeader
import com.pandulapeter.campfire.presentation.ui.screens.importReport.review.reviewSections
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.data.model.domain.ImportChoices
import com.pandulapeter.campfire.data.model.domain.ImportProgress
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.import_failed
import com.pandulapeter.campfire.presentation.resources.import_loading_title
import com.pandulapeter.campfire.presentation.resources.import_progress_downloading
import com.pandulapeter.campfire.presentation.resources.import_progress_title
import com.pandulapeter.campfire.presentation.resources.import_review_demo_library
import com.pandulapeter.campfire.presentation.resources.import_review_from
import com.pandulapeter.campfire.presentation.resources.import_review_title
import com.pandulapeter.campfire.presentation.resources.import_review_title_empty
import com.pandulapeter.campfire.presentation.resources.import_status_stopped
import com.pandulapeter.campfire.presentation.resources.import_status_title
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.only
import com.pandulapeter.campfire.presentation.localization.textResource

/**
 * Every import the user started, on one screen that goes through the import's stages in place rather than as a run of
 * dialogs replacing one another (see `CampfireViewModel.importReport`): the files being read, why they could not be,
 * the review of everything they hold, the import the review decides on being written, and every file of what it came
 * to, grouped by what became of it. It is a screen of the back stack, so a song opened from its list comes back to it,
 * and a dialog that asks for one confirmation goes over it.
 *
 * Leaving it before the import is written drops the import, and needs no confirmation since nothing has been written
 * yet; leaving it while the import is written lets that finish on its own, see `ImportController.onImportReportLeft`.
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
    // Read through the state rather than off the stage, so that a tick recomposes the rows and the counts that read
    // it rather than everything the stage is handed to.
    val choices = remember { { (report as? ImportReport.Review)?.choices ?: ImportChoices() } }
    val plan = (shown as? ImportReport.Review)?.plan
    val reviewSections = remember(plan, songs, setlists) { plan?.let { importReviewSections(it, songs, setlists) }.orEmpty() }
    val matchingReviewSections = remember(reviewSections, query) { reviewSections.matchingReview(query, viewModel.songRenderer::normalizeForSearch) }
    val importCount by remember(plan) { derivedStateOf { plan?.takeIf { it.hasEntriesToChoose }?.let { importReviewSummary(it, choices()).importCount } } }
    val result = (shown as? ImportReport.Finished)?.result
    val resultSections = remember(result, songs, setlists) { result?.let { importReportSections(it, songs, setlists) }.orEmpty() }
    val matchingResultSections = remember(resultSections, query) { resultSections.matching(query, viewModel.songRenderer::normalizeForSearch) }
    val layoutDirection = LocalLayoutDirection.current
    // Narrowed rather than taken apart, so that the keyboard of the search field lays the list out again as it slides
    // instead of recomposing the screen on every frame of it.
    val listPadding = contentPadding.only(bottom = true, extraBottom = FLOATING_BUTTON_CLEARANCE)
    val ime = WindowInsets.ime
    val density = LocalDensity.current
    // Derived, so that the screen recomposes as the keyboard comes and goes rather than on every frame it slides.
    val isKeyboardVisible by remember(ime, density) { derivedStateOf { ime.getBottom(density) > 0 } }
    Column(Modifier.fillMaxSize()) {
        ImportReportTopAppBar(
            title = shown.title(progress),
            subtitle = if ((shown as? ImportReport.Review)?.isDemoLibrary == true) {
                stringResource(Res.string.import_review_demo_library)
            } else {
                shown.sourceName?.let { textResource(Res.string.import_review_from, it) }
            },
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
                // The stage, so that a tick, a result arriving for the same stage, or the library renaming a song in it,
                // is the same list going on rather than a second one fading in over it.
                contentKey = { it::class },
            ) { stage ->
                when (stage) {
                    is ImportReport.Loading -> Box(
                        modifier = Modifier.fillMaxSize().padding(contentPadding.only(bottom = true)),
                        contentAlignment = Alignment.Center,
                    ) {
                        ImportLoadingContent(
                            progress = progress ?: ImportProgress(ImportProgress.Phase.UNPACKING),
                            onCancel = viewModel::cancelImportPreparation,
                        )
                    }

                    is ImportReport.Failed -> Box(
                        modifier = Modifier.fillMaxSize().padding(contentPadding.only(bottom = true)),
                        contentAlignment = Alignment.Center,
                    ) {
                        ImportFailedContent(failure = stage)
                    }

                    is ImportReport.Review -> ReportList(
                        contentPadding = listPadding,
                        searchFieldIndex = REVIEW_HEADER_ITEM_COUNT.takeIf { reviewSections.isNotEmpty() },
                    ) { listState ->
                        reviewHeader(plan = stage.plan, choices = choices)
                        if (reviewSections.isNotEmpty()) searchField(searchState = searchState, listState = listState, index = REVIEW_HEADER_ITEM_COUNT)
                        reviewSections(
                            sections = matchingReviewSections,
                            isFiltered = query.isNotBlank(),
                            choices = choices,
                            onEntriesChosenChange = viewModel::setImportEntriesChosen,
                            onConflictResolutionSelected = viewModel::setImportConflictResolution,
                            onGroupConflictResolutionSelected = viewModel::setImportGroupConflictResolution,
                            onOpenSong = viewModel::openReportedSong,
                        )
                    }

                    ImportReport.Importing -> Box(
                        modifier = Modifier.fillMaxSize().padding(contentPadding.only(bottom = true)),
                        contentAlignment = Alignment.Center,
                    ) {
                        ImportLoadingContent(
                            // The comparison before the first entry is reported is part of writing the import too.
                            progress = progress ?: ImportProgress(ImportProgress.Phase.IMPORTING),
                            onCancel = null,
                        )
                    }

                    is ImportReport.Finished -> ReportList(
                        contentPadding = listPadding,
                        searchFieldIndex = FINISHED_HEADER_ITEM_COUNT.takeIf { resultSections.isNotEmpty() },
                    ) { listState ->
                        finishedHeader(stage.result)
                        if (resultSections.isNotEmpty()) searchField(searchState = searchState, listState = listState, index = FINISHED_HEADER_ITEM_COUNT)
                        sections(
                            sections = matchingResultSections,
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
                importCount = importCount,
                isKeyboardVisible = isKeyboardVisible,
                onImport = viewModel::confirmImport,
                onRetry = viewModel::retryImport,
                onDone = onBack,
            )
        }
    }
}

@Composable
private fun ImportReport.title(progress: ImportProgress?) = stringResource(
    when (this) {
        is ImportReport.Loading -> if (progress?.phase == ImportProgress.Phase.DOWNLOADING) Res.string.import_progress_downloading else Res.string.import_loading_title
        is ImportReport.Failed -> Res.string.import_failed
        is ImportReport.Review -> if (plan.hasEntriesToChoose) Res.string.import_review_title else Res.string.import_review_title_empty
        ImportReport.Importing -> Res.string.import_progress_title
        is ImportReport.Finished -> when {
            result == null -> Res.string.import_failed
            result.isFailed -> Res.string.import_status_stopped
            else -> Res.string.import_status_title
        }
    },
)

/** What the stage is reading or has read, for the subtitle; the result names its own files. */
private val ImportReport.sourceName
    get() = when (this) {
        is ImportReport.Loading -> sourceName
        is ImportReport.Failed -> sourceName
        is ImportReport.Review -> plan.sourceNames.takeIf { it.isNotEmpty() }?.joinToString(", ")
        ImportReport.Importing, is ImportReport.Finished -> null
    }

/** How many items [reviewHeader] and [finishedHeader] put above the search field, which is where it pins from. */
private const val REVIEW_HEADER_ITEM_COUNT = 1
private const val FINISHED_HEADER_ITEM_COUNT = 1

/** Room under the last row for the floating button to sit beside rather than over it, as the export screen leaves. */
private val FLOATING_BUTTON_CLEARANCE: Dp = 88.dp
