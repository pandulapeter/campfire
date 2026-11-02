/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs.labels

import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.state.importing.ImportReport
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.presentation.localization.pluralTextResource
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.done
import com.pandulapeter.campfire.presentation.resources.import_review_tags_subtitle
import com.pandulapeter.campfire.data.model.song.Tag
import com.pandulapeter.campfire.presentation.ui.rendering.SongRenderer
import com.pandulapeter.campfire.presentation.ui.state.PreferencesController
import com.pandulapeter.campfire.presentation.ui.state.importing.ImportReportState
import kotlinx.coroutines.flow.StateFlow
import com.pandulapeter.campfire.presentation.ui.state.importing.importReviewSummary

/**
 * The tags the import under review puts on every song it writes ([TagsSheet]), opened from the review's summary. It is
 * the one-song sheet over a song that carries only these tags, so Done leaves the set exactly as ticked; nothing is
 * written until the import is, which is why it reads Done rather than Save.
 */
@Composable
internal fun ImportTagsSheet(
    libraryTags: StateFlow<List<Tag>>,
    preferencesController: PreferencesController,
    songRenderer: SongRenderer,
    importReportState: ImportReportState,
    onDismiss: () -> Unit,
    dialog: DialogType.ImportTags,
) {
    val tags = remember(dialog) { (importReportState.importReport.value as? ImportReport.Review)?.choices?.tags.orEmpty() }
    // Counted from the live review rather than when the sheet was opened, since a batch joining the review meanwhile
    // adds songs these tags go onto.
    val report by importReportState.importReport.collectAsStateWithLifecycle()
    val songCount = remember(report) { (report as? ImportReport.Review)?.let { importReviewSummary(it.plan, it.choices).songCount } ?: 0 }
    TagsSheet(
        libraryTagsFlow = libraryTags,
        preferencesController = preferencesController,
        songRenderer = songRenderer,
        tagsPerSong = listOf(tags),
        sheetKey = IMPORT_TAGS_SHEET_KEY,
        subtitle = pluralTextResource(Res.plurals.import_review_tags_subtitle, songCount, songCount.toString()),
        confirmLabel = stringResource(Res.string.done),
        onDismiss = onDismiss,
        onSave = { draft, spellings -> importReportState.setImportTags(draft.onKeys.mapNotNull(spellings::get)) },
    )
}

private const val IMPORT_TAGS_SHEET_KEY = "import"
