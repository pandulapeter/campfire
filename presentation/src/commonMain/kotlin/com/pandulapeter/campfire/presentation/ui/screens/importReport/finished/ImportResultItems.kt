/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.importReport.finished

import com.pandulapeter.campfire.presentation.ui.components.WarningCard
import com.pandulapeter.campfire.presentation.ui.screens.importReport.ImportFeatureWarning
import com.pandulapeter.campfire.presentation.ui.screens.importReport.ItemWidth
import com.pandulapeter.campfire.presentation.ui.screens.importReport.text
import com.pandulapeter.campfire.presentation.ui.screens.importReport.ReportNote
import com.pandulapeter.campfire.presentation.ui.screens.importReport.SectionTitle
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
import com.pandulapeter.campfire.data.model.importing.ImportResult
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_edit
import com.pandulapeter.campfire.presentation.resources.import_files_empty
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
import com.pandulapeter.campfire.presentation.resources.import_status_left_out
import com.pandulapeter.campfire.presentation.resources.import_status_songs
import com.pandulapeter.campfire.presentation.resources.import_status_unknown_failure
import com.pandulapeter.campfire.presentation.resources.import_status_unprocessed
import com.pandulapeter.campfire.presentation.resources.import_status_unreadable
import com.pandulapeter.campfire.presentation.ui.components.EDGE_FADE_SIZE
import com.pandulapeter.campfire.presentation.ui.components.SearchState
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource

/** What the import came to in one sentence, above the files it is about. */
internal fun LazyListScope.finishedHeader(
    result: ImportResult?,
    warning: ImportFeatureWarning?,
) {
    item(key = "summary", contentType = "text") {
        val isError = result == null || result.isFailed
        Column(
            modifier = ItemWidth.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            warning?.let { WarningCard(text = it.text()) }
            Text(
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
}

private val ImportResult.isClean
    get() = skippedFileNames.isEmpty() && leftOutFileNames.isEmpty() && oversizedFileNames.isEmpty() &&
        unreadableDocumentFileNames.isEmpty()

/**
 * Every section with a heading and a count, and its files under it. A song the library holds opens in a pager over
 * the songs of its section; one that was converted from another format says so, and opens in the editor from its own
 * button outside performance mode, since a conversion is a best attempt worth checking. With the songs switched off
 * there is nowhere to open one, and [onOpenSong] is null.
 */
internal fun LazyListScope.sections(
    sections: List<ImportReportSection>,
    isFiltered: Boolean,
    isPerformanceModeEnabled: Boolean,
    onOpenSong: ((songFileNames: List<String>, index: Int) -> Unit)?,
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
                canEdit = row.isConverted && row.isSong && !isPerformanceModeEnabled && onOpenSong != null,
                onClick = if (row.isSong && onOpenSong != null) {
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
        ImportReportSection.Kind.FAILED -> Res.string.import_status_failed
        ImportReportSection.Kind.UNPROCESSED -> Res.string.import_status_unprocessed
        ImportReportSection.Kind.LEFT_OUT -> Res.string.import_status_left_out
        ImportReportSection.Kind.SKIPPED -> Res.string.import_status_skipped
        ImportReportSection.Kind.OVERSIZED -> Res.string.import_status_oversized
        ImportReportSection.Kind.UNREADABLE -> Res.string.import_status_unreadable
        ImportReportSection.Kind.SONGS -> Res.string.import_status_songs
        ImportReportSection.Kind.SETLISTS -> Res.string.import_status_setlists
        ImportReportSection.Kind.DUPLICATES -> Res.string.import_status_duplicates
    }
