/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.importReport.review

import com.pandulapeter.campfire.presentation.ui.components.WarningCard
import com.pandulapeter.campfire.presentation.ui.screens.importReport.ImportFeatureWarning
import com.pandulapeter.campfire.presentation.ui.screens.importReport.ItemWidth
import com.pandulapeter.campfire.presentation.ui.screens.importReport.text
import com.pandulapeter.campfire.presentation.ui.screens.importReport.ReportNote
import com.pandulapeter.campfire.presentation.ui.screens.importReport.finished.matching
import com.pandulapeter.campfire.presentation.ui.screens.importReport.finished.sections
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.importing.ImportChoices
import com.pandulapeter.campfire.data.model.importing.ImportConflictResolution
import com.pandulapeter.campfire.data.model.importing.ImportEntryKey
import com.pandulapeter.campfire.data.model.importing.ImportPlan
import com.pandulapeter.campfire.presentation.localization.pluralStringResource
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.import_files_empty
import com.pandulapeter.campfire.presentation.resources.import_review_conflicts
import com.pandulapeter.campfire.presentation.resources.import_review_setlists
import com.pandulapeter.campfire.presentation.resources.import_review_shared_updates
import com.pandulapeter.campfire.presentation.resources.import_review_songs
import com.pandulapeter.campfire.presentation.resources.import_status_duplicates
import com.pandulapeter.campfire.presentation.resources.import_status_oversized
import com.pandulapeter.campfire.presentation.resources.import_status_skipped
import com.pandulapeter.campfire.presentation.resources.import_status_unreadable
import org.jetbrains.compose.resources.StringResource

/**
 * What Import would do, above everything the files hold, so that the counts are read before the list and change in
 * sight as the boxes are ticked. Reads the choices through a derived state, so that a tick recomposes the card alone.
 *
 * @param sharedLinkUpdateCount How many names already taken a shared link's review started on Replace rather than Keep
 *   both, being files the same link wrote here before: a line under the counts, above the names it is about, says so and
 *   what Keep both is for.
 * @param onEditTags Opens the tag sheet of the songs the import writes, how many as the ticks stand; the tile it is opened
 *   from is there only where the import has a song it could write.
 */
internal fun LazyListScope.reviewHeader(
    plan: ImportPlan,
    choices: () -> ImportChoices,
    warning: ImportFeatureWarning?,
    sharedLinkUpdateCount: Int,
    onEditTags: (songCount: Int) -> Unit,
) = item(key = "summary", contentType = "summary") {
    val summary by remember(plan) { derivedStateOf { importReviewSummary(plan, choices()) } }
    val tags by remember { derivedStateOf { choices().tags } }
    Column(
        modifier = ItemWidth.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        warning?.let { WarningCard(text = it.text()) }
        ImportReviewSummaryCard(modifier = Modifier.fillMaxWidth(), summary = summary)
        if (plan.hasSongsToChoose) {
            ImportReviewTags(tags = tags, onEdit = { onEditTags(summary.songCount) })
        }
        if (sharedLinkUpdateCount > 0) {
            ReportNote(text = pluralStringResource(Res.plurals.import_review_shared_updates, sharedLinkUpdateCount, sharedLinkUpdateCount))
        }
    }
}

/**
 * Every group of the review with its heading, its box, and its rows. A group's box, and the choice for all of them of
 * the names already taken, act on the rows it shows, which is only the matching ones while the search narrows them, so
 * that unticking a group under a search for "live" leaves the rest of it alone.
 */
internal fun LazyListScope.reviewSections(
    sections: List<ImportReviewSection>,
    isFiltered: Boolean,
    choices: () -> ImportChoices,
    onEntriesChosenChange: (Collection<ImportEntryKey>, Boolean) -> Unit,
    onConflictResolutionSelected: (ImportEntryKey, ImportConflictResolution) -> Unit,
    onGroupConflictResolutionSelected: (keys: Collection<ImportEntryKey>, isFiltered: Boolean, ImportConflictResolution) -> Unit,
    onOpenSong: ((songFileNames: List<String>, index: Int) -> Unit)?,
) {
    if (isFiltered && sections.isEmpty()) {
        item(key = "empty", contentType = "text") {
            ReportNote(
                modifier = ItemWidth.animateItem().padding(horizontal = 16.dp, vertical = 24.dp),
                text = stringResource(Res.string.import_files_empty),
            )
        }
    }
    sections.forEach { section ->
        val keys = section.rows.mapNotNull { it.key }
        val isConflicts = section.kind == ImportReviewSection.Kind.CONFLICTS
        item(key = "header_${section.kind}", contentType = if (isConflicts) "conflictsHeader" else "header") {
            val boxState by remember(section.rows) { derivedStateOf { groupBoxState(section.rows, choices()) } }
            val resolution by remember(section.rows) { derivedStateOf { groupConflictResolution(section.rows, choices()) } }
            ImportReviewSectionHeader(
                modifier = ItemWidth.animateItem().padding(top = 16.dp),
                title = stringResource(section.kind.label),
                count = section.rows.size,
                boxState = boxState.takeIf { keys.isNotEmpty() },
                onBoxClick = { onEntriesChosenChange(keys, boxState != ToggleableState.On) },
                isConflicts = isConflicts,
                resolution = resolution,
                onResolutionSelected = { onGroupConflictResolutionSelected(keys, isFiltered, it) },
            )
        }
        // A library song opened from a row is read in a pager over the songs of its group the library holds.
        val librarySongFileNames = section.rows.filter { it.isSong && it.libraryTitle != null }.mapNotNull { it.libraryFileName }.distinct()
        items(
            items = section.rows,
            key = { it.rowKey },
            contentType = { if (it.key == null) "infoRow" else if (isConflicts) "conflictRow" else "row" },
        ) { row ->
            ImportReviewRowItem(
                modifier = ItemWidth.animateItem(),
                row = row,
                isConflict = isConflicts,
                choices = choices,
                onChosenChange = { key, isChosen -> onEntriesChosenChange(listOf(key), isChosen) },
                onResolutionSelected = onConflictResolutionSelected,
                onOpenLibrarySong = onOpenSong?.let { open -> { fileName -> open(librarySongFileNames, librarySongFileNames.indexOf(fileName).coerceAtLeast(0)) } },
            )
        }
    }
}

private val ImportReviewSection.Kind.label: StringResource
    get() = when (this) {
        ImportReviewSection.Kind.CONFLICTS -> Res.string.import_review_conflicts
        ImportReviewSection.Kind.SONGS -> Res.string.import_review_songs
        ImportReviewSection.Kind.SETLISTS -> Res.string.import_review_setlists
        ImportReviewSection.Kind.DUPLICATES -> Res.string.import_status_duplicates
        ImportReviewSection.Kind.SKIPPED -> Res.string.import_status_skipped
        ImportReviewSection.Kind.OVERSIZED -> Res.string.import_status_oversized
        ImportReviewSection.Kind.UNREADABLE -> Res.string.import_status_unreadable
    }
