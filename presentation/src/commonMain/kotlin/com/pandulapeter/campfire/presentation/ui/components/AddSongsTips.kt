/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.add_songs_tip_import_hint
import com.pandulapeter.campfire.presentation.resources.add_songs_tip_import_title
import com.pandulapeter.campfire.presentation.resources.add_songs_tip_paste_hint
import com.pandulapeter.campfire.presentation.resources.add_songs_tip_paste_title
import com.pandulapeter.campfire.presentation.resources.ic_content_paste
import com.pandulapeter.campfire.presentation.resources.ic_import
import com.pandulapeter.campfire.presentation.resources.import_files
import com.pandulapeter.campfire.presentation.resources.songs_paste_from_clipboard
import org.jetbrains.compose.resources.painterResource

/**
 * The two ways most people fill their library, each with a button that does it: pasting a song copied off any web page,
 * which the paste converter turns into ChordPro however it was laid out, and importing what another songbook app
 * exports. Shown as a card at the end of a library too small to be anybody's own yet.
 *
 * @param isEnabled False disables both buttons, for while an import runs or performance mode is on.
 */
@Composable
internal fun AddSongsTips(
    modifier: Modifier = Modifier,
    isEnabled: Boolean,
    onPaste: () -> Unit,
    onImport: () -> Unit,
) = Column(
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(20.dp),
) {
    AddSongsTip(
        icon = painterResource(Res.drawable.ic_content_paste),
        title = stringResource(Res.string.add_songs_tip_paste_title),
        hint = stringResource(Res.string.add_songs_tip_paste_hint),
        actionText = stringResource(Res.string.songs_paste_from_clipboard),
        isEnabled = isEnabled,
        onClick = onPaste,
    )
    AddSongsTip(
        icon = painterResource(Res.drawable.ic_import),
        title = stringResource(Res.string.add_songs_tip_import_title),
        hint = stringResource(Res.string.add_songs_tip_import_hint),
        actionText = stringResource(Res.string.import_files),
        isEnabled = isEnabled,
        onClick = onImport,
    )
}

@Composable
private fun AddSongsTip(
    icon: Painter,
    title: String,
    hint: String,
    actionText: String,
    isEnabled: Boolean,
    onClick: () -> Unit,
) = Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(16.dp),
) {
    Icon(
        modifier = Modifier.padding(top = 2.dp).size(24.dp),
        painter = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
    )
    Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = hint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            modifier = Modifier.padding(top = 4.dp),
            enabled = isEnabled,
            onClick = onClick,
        ) {
            Text(actionText)
        }
    }
}
