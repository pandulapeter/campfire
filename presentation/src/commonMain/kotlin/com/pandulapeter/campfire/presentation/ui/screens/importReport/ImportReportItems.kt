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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.domain.ImportConflictResolution
import com.pandulapeter.campfire.data.model.domain.ImportPlan
import com.pandulapeter.campfire.data.model.domain.ImportResult
import com.pandulapeter.campfire.presentation.localization.pluralStringResource
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_edit
import com.pandulapeter.campfire.presentation.resources.import_conflicts_apply_all
import com.pandulapeter.campfire.presentation.resources.import_conflicts_duplicates
import com.pandulapeter.campfire.presentation.resources.import_conflicts_files
import com.pandulapeter.campfire.presentation.resources.import_conflicts_keep_both
import com.pandulapeter.campfire.presentation.resources.import_conflicts_keep_both_description
import com.pandulapeter.campfire.presentation.resources.import_conflicts_new
import com.pandulapeter.campfire.presentation.resources.import_conflicts_question
import com.pandulapeter.campfire.presentation.resources.import_conflicts_replace
import com.pandulapeter.campfire.presentation.resources.import_conflicts_replace_description
import com.pandulapeter.campfire.presentation.resources.import_conflicts_skip
import com.pandulapeter.campfire.presentation.resources.import_conflicts_skip_description
import com.pandulapeter.campfire.presentation.resources.import_conflicts_skipped
import com.pandulapeter.campfire.presentation.resources.import_conflicts_summary
import com.pandulapeter.campfire.presentation.resources.import_files_empty
import com.pandulapeter.campfire.presentation.resources.import_oversized
import com.pandulapeter.campfire.presentation.resources.import_report_clean
import com.pandulapeter.campfire.presentation.resources.import_report_converted
import com.pandulapeter.campfire.presentation.resources.import_report_edit
import com.pandulapeter.campfire.presentation.resources.import_report_left_out
import com.pandulapeter.campfire.presentation.resources.import_report_search
import com.pandulapeter.campfire.presentation.resources.import_status_duplicates
import com.pandulapeter.campfire.presentation.resources.import_status_failed
import com.pandulapeter.campfire.presentation.resources.import_status_oversized
import com.pandulapeter.campfire.presentation.resources.import_status_partial
import com.pandulapeter.campfire.presentation.resources.import_status_setlists
import com.pandulapeter.campfire.presentation.resources.import_status_skipped
import com.pandulapeter.campfire.presentation.resources.import_status_skipped_conflicts
import com.pandulapeter.campfire.presentation.resources.import_status_songs
import com.pandulapeter.campfire.presentation.resources.import_status_unknown_failure
import com.pandulapeter.campfire.presentation.resources.import_status_unprocessed
import com.pandulapeter.campfire.presentation.resources.import_status_unreadable
import com.pandulapeter.campfire.presentation.resources.import_unreadable_documents
import com.pandulapeter.campfire.presentation.ui.components.EDGE_FADE_SIZE
import com.pandulapeter.campfire.presentation.ui.components.RadioListItem
import com.pandulapeter.campfire.presentation.ui.components.SearchState
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource

/**
 * The search of the list, standing right above the files it narrows and pinned at the top of the list once they are
 * scrolled under it: always there rather than behind a button, since the list is the screen's whole content and a
 * name is what somebody comes to it to find. The rows fade out under it the way they fade out under a bar, by a
 * gradient drawn below the field over what passes under it, as strong as the rows have been scrolled under it.
 */
