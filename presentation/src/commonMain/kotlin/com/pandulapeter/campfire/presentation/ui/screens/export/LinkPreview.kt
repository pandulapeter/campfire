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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.importing.ImportLimits
import com.pandulapeter.campfire.data.model.share.SharedLinkKind
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.settings_sync_connecting
import com.pandulapeter.campfire.presentation.resources.share_authorization
import com.pandulapeter.campfire.presentation.resources.share_copy_missing
import com.pandulapeter.campfire.presentation.resources.share_creating
import com.pandulapeter.campfire.presentation.resources.share_failed
import com.pandulapeter.campfire.presentation.resources.share_missing
import com.pandulapeter.campfire.presentation.resources.share_network
import com.pandulapeter.campfire.presentation.resources.share_newer_version
import com.pandulapeter.campfire.presentation.resources.share_not_connected
import com.pandulapeter.campfire.presentation.resources.share_not_public
import com.pandulapeter.campfire.presentation.resources.share_not_shared_setlist
import com.pandulapeter.campfire.presentation.resources.share_not_shared_song
import com.pandulapeter.campfire.presentation.resources.share_qr_code
import com.pandulapeter.campfire.presentation.resources.share_remote_full
import com.pandulapeter.campfire.presentation.resources.share_scope_missing
import com.pandulapeter.campfire.presentation.resources.share_stopping
import com.pandulapeter.campfire.presentation.resources.share_too_large
import com.pandulapeter.campfire.presentation.resources.share_too_large_to_update
import com.pandulapeter.campfire.presentation.ui.components.DelayedLoadingIndicator
import com.pandulapeter.campfire.presentation.ui.share.QrCode
import com.pandulapeter.campfire.presentation.ui.share.QrCodeImage
import com.pandulapeter.campfire.presentation.ui.share.sharedOnText
import com.pandulapeter.campfire.presentation.ui.sync.connectionFailedMessage
import com.pandulapeter.campfire.presentation.ui.theme.LocalMonospaceFontFamily

/**
 * The preview of the Link format: what sharing [kind] does and what it takes, or what went wrong, until it is shared,
 * then the link itself, as a QR code to be read off the screen and as text to be read or selected. States fade into one
 * another, each a user's tap or the connection's answer to one.
 */
@Composable
internal fun LinkPreview(
    modifier: Modifier,
    state: LinkExportState,
    kind: SharedLinkKind,
    contentPadding: PaddingValues,
) = AnimatedContent(
    targetState = state,
    modifier = modifier,
    contentKey = { it::class },
    transitionSpec = { fadeIn() togetherWith fadeOut() },
) { shown ->
    Box(Modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.Center) {
        when (shown) {
            LinkExportState.Creating -> DelayedLoadingIndicator(label = stringResource(Res.string.share_creating))
            LinkExportState.Stopping -> DelayedLoadingIndicator(label = stringResource(Res.string.share_stopping))
            is LinkExportState.Shared -> SharedLink(state = shown)
            else -> LinkMessage(linkMessageOf(shown, kind))
        }
    }
}

@Composable
private fun linkMessageOf(state: LinkExportState, kind: SharedLinkKind) = when (state) {
    LinkExportState.NotConnected -> stringResource(Res.string.share_not_connected)
    LinkExportState.Connecting -> stringResource(Res.string.settings_sync_connecting)
    is LinkExportState.ConnectionFailed -> stringResource(state.reason.connectionFailedMessage)
    LinkExportState.NotShared -> stringResource(if (kind == SharedLinkKind.SETLIST) Res.string.share_not_shared_setlist else Res.string.share_not_shared_song)
    LinkExportState.CopyMissing -> stringResource(Res.string.share_copy_missing)
    is LinkExportState.Failed -> when (state.problem) {
        LinkProblem.SCOPE_MISSING -> stringResource(Res.string.share_scope_missing)
        LinkProblem.AUTHORIZATION -> stringResource(Res.string.share_authorization)
        LinkProblem.TOO_LARGE -> stringResource(Res.string.share_too_large, (ImportLimits.MAX_IMPORT_SIZE shr 20).toInt())
        LinkProblem.NOT_PUBLIC -> stringResource(Res.string.share_not_public)
        LinkProblem.NETWORK -> stringResource(Res.string.share_network)
        LinkProblem.REMOTE_STORAGE_FULL -> stringResource(Res.string.share_remote_full)
        LinkProblem.NEWER_VERSION -> stringResource(Res.string.share_newer_version)
        LinkProblem.MISSING -> stringResource(Res.string.share_missing)
        LinkProblem.OTHER -> stringResource(Res.string.share_failed)
    }
    LinkExportState.Creating, LinkExportState.Stopping, is LinkExportState.Shared -> ""
}

@Composable
private fun LinkMessage(text: String) = Text(
    text = text,
    modifier = Modifier.widthIn(max = MAX_TEXT_WIDTH).padding(horizontal = 24.dp),
    style = MaterialTheme.typography.bodyLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    textAlign = TextAlign.Center,
)

/**
 * The QR code, the link under it in the monospace face, selectable since some will rather copy a part of it by hand,
 * and the day it was shared; scrolled where a short window has no room for all of it. The code is made from the state
 * this is drawn for, not the screen's current one, so that a link fading out once Stop sharing is pressed keeps it.
 */
@Composable
private fun SharedLink(
    state: LinkExportState.Shared,
) = Column(
    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(12.dp),
) {
    val qrCode = remember(state.share.link.landingUrl) { QrCode.encode(state.share.link.landingUrl) }
    if (qrCode != null) {
        QrCodeImage(
            modifier = Modifier.widthIn(max = QR_CODE_WIDTH).fillMaxWidth(),
            code = qrCode,
            contentDescription = stringResource(Res.string.share_qr_code),
        )
    }
    SelectionContainer {
        Text(
            text = state.share.link.landingUrl,
            modifier = Modifier.widthIn(max = MAX_TEXT_WIDTH),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = LocalMonospaceFontFamily.current),
            textAlign = TextAlign.Center,
        )
    }
    state.share.sharedAt?.let { day ->
        Text(
            text = sharedOnText(day),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (state.isTooLarge) {
        Text(
            text = stringResource(Res.string.share_too_large_to_update),
            modifier = Modifier.widthIn(max = MAX_TEXT_WIDTH),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
    }
}

/** As wide as a phone camera reads comfortably from across a table, and no wider on a large screen. */
private val QR_CODE_WIDTH = 240.dp

private val MAX_TEXT_WIDTH = 480.dp
