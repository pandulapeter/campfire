/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.export

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.preferences.PrintSettings
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.print_format
import com.pandulapeter.campfire.presentation.resources.print_format_chordpro
import com.pandulapeter.campfire.presentation.resources.print_format_link
import com.pandulapeter.campfire.presentation.resources.print_format_link_description
import com.pandulapeter.campfire.presentation.resources.print_format_pdf
import com.pandulapeter.campfire.presentation.resources.print_format_chordpro_description
import com.pandulapeter.campfire.presentation.resources.print_format_pdf_description
import com.pandulapeter.campfire.presentation.resources.print_format_zip
import com.pandulapeter.campfire.presentation.resources.print_format_zip_description
import com.pandulapeter.campfire.presentation.ui.components.ChoiceCard
import com.pandulapeter.campfire.presentation.ui.components.list.LIST_ITEM_KEYLINE
import com.pandulapeter.campfire.presentation.ui.print.PrintSource
import com.pandulapeter.campfire.presentation.ui.settingsPage.SettingsSectionTitle

/**
 * The first of the options: each of [formats] as a card saying in a line whom it suits, since they are told apart by
 * what the person receiving it can do with it rather than by anything the names say. Every card says it at once, rather
 * than only the chosen one, so that the choice is made by reading rather than by trying each; the preview under the cards
 * says more of the chosen one. The link, where offered, comes first:
 * it is how another Campfire user gets the item, and gets it again as it changes.
 */
@Composable
internal fun FormatChoice(
    modifier: Modifier,
    format: PrintSettings.Format,
    formats: List<PrintSettings.Format>,
    kind: PrintSource.Kind,
    onSelected: (PrintSettings.Format) -> Unit,
) = Column(modifier) {
    SettingsSectionTitle(
        text = stringResource(Res.string.print_format),
        contentPadding = PaddingValues(start = LIST_ITEM_KEYLINE, end = LIST_ITEM_KEYLINE, top = 8.dp, bottom = 8.dp),
    )
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = LIST_ITEM_KEYLINE).selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        formats.forEach { option ->
            ChoiceCard(
                modifier = Modifier.fillMaxWidth(),
                title = formatTitle(option, kind),
                description = formatDescription(option, kind),
                isSelected = option == format,
                role = Role.RadioButton,
                onClick = { onSelected(option) },
            )
        }
    }
}

@Composable
private fun formatTitle(format: PrintSettings.Format, kind: PrintSource.Kind) = stringResource(
    when {
        format == PrintSettings.Format.PDF -> Res.string.print_format_pdf
        format == PrintSettings.Format.LINK -> Res.string.print_format_link
        kind != PrintSource.Kind.SONG -> Res.string.print_format_zip
        else -> Res.string.print_format_chordpro
    },
)

/** A line saying whom the format suits; what it holds is the preview's to say, under the cards. */
@Composable
private fun formatDescription(format: PrintSettings.Format, kind: PrintSource.Kind) = stringResource(
    when {
        format == PrintSettings.Format.PDF -> Res.string.print_format_pdf_description
        format == PrintSettings.Format.LINK -> Res.string.print_format_link_description
        kind != PrintSource.Kind.SONG -> Res.string.print_format_zip_description
        else -> Res.string.print_format_chordpro_description
    },
)
