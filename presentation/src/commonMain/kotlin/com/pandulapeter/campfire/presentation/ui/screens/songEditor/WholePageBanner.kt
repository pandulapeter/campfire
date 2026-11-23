/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songEditor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_clear
import com.pandulapeter.campfire.presentation.resources.song_editor_keep_cropped
import com.pandulapeter.campfire.presentation.resources.song_editor_page_cropped
import com.pandulapeter.campfire.presentation.resources.song_editor_use_whole_page
import com.pandulapeter.campfire.presentation.ui.components.CampfireIconButton
import com.pandulapeter.campfire.presentation.ui.components.only
import com.pandulapeter.campfire.presentation.ui.state.editor.WholePageOffer
import org.jetbrains.compose.resources.painterResource

/**
 * **Use the whole page**, under the editor's bar while [offer] stands: a new song from a page whose clutter the import
 * cropped. It is there from the editor's first frame, since the offer is made before the editor opens, and slides away
 * once used, dismissed, or the text is edited.
 *
 * A card in [com.pandulapeter.campfire.presentation.ui.components.WarningCard]'s colors and shape, so it stands apart
 * from the bar and the field, edged like the field's text. Its height is one button row, since a pasted song opens with
 * the keyboard up; [isCompact] (typing in a short window) takes the gaps around it too. Under the toolbar, whose own
 * bottom padding already matches the gap below the card and the preview's top padding together, [isUnderToolbar] takes
 * the gap above it, so the card sits as far from the buttons as from the preview's first card.
 */
@Composable
internal fun ColumnScope.WholePageBanner(
    offer: WholePageOffer,
    contentPadding: PaddingValues,
    isCompact: Boolean,
    isUnderToolbar: Boolean,
) {
    val wholePageDraft by offer.wholePageDraft.collectAsStateWithLifecycle()
    val gap by animateDpAsState(if (isCompact) 0.dp else 8.dp)
    val topGap by animateDpAsState(if (isCompact || isUnderToolbar) 0.dp else 4.dp)
    AnimatedVisibility(visible = wholePageDraft != null) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding.only(start = true, end = true))
                .padding(start = 16.dp, end = 16.dp, top = topGap, bottom = gap),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                    text = stringResource(Res.string.song_editor_page_cropped),
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = offer::use) { Text(stringResource(Res.string.song_editor_use_whole_page)) }
                CampfireIconButton(
                    icon = painterResource(Res.drawable.ic_clear),
                    label = stringResource(Res.string.song_editor_keep_cropped),
                    onClick = offer::dismiss,
                )
            }
        }
    }
}
