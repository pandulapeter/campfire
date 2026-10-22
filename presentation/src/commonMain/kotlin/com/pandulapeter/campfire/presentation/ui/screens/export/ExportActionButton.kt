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
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.pandulapeter.campfire.presentation.resources.ic_save
import com.pandulapeter.campfire.presentation.resources.print_save
import com.pandulapeter.campfire.presentation.resources.settings_sync_connect_dropbox
import com.pandulapeter.campfire.presentation.resources.share_connect_again
import com.pandulapeter.campfire.presentation.resources.share_copy
import com.pandulapeter.campfire.presentation.resources.share_create
import com.pandulapeter.campfire.presentation.resources.share_try_again
import com.pandulapeter.campfire.presentation.ui.print.PdfExportProgress
import org.jetbrains.compose.resources.painterResource

/**
 * What the screen's one floating button offers: a PDF's or the files' Save, Cancel while a PDF's pages are drawn (their
 * count in a ring in place of the icon), or the Link format's next step.
 */
internal sealed interface ExportButtonAction {

    data class Save(val progress: PdfExportProgress?) : ExportButtonAction

    data class Link(val action: LinkAction) : ExportButtonAction
}

/**
 * The screen's floating action button, one for every format: a change of the format, or of what the format asks for
 * next, crossfades its icon and label inside the button, whose size follows them, rather than taking one button away
 * and bringing another, and it leaves only where there is nothing to do ([FloatingControlVisibility]). It is described
 * by the label it shows, since Material's extended floating button clears the semantics of its text, and so it is still
 * described where it is collapsed to its icon. That is Copy link's alone, and only [isInShortPane] (a short window, the
 * options beside the preview), where the QR code takes the pane's height beside it ([linkPreviewPaddingFor]) and says
 * what it copies; any other action's bare icon would not say what it does.
 */
@Composable
internal fun ExportActionButton(
    modifier: Modifier,
    action: ExportButtonAction?,
    isFirstShownAtOnce: Boolean,
    isInShortPane: Boolean,
    onClick: (ExportButtonAction) -> Unit,
) {
    // Kept while the button leaves, so that it fades out showing what it last offered rather than an empty label.
    var shown by remember { mutableStateOf(action) }
    if (action != null) shown = action
    FloatingControlVisibility(
        modifier = modifier,
        isVisible = action != null,
        isFirstShownAtOnce = isFirstShownAtOnce,
        shape = FloatingActionButtonDefaults.extendedFabShape,
        elevation = FLOATING_BUTTON_ELEVATION,
    ) {
        val current = shown ?: return@FloatingControlVisibility
        val label = stringResource(current.label)
        ExtendedFloatingActionButton(
            text = {
                AnimatedContent(
                    targetState = current.label,
                    transitionSpec = { fadeIn() togetherWith fadeOut() using SizeTransform(clip = false) },
                ) { shownLabel ->
                    Text(stringResource(shownLabel))
                }
            },
            icon = {
                // Keyed by the icon, so that the ring counts on as the pages are drawn rather than crossfading every page.
                AnimatedContent(
                    targetState = current,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    contentKey = { it.iconKey },
                ) { shownAction ->
                    ButtonIcon(shownAction)
                }
            },
            onClick = { action?.let(onClick) },
            modifier = Modifier.semantics { contentDescription = label },
            // From the action kept while the button leaves, so that it does not grow again as it fades out.
            expanded = !(isInShortPane && current == ExportButtonAction.Link(LinkAction.COPY)),
            // The shadow is drawn around the button by FloatingControlVisibility, which fades it with the button.
            elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
        )
    }
}

@Composable
private fun ButtonIcon(
    action: ExportButtonAction,
) {
    val progress = (action as? ExportButtonAction.Save)?.progress
    if (progress == null) {
        Icon(painter = painterResource(action.icon), contentDescription = null)
    } else {
        CircularProgressIndicator(
            progress = { if (progress.total == 0) 0f else progress.done.toFloat() / progress.total },
            modifier = Modifier.size(24.dp),
            strokeWidth = 3.dp,
        )
    }
}

/** What tells one icon of the button from another, the progress ring being one whatever it counts. */
private val ExportButtonAction.iconKey: Any
    get() = when (this) {
        is ExportButtonAction.Save -> progress != null
        is ExportButtonAction.Link -> action
    }

private val ExportButtonAction.label
    get() = when (this) {
        is ExportButtonAction.Save -> if (progress != null) Res.string.cancel else Res.string.print_save
        is ExportButtonAction.Link -> when (action) {
            LinkAction.CONNECT -> Res.string.settings_sync_connect_dropbox
            LinkAction.CONNECT_AGAIN -> Res.string.share_connect_again
            LinkAction.CANCEL -> Res.string.cancel
            LinkAction.CREATE -> Res.string.share_create
            LinkAction.TRY_AGAIN -> Res.string.share_try_again
            LinkAction.COPY -> Res.string.share_copy
        }
    }

private val ExportButtonAction.icon
    get() = when (this) {
        is ExportButtonAction.Save -> Res.drawable.ic_save
        is ExportButtonAction.Link -> when (action) {
            LinkAction.CONNECT, LinkAction.CONNECT_AGAIN -> Res.drawable.ic_cloud
            LinkAction.CANCEL -> Res.drawable.ic_clear
            LinkAction.CREATE -> Res.drawable.ic_link
            LinkAction.TRY_AGAIN -> Res.drawable.ic_refresh
            LinkAction.COPY -> Res.drawable.ic_duplicate
        }
    }

/** Material's resting elevation of a floating action button. */
private val FLOATING_BUTTON_ELEVATION = 6.dp
