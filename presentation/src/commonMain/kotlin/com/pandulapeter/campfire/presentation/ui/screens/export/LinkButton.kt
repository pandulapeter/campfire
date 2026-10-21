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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.cancel
import com.pandulapeter.campfire.presentation.resources.ic_clear
import com.pandulapeter.campfire.presentation.resources.ic_cloud
import com.pandulapeter.campfire.presentation.resources.ic_duplicate
import com.pandulapeter.campfire.presentation.resources.ic_link
import com.pandulapeter.campfire.presentation.resources.ic_refresh
import com.pandulapeter.campfire.presentation.resources.settings_sync_connect_dropbox
import com.pandulapeter.campfire.presentation.resources.share_connect_again
import com.pandulapeter.campfire.presentation.resources.share_copy
import com.pandulapeter.campfire.presentation.resources.share_create
import com.pandulapeter.campfire.presentation.resources.share_try_again
import org.jetbrains.compose.resources.painterResource

/**
 * The Link format's floating action button, in [SaveButton]'s place: what the state asks for next, its label and icon
 * crossfading as one state follows another, and leaving where there is nothing to do. It is described by the label it
 * shows, since Material's extended floating button clears the semantics of its text.
 */
@Composable
internal fun LinkButton(
    modifier: Modifier,
    action: LinkAction?,
    onClick: (LinkAction) -> Unit,
) {
    // Kept while the button leaves, so that it fades out showing what it last offered rather than an empty label.
    var shown by remember { mutableStateOf(action) }
    if (action != null) shown = action
    AnimatedVisibility(action != null, modifier = modifier, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
        val current = shown ?: return@AnimatedVisibility
        val label = stringResource(current.label)
        ExtendedFloatingActionButton(
            onClick = { action?.let(onClick) },
            modifier = Modifier.semantics { contentDescription = label },
        ) {
            AnimatedContent(
                targetState = current,
                transitionSpec = { fadeIn() togetherWith fadeOut() using SizeTransform(clip = false) },
            ) { shownAction ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painter = painterResource(shownAction.icon), contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(shownAction.label))
                }
            }
        }
    }
}

private val LinkAction.label
    get() = when (this) {
        LinkAction.CONNECT -> Res.string.settings_sync_connect_dropbox
        LinkAction.CONNECT_AGAIN -> Res.string.share_connect_again
        LinkAction.CANCEL -> Res.string.cancel
        LinkAction.CREATE -> Res.string.share_create
        LinkAction.TRY_AGAIN -> Res.string.share_try_again
        LinkAction.COPY -> Res.string.share_copy
    }

private val LinkAction.icon
    get() = when (this) {
        LinkAction.CONNECT, LinkAction.CONNECT_AGAIN -> Res.drawable.ic_cloud
        LinkAction.CANCEL -> Res.drawable.ic_clear
        LinkAction.CREATE -> Res.drawable.ic_link
        LinkAction.TRY_AGAIN -> Res.drawable.ic_refresh
        LinkAction.COPY -> Res.drawable.ic_duplicate
    }
