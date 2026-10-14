/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs.sheet

import com.pandulapeter.campfire.presentation.ui.dialogs.label
import com.pandulapeter.campfire.presentation.ui.dialogs.songLabel
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.close
import com.pandulapeter.campfire.presentation.resources.ic_clear
import com.pandulapeter.campfire.presentation.ui.components.only
import org.jetbrains.compose.resources.painterResource

/**
 * The top of every sheet: what it is about, and a close button. A sheet whose list grows past the screen covers the
 * whole of it once it is dragged up, and a sheet that fills the screen has no scrim left to tap and no edge that looks
 * like it could be dragged back down, so the button is on the short sheets too, where the next one opened may not be
 * short. A sheet about one song or one setlist names it in the subtitle ([songLabel], or the setlist's title), even
 * where the screen behind it is that very song: it is opened from rows of other lists as readily as from the thing
 * itself, and one that named what its boxes are about only some of the time would read as two different sheets.
 */
@Composable
internal fun SheetHeader(
    title: String,
    subtitle: String,
    actions: (@Composable RowScope.(close: () -> Unit) -> Unit)?,
    onClose: () -> Unit,
    onActionDone: () -> Unit,
) = Row(
    // An action at the end sits as far from the edge as the close button does from the start.
    modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = if (actions == null) 16.dp else 4.dp, top = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    IconButton(onClick = onClose) {
        Icon(
            painter = painterResource(Res.drawable.ic_clear),
            contentDescription = stringResource(Res.string.close),
        )
    }
    Column(
        modifier = Modifier.weight(1f),
    ) {
        Text(
            modifier = Modifier.semantics { heading() },
            text = title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    if (actions != null) {
        // Header actions need the same readable size as the title while retaining button label weight and spacing.
        val typography = MaterialTheme.typography
        val actionTypography = remember(typography) {
            typography.copy(labelLarge = typography.labelLarge.copy(fontSize = 16.sp, lineHeight = 24.sp))
        }
        MaterialTheme(typography = actionTypography) { actions(onActionDone) }
    }
}