internal fun LazyListScope.searchField(
    searchState: SearchState,
    listState: LazyListState,
    index: Int,
) = stickyHeader(key = "search", contentType = "search") {
    val backgroundColor = MaterialTheme.colorScheme.background
    val fadeHeight = with(LocalDensity.current) { EDGE_FADE_SIZE.toPx() }
    Box(
        modifier = ItemWidth
            .drawWithContent {
                drawContent()
                val scrolledUnder = when {
                    listState.firstVisibleItemIndex > index -> fadeHeight
                    listState.firstVisibleItemIndex == index -> listState.firstVisibleItemScrollOffset.toFloat()
                    else -> 0f
                }
                val strength = (scrolledUnder / fadeHeight).coerceIn(0f, 1f)
                if (strength > 0f) {
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(backgroundColor, backgroundColor.copy(alpha = 0f)),
                            startY = size.height,
                            endY = size.height + fadeHeight,
                        ),
                        topLeft = Offset(0f, size.height),
                        size = Size(size.width, fadeHeight),
                        alpha = strength,
                    )
                }
            }
            .background(backgroundColor)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        ImportReportSearchField(
            searchState = searchState,
            placeholder = stringResource(Res.string.import_report_search),
        )
    }
}

/**
 * The question first and the names it is about after it, since the names can run to hundreds and the answer is one
 * for all of them. What the import does with everything else is said above the question, so that it is answered in
 * full sight of the rest.
 */
internal fun LazyListScope.reviewHeader(
    summary: ImportPlan.Summary,
    resolution: ImportConflictResolution,
    onResolutionSelected: (ImportConflictResolution) -> Unit,
) {
    item(key = "summary", contentType = "text") {
        Column(
            modifier = ItemWidth.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = pluralStringResource(
                    Res.plurals.import_conflicts_summary,
                    summary.conflictingFileNames.size,
                    summary.conflictingFileNames.size,
                ),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (summary.newSongCount > 0 || summary.newSetlistCount > 0) {
                ReportNote(text = stringResource(Res.string.import_conflicts_new, summary.newSongCount, summary.newSetlistCount))
            }
            if (summary.duplicateCount > 0) {
                ReportNote(text = pluralStringResource(Res.plurals.import_conflicts_duplicates, summary.duplicateCount, summary.duplicateCount))
            }
            if (summary.skippedCount > 0) {
                ReportNote(text = pluralStringResource(Res.plurals.import_conflicts_skipped, summary.skippedCount, summary.skippedCount))
            }
            if (summary.oversizedCount > 0) {
                ReportNote(text = pluralStringResource(Res.plurals.import_oversized, summary.oversizedCount, summary.oversizedCount))
            }
            if (summary.unreadableDocumentCount > 0) {
                ReportNote(text = pluralStringResource(Res.plurals.import_unreadable_documents, summary.unreadableDocumentCount, summary.unreadableDocumentCount))
            }
        }
    }
    item(key = "question", contentType = "question") {
        Column(modifier = ItemWidth.padding(top = 16.dp)) {
            SectionTitle(text = stringResource(Res.string.import_conflicts_question))
            ReportNote(
                modifier = Modifier.padding(horizontal = 16.dp),
                text = stringResource(Res.string.import_conflicts_apply_all),
            )
            ImportConflictResolution.entries.forEach { option ->
                RadioListItem(
                    title = stringResource(option.label),
                    description = stringResource(option.description),
                    isSelected = option == resolution,
                    onSelected = { onResolutionSelected(option) },
                )
            }
        }
    }
}

