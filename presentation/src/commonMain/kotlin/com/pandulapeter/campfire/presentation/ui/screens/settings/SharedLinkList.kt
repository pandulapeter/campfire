/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.share.Share
import com.pandulapeter.campfire.data.model.share.ShareState
import com.pandulapeter.campfire.data.model.share.SharedLinkKind
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_delete
import com.pandulapeter.campfire.presentation.resources.ic_duplicate
import com.pandulapeter.campfire.presentation.resources.ic_setlists
import com.pandulapeter.campfire.presentation.resources.ic_share
import com.pandulapeter.campfire.presentation.resources.ic_songs
import com.pandulapeter.campfire.presentation.resources.settings_sharing_actions
import com.pandulapeter.campfire.presentation.resources.settings_sharing_copy_missing
import com.pandulapeter.campfire.presentation.resources.settings_sharing_orphaned
import com.pandulapeter.campfire.presentation.resources.settings_sharing_show_qr_code
import com.pandulapeter.campfire.presentation.resources.settings_sharing_too_large
import com.pandulapeter.campfire.presentation.resources.share_copy
import com.pandulapeter.campfire.presentation.resources.share_stop
import com.pandulapeter.campfire.presentation.resources.share_stopping
import com.pandulapeter.campfire.presentation.ui.components.menu.ActionsMenu
import com.pandulapeter.campfire.presentation.ui.components.menu.ActionsMenuItem
import com.pandulapeter.campfire.presentation.ui.settingsPage.AnimatedSettingsRow
import com.pandulapeter.campfire.presentation.ui.share.sharedOnText
import org.jetbrains.compose.resources.painterResource

/**
 * The account's shares, one row each, most recently shared first as the list keeps them. A share that is stopped
 * shrinks away in its own place ([sharedLinkRowsOf]); one that arrives with a fresher read of the list is simply there,
 * since that is data arriving rather than anything the user did.
 *
 * @param busyShareIds The shares being stopped, drawn faded and with a progress indicator in place of their menu until
 *   the list lets them go, or until the stop fails and gives the menu back.
 * @param canShowQrCode Whether the library holds the item, which the export screen that shows the QR code is opened on.
 */
@Composable
internal fun ColumnScope.SharedLinkList(
    shares: List<Share>,
    busyShareIds: Set<String>,
    canShowQrCode: (Share) -> Boolean,
    onCopy: (Share) -> Unit,
    onShowQrCode: (Share) -> Unit,
    onStop: (Share) -> Unit,
) {
    val shown = remember { ShownRows(shares) }
    shown.rows = sharedLinkRowsOf(shown.rows, shares)
    val current = shares.associateBy { it.shareId }
    shown.rows.forEach { row ->
        key(row.shareId) {
            AnimatedSettingsRow(value = current[row.shareId]) { share ->
                SharedLinkRow(
                    share = share,
                    isBusy = share.shareId in busyShareIds,
                    canShowQrCode = canShowQrCode(share),
                    onCopy = { onCopy(share) },
                    onShowQrCode = { onShowQrCode(share) },
                    onStop = { onStop(share) },
                )
            }
        }
    }
}

@Composable
private fun SharedLinkRow(
    share: Share,
    isBusy: Boolean,
    canShowQrCode: Boolean,
    onCopy: () -> Unit,
    onShowQrCode: () -> Unit,
    onStop: () -> Unit,
) = ListItem(
    modifier = Modifier.alpha(animateFloatAsState(if (isBusy) 0.5f else 1f).value),
    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    leadingContent = {
        Icon(
            painter = painterResource(if (share.kind == SharedLinkKind.SETLIST) Res.drawable.ic_setlists else Res.drawable.ic_songs),
            contentDescription = null,
        )
    },
    headlineContent = { Text(share.title) },
    supportingContent = sharedLinkStatus(share)?.let { status ->
        {
            Text(
                text = status,
                color = if (share.state == ShareState.OK) Color.Unspecified else MaterialTheme.colorScheme.error,
            )
        }
    },
    trailingContent = {
        // Nothing to do with a share already on its way out but to see that it is: the menu gives way to a progress
        // indicator until the row leaves, or comes back where the stop failed. The two swap sizes smoothly, so that the
        // title they leave room for never jumps.
        AnimatedContent(
            targetState = isBusy,
            contentAlignment = Alignment.Center,
            transitionSpec = { fadeIn() togetherWith fadeOut() using SizeTransform(clip = false) },
        ) { isStopping ->
            if (isStopping) {
                StoppingIndicator()
            } else {
                SharedLinkActions(share = share, canShowQrCode = canShowQrCode, onCopy = onCopy, onShowQrCode = onShowQrCode, onStop = onStop)
            }
        }
    },
)

@Composable
private fun SharedLinkActions(
    share: Share,
    canShowQrCode: Boolean,
    onCopy: () -> Unit,
    onShowQrCode: () -> Unit,
    onStop: () -> Unit,
) = ActionsMenu(contentDescription = stringResource(Res.string.settings_sharing_actions)) {
    val isWorthHandingOut = share.state != ShareState.ORPHANED && share.state != ShareState.COPY_MISSING
    listOfNotNull(
        ActionsMenuItem(
            title = stringResource(Res.string.share_copy),
            icon = painterResource(Res.drawable.ic_duplicate),
            onClick = onCopy,
        ).takeIf { isWorthHandingOut },
        ActionsMenuItem(
            title = stringResource(Res.string.settings_sharing_show_qr_code),
            icon = painterResource(Res.drawable.ic_share),
            onClick = onShowQrCode,
        ).takeIf { isWorthHandingOut && canShowQrCode },
        ActionsMenuItem(
            title = stringResource(Res.string.share_stop),
            icon = painterResource(Res.drawable.ic_delete),
            isAlwaysInMenu = true,
            onClick = onStop,
        ),
    )
}

/**
 * In the room of the menu's button, so that the row keeps its height, and named for screen readers as the export screen
 * names the same moment.
 */
@Composable
private fun StoppingIndicator() {
    val description = stringResource(Res.string.share_stopping)
    Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp).semantics { contentDescription = description },
            strokeWidth = 3.dp,
        )
    }
}

/** The line under a share's title: what keeps it from following its item, else the day it was shared. */
@Composable
private fun sharedLinkStatus(share: Share) = when (share.state) {
    ShareState.ORPHANED -> stringResource(Res.string.settings_sharing_orphaned)
    ShareState.TOO_LARGE -> stringResource(Res.string.settings_sharing_too_large)
    ShareState.COPY_MISSING -> stringResource(Res.string.settings_sharing_copy_missing)
    ShareState.OK -> share.sharedAt?.let { sharedOnText(it) }
}

/** What [SharedLinkList] drew last. Not a state, since it is only ever written while the list is drawn from it. */
private class ShownRows(var rows: List<Share>)

/**
 * The rows the list of shares draws: every share in [shares], in its order, and the ones [shown] had that [shares] no
 * longer has kept where they were, after the row they followed, so that a stopped share shrinks away in its own place
 * rather than vanishing in a frame.
 */
internal fun sharedLinkRowsOf(shown: List<Share>, shares: List<Share>): List<Share> {
    val current = shares.mapTo(mutableSetOf()) { it.shareId }
    val result = shares.toMutableList()
    var previous: String? = null
    shown.forEach { share ->
        if (share.shareId !in current) {
            val index = previous?.let { id -> result.indexOfFirst { it.shareId == id } + 1 } ?: 0
            result.add(index, share)
        }
        previous = share.shareId
    }
    return result
}
