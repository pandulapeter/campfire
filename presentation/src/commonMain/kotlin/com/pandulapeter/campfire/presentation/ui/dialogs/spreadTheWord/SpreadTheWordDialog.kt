/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs.spreadTheWord

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.author_photo
import com.pandulapeter.campfire.presentation.resources.close
import com.pandulapeter.campfire.presentation.resources.settings_share_app
import com.pandulapeter.campfire.presentation.resources.spread_the_word_message
import com.pandulapeter.campfire.presentation.resources.spread_the_word_message_without_rating
import com.pandulapeter.campfire.presentation.resources.spread_the_word_rate
import com.pandulapeter.campfire.presentation.resources.spread_the_word_title
import com.pandulapeter.campfire.presentation.ui.components.bounceVerticalScroll
import com.pandulapeter.campfire.presentation.ui.components.fadingVerticalEdges
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource

/**
 * The developer's own note asking a regular user to tell a musician friend about Campfire, the same for everyone rather
 * than gated on whether they enjoy it, which Play's policy forbids. It makes one ask, so it offers no donation. Every
 * button closes it, since it never comes back; Settings → About keeps the Share and Rate rows for later.
 *
 * @param rateStoreName The store a rating is left in, or null where the dialog may not ask for one, and its text says
 *   nothing about rating either.
 */
@Composable
internal fun SpreadTheWordDialog(
    rateStoreName: StringResource?,
    onShare: () -> Unit,
    onRate: () -> Unit,
    onDismiss: () -> Unit,
) = AlertDialog(
    modifier = Modifier.widthIn(max = 480.dp),
    onDismissRequest = onDismiss,
    title = { SpreadTheWordTitle() },
    text = {
        val scrollState = rememberScrollState()
        Text(
            modifier = Modifier.fadingVerticalEdges(scrollState).bounceVerticalScroll(scrollState),
            text = stringResource(if (rateStoreName == null) Res.string.spread_the_word_message_without_rating else Res.string.spread_the_word_message),
        )
    },
    // Both slots are laid out in one flowing row, so the buttons wrap under one another where the words are long.
    dismissButton = {
        TextButton(onClick = onDismiss) { Text(stringResource(Res.string.close)) }
        if (rateStoreName != null) {
            TextButton(onClick = onRate) { Text(stringResource(Res.string.spread_the_word_rate, stringResource(rateStoreName))) }
        }
    },
    confirmButton = {
        Button(onClick = onShare) { Text(stringResource(Res.string.settings_share_app)) }
    },
)

/** The author's photo beside the title, which is what makes the note read as one person's rather than the app's. */
@Composable
private fun SpreadTheWordTitle() = Row(
    horizontalArrangement = Arrangement.spacedBy(16.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    // The title names the person in it, so the photo says nothing more to a screen reader.
    Image(
        modifier = Modifier.size(56.dp).clip(CircleShape),
        painter = painterResource(Res.drawable.author_photo),
        contentDescription = null,
        contentScale = ContentScale.Crop,
    )
    Text(stringResource(Res.string.spread_the_word_title))
}