/** What the import came to in one sentence, above the files it is about. */
internal fun LazyListScope.finishedHeader(result: ImportResult?) {
    item(key = "summary", contentType = "text") {
        val isError = result == null || result.isFailed
        Text(
            modifier = ItemWidth.padding(horizontal = 16.dp),
            text = stringResource(
                when {
                    result == null -> Res.string.import_status_unknown_failure
                    result.isFailed -> Res.string.import_status_partial
                    result.isClean -> Res.string.import_report_clean
                    else -> Res.string.import_report_left_out
                },
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}

private val ImportResult.isClean
    get() = skippedFileNames.isEmpty() && skippedConflictingFileNames.isEmpty() && oversizedFileNames.isEmpty() &&
        unreadableDocumentFileNames.isEmpty()

/**
 * Every section with a heading and a count, and its files under it. A song the library holds opens in a pager over
 * the songs of its section; one that was converted from another format says so, and opens in the editor from its own
 * button outside performance mode, since a conversion is a best attempt worth checking.
 */
internal fun LazyListScope.sections(
    sections: List<ImportReportSection>,
    isFiltered: Boolean,
    isPerformanceModeEnabled: Boolean,
    onOpenSong: (songFileNames: List<String>, index: Int) -> Unit,
    onEditSong: (String) -> Unit,
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
        item(key = "header_${section.kind}", contentType = "header") {
            SectionTitle(
                modifier = ItemWidth.animateItem().padding(top = 16.dp),
                text = stringResource(section.kind.label),
                count = section.rows.size,
            )
        }
        val songFileNames = section.rows.filter { it.isSong }.map { it.fileName }.distinct()
        items(
            items = section.rows,
            key = { row -> "${section.kind}_${row.key}" },
            contentType = { "row" },
        ) { row ->
            ImportReportRowItem(
                modifier = ItemWidth.animateItem(),
                row = row,
                canEdit = row.isConverted && row.isSong && !isPerformanceModeEnabled,
                onClick = if (row.isSong) {
                    { onOpenSong(songFileNames, songFileNames.indexOf(row.fileName).coerceAtLeast(0)) }
                } else {
                    null
                },
                onEdit = { onEditSong(row.fileName) },
            )
        }
    }
}

@Composable
private fun ImportReportRowItem(
    modifier: Modifier = Modifier,
    row: ImportReportRow,
    canEdit: Boolean,
    onClick: (() -> Unit)?,
    onEdit: () -> Unit,
) = ListItem(
    modifier = if (onClick == null) modifier else modifier.clickable(onClick = onClick),
    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    headlineContent = {
        Text(
            text = row.title ?: row.fileName,
            maxLines = 1,
            overflow = if (row.title == null) TextOverflow.MiddleEllipsis else TextOverflow.Ellipsis,
        )
    },
    supportingContent = listOfNotNull(
        row.subtitle,
        if (row.isConverted) stringResource(Res.string.import_report_converted) else null,
    ).takeIf { it.isNotEmpty() }?.let { lines ->
        {
            Text(
                text = lines.joinToString(" · "),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    },
    trailingContent = if (canEdit) {
        {
            IconButton(onClick = onEdit) {
                Icon(
                    painter = painterResource(Res.drawable.ic_edit),
                    contentDescription = stringResource(Res.string.import_report_edit),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    } else {
        null
    },
)

private val ImportReportSection.Kind.label: StringResource
    get() = when (this) {
        ImportReportSection.Kind.CONFLICTS -> Res.string.import_conflicts_files
        ImportReportSection.Kind.FAILED -> Res.string.import_status_failed
        ImportReportSection.Kind.UNPROCESSED -> Res.string.import_status_unprocessed
        ImportReportSection.Kind.SKIPPED_CONFLICTS -> Res.string.import_status_skipped_conflicts
        ImportReportSection.Kind.SKIPPED -> Res.string.import_status_skipped
        ImportReportSection.Kind.OVERSIZED -> Res.string.import_status_oversized
        ImportReportSection.Kind.UNREADABLE -> Res.string.import_status_unreadable
        ImportReportSection.Kind.SONGS -> Res.string.import_status_songs
        ImportReportSection.Kind.SETLISTS -> Res.string.import_status_setlists
        ImportReportSection.Kind.DUPLICATES -> Res.string.import_status_duplicates
    }

private val ImportConflictResolution.label
    get() = when (this) {
        ImportConflictResolution.KEEP_BOTH -> Res.string.import_conflicts_keep_both
        ImportConflictResolution.REPLACE -> Res.string.import_conflicts_replace
        ImportConflictResolution.SKIP -> Res.string.import_conflicts_skip
    }

private val ImportConflictResolution.description
    get() = when (this) {
        ImportConflictResolution.KEEP_BOTH -> Res.string.import_conflicts_keep_both_description
        ImportConflictResolution.REPLACE -> Res.string.import_conflicts_replace_description
        ImportConflictResolution.SKIP -> Res.string.import_conflicts_skip_description
    }
