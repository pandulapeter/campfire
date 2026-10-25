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
import com.pandulapeter.campfire.presentation.ui.screens.importReport.ImportReport
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.pandulapeter.campfire.presentation.localization.pluralStringResource
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.done
import com.pandulapeter.campfire.presentation.resources.import_review_tags_subtitle
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel

/**
 * The tags the import under review puts on every song it writes ([TagsSheet]), opened from the review's summary. It is
 * the one-song sheet over a song that carries only these tags, so Done leaves the set exactly as ticked; nothing is
 * written until the import is, which is why it reads Done rather than Save.
 */
@Composable
internal fun ImportTagsSheet(
    viewModel: CampfireViewModel,
    dialog: DialogType.ImportTags,
) {
    val tags = remember(dialog) { (viewModel.importReportState.importReport.value as? ImportReport.Review)?.choices?.tags.orEmpty() }
    TagsSheet(
        viewModel = viewModel,
        tagsPerSong = listOf(tags),
        sheetKey = IMPORT_TAGS_SHEET_KEY,
        subtitle = pluralStringResource(Res.plurals.import_review_tags_subtitle, dialog.songCount, dialog.songCount),
        confirmLabel = stringResource(Res.string.done),
        onDismiss = { viewModel.dialogHost.dismissSheet(dialog) },
        onSave = { draft, spellings -> viewModel.importReportState.setImportTags(draft.onKeys.mapNotNull(spellings::get)) },
    )
}

private const val IMPORT_TAGS_SHEET_KEY = "import"
