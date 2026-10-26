/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.data.model.song.Song
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.navigation.ImportPreview
import com.pandulapeter.campfire.presentation.ui.screens.importReport.ImportReport
import com.pandulapeter.campfire.presentation.ui.state.importing.ImportPreviewActions
import com.pandulapeter.campfire.presentation.ui.state.importing.ImportPreviewPage
import com.pandulapeter.campfire.presentation.ui.state.importing.importPreviewActionsOf
import com.pandulapeter.campfire.presentation.ui.state.importing.importPreviewFileName
import com.pandulapeter.campfire.presentation.ui.state.importing.importPreviewSongIndices
import com.pandulapeter.campfire.presentation.ui.state.importing.importPreviewSongOf

/**
 * What the song details screen shows as a preview of songs an import has not written yet ([CampfireDestination.SongDetails.importPreview]):
 * its pages, drawn from the plan's text rather than from the library, the review they belong to, and what the preview
 * keeps in memory that a song of the library keeps in the preferences by its file name.
 *
 * @param review The review the pages are of, as it was last seen: while it is being written the report says only that,
 *   and the preview keeps showing what is being written.
 * @param isWriting Whether the import the preview's button started is being written, which the button shows.
 * @param folds The sections folded on each page, by its page name: a fold is a preference kept by file name, and the
 *   page names no file of the library, so the preview keeps its own for as long as it is open.
 */
internal class ImportPreviewState(
    val preview: ImportPreview,
    val pages: List<ImportPreviewPage>,
    val review: ImportReport.Review,
    val isWriting: Boolean,
    val folds: SnapshotStateMap<String, Set<String>>,
) {
    val songs: List<Song> = pages.map { it.song }

    /** The text of each page, by its page name, which the pages read in place of the library's. */
    val texts: Map<String, String> = pages.associate { it.pageFileName to it.entry.text }

    val actions: ImportPreviewActions? = importPreviewActionsOf(review, isReviewPager = preview.isReviewPager)

    fun toggleFold(pageFileName: String, key: String) {
        val folded = folds[pageFileName].orEmpty()
        folds[pageFileName] = if (key in folded) folded - key else folded + key
    }
}

/**
 * The preview [destination] is, or null where it is a song of the library. Each page's song is read from its text once
 * and kept for as long as the screen is, since a review joined by another batch lists every page again.
 */
@Composable
internal fun rememberImportPreviewState(
    viewModel: CampfireViewModel,
    destination: CampfireDestination.SongDetails,
): ImportPreviewState? {
    val report by viewModel.importReportState.importReport.collectAsStateWithLifecycle()
    val lastReview = remember { LastReview() }
    (report as? ImportReport.Review)?.takeIf { it.id == destination.importPreview?.reviewId }?.let { lastReview.review = it }
    val folds = remember { mutableStateMapOf<String, Set<String>>() }
    val songs = remember { mutableMapOf<Int, Pair<String, Song>>() }
    val preview = destination.importPreview ?: return null
    val review = lastReview.review ?: return null
    val pages = remember(review.plan, preview) {
        importPreviewSongIndices(preview, review.plan).map { index ->
            val entry = review.plan.songs[index]
            val song = songs[index]?.takeIf { it.first == entry.text }?.second
                ?: importPreviewSongOf(entry, importPreviewFileName(index)).also { songs[index] = entry.text to it }
            ImportPreviewPage(index = index, entry = entry, song = song)
        }
    }
    return remember(preview, pages, review, report) {
        ImportPreviewState(
            preview = preview,
            pages = pages,
            review = review,
            isWriting = report == ImportReport.Importing,
            folds = folds,
        )
    }
}

/**
 * Closes [destination] where it is a preview with nothing to show ([importPreview] is null): one gone forward into, or
 * restored, over a review that is not the one it was opened over, or over none, as a song of the library with no songs
 * closes itself.
 */
@Composable
internal fun CloseImportPreviewWithoutReview(
    viewModel: CampfireViewModel,
    destination: CampfireDestination.SongDetails,
    importPreview: ImportPreviewState?,
    onBack: () -> Unit,
) {
    val isOrphaned = destination.importPreview != null && importPreview == null
    LaunchedEffect(isOrphaned) {
        // A preview of one song composed afresh while it is being written (an Android activity recreated by a change its
        // configChanges do not cover) has forgotten its review, and stays until followImportedSong rewrites it: popping it
        // would be taken by applyImportPlan for the import screen being left.
        if (isOrphaned &&
            viewModel.importReportState.importReport.value != ImportReport.Importing &&
            viewModel.navigator.backStack.lastOrNull() == destination
        ) {
            onBack()
        }
    }
}

/** Not state: written while composing, and read by the same composition. */
private class LastReview(var review: ImportReport.Review? = null)

/**
 * Works out the key the chords of the page being read and of those beside it are in, which the read only line names for
 * a song whose text names none, as the library's songs have it worked out as their text is read (`InferredSongKeys`).
 */
@Composable
internal fun InferImportPreviewKeys(
    viewModel: CampfireViewModel,
    importPreview: ImportPreviewState,
    currentPage: Int,
) = LaunchedEffect(importPreview.pages, currentPage) {
    viewModel.songTextStore.inferredKeys.inferFor(
        listOfNotNull(importPreview.pages.getOrNull(currentPage - 1), importPreview.pages.getOrNull(currentPage), importPreview.pages.getOrNull(currentPage + 1))
            .map { it.pageFileName to it.entry.text },
    )
}

/**
 * The key each page of the pager goes by: its song's file name, but for the screen a preview of one song became once
 * the song was written, which keeps the key the preview's page had. The pager then keeps the page itself, scrolled where
 * it was, and only what changed on it changes, rather than a new page taking the place of the one being read.
 */
@Composable
internal fun rememberPageKeys(songs: List<Song>, isImportPreview: Boolean): (Song) -> String {
    val previewPage = remember { LastPreviewPage() }
    if (isImportPreview) previewPage.fileName = songs.singleOrNull()?.fileName
    val alias = previewPage.fileName?.takeIf { !isImportPreview }
    val imported = alias?.let { songs.singleOrNull()?.fileName }
    return remember(alias, imported) { { song -> if (alias != null && song.fileName == imported) alias else song.fileName } }
}

/** Not state, for the same reason as [LastReview]. */
private class LastPreviewPage(var fileName: String? = null)

/** [value], or the last one that was not null while it is: what something on its way out still draws. */
@Composable
internal fun <T : Any> rememberLast(value: T?): T? {
    val holder = remember { LastValue<T>() }
    if (value != null) holder.value = value
    return holder.value
}

/** Not state: written while composing, and read by the same composition. */
private class LastValue<T : Any>(var value: T? = null)
