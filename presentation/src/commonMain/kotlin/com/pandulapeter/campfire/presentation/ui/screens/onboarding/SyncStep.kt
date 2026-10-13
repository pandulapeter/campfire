/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.domain.AuthorizationCompletionPage
import com.pandulapeter.campfire.data.model.domain.SyncFailureReason
import com.pandulapeter.campfire.data.model.domain.SyncState
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.cancel
import com.pandulapeter.campfire.presentation.resources.ic_check
import com.pandulapeter.campfire.presentation.resources.ic_cloud
import com.pandulapeter.campfire.presentation.resources.onboarding_sync_message
import com.pandulapeter.campfire.presentation.resources.onboarding_sync_title
import com.pandulapeter.campfire.presentation.resources.settings_sync_connect_dropbox
import com.pandulapeter.campfire.presentation.resources.settings_sync_connected_as
import com.pandulapeter.campfire.presentation.resources.settings_sync_connecting
import com.pandulapeter.campfire.presentation.resources.settings_sync_connection_failed_authorization
import com.pandulapeter.campfire.presentation.resources.settings_sync_connection_failed_network
import com.pandulapeter.campfire.presentation.resources.settings_sync_connection_failed_storage
import com.pandulapeter.campfire.presentation.resources.settings_sync_connection_failed_unknown
import com.pandulapeter.campfire.presentation.resources.settings_sync_redirect_page_message
import com.pandulapeter.campfire.presentation.resources.settings_sync_redirect_page_title
import com.pandulapeter.campfire.presentation.ui.components.textResource
import org.jetbrains.compose.resources.painterResource

/**
 * The offer to connect Dropbox, which is the one thing in the app a newcomer would never find by using it: sync stays
 * off and silent until somebody connects a folder. The page goes through the same three stages the settings screen's
 * sync section does - the invitation (with the reason a connection failed under it), the wait for the consent page
 * with its way out, and the account once it is connected - crossfading between them, and leaves everything that comes
 * after a connection (a run's progress, its questions, disconnecting) to that section. Connecting is never required:
 * the bar under the page goes on either way, and the welcome page has already said that Settings holds every answer.
 */
@Composable
internal fun SyncStep(
    modifier: Modifier = Modifier,
    markModifier: Modifier,
    syncState: SyncState,
    isCompact: Boolean,
    onConnect: (AuthorizationCompletionPage) -> Unit,
    onCancel: () -> Unit,
) = OnboardingPage(
    modifier = modifier,
    markModifier = markModifier,
    title = stringResource(Res.string.onboarding_sync_title),
    message = stringResource(Res.string.onboarding_sync_message),
    isCompact = isCompact,
) {
    // Resolved here rather than in the data layer, which can see neither the translations nor the language picked.
    val completionPage = AuthorizationCompletionPage(
        title = stringResource(Res.string.settings_sync_redirect_page_title),
        message = stringResource(Res.string.settings_sync_redirect_page_message),
    )
    AnimatedContent(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        targetState = syncState,
        contentKey = { it.stage },
        transitionSpec = { fadeIn() togetherWith fadeOut() using SizeTransform(clip = false) },
        contentAlignment = Alignment.TopCenter,
    ) { state ->
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (state) {
                SyncState.Disconnected, is SyncState.ConnectionFailed -> DisconnectedContent(
                    failureReason = (state as? SyncState.ConnectionFailed)?.reason,
                    onConnect = { onConnect(completionPage) },
                )

                is SyncState.Connecting -> ConnectingContent(onCancel = onCancel)
                is SyncState.Connected -> ConnectedContent(accountName = state.account.displayName)
            }
        }
    }
}

/** What the page crossfades between: a failed connection is the invitation with its reason, so the two are one. */
private enum class SyncStage { DISCONNECTED, CONNECTING, CONNECTED }

private val SyncState.stage
    get() = when (this) {
        SyncState.Disconnected, is SyncState.ConnectionFailed -> SyncStage.DISCONNECTED
        is SyncState.Connecting -> SyncStage.CONNECTING
        is SyncState.Connected -> SyncStage.CONNECTED
    }

@Composable
private fun DisconnectedContent(
    failureReason: SyncFailureReason?,
    onConnect: () -> Unit,
) {
    Button(onClick = onConnect) {
        Icon(
            modifier = Modifier.size(18.dp),
            painter = painterResource(Res.drawable.ic_cloud),
            contentDescription = null,
        )
        Text(
            modifier = Modifier.padding(start = 8.dp),
            text = stringResource(Res.string.settings_sync_connect_dropbox),
        )
    }
    failureReason?.let { reason ->
        Text(
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            text = stringResource(
                when (reason) {
                    SyncFailureReason.NETWORK -> Res.string.settings_sync_connection_failed_network
                    SyncFailureReason.AUTHORIZATION -> Res.string.settings_sync_connection_failed_authorization
                    SyncFailureReason.STORAGE -> Res.string.settings_sync_connection_failed_storage
                    SyncFailureReason.REMOTE_STORAGE_FULL,
                    SyncFailureReason.UNKNOWN -> Res.string.settings_sync_connection_failed_unknown
                }
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The wait for the consent page, which the app cannot see: every platform tries to notice a browser closed without an
 * answer, but this must never be a state that only a restart leaves, so Cancel is always there.
 */
@Composable
private fun ConnectingContent(
    onCancel: () -> Unit,
) {
    Text(
        text = stringResource(Res.string.settings_sync_connecting),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
    )
    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(0.6f))
    OutlinedButton(onClick = onCancel) { Text(stringResource(Res.string.cancel)) }
}

@Composable
private fun ConnectedContent(
    accountName: String,
) = Surface(
    shape = MaterialTheme.shapes.large,
    color = MaterialTheme.colorScheme.secondaryContainer,
) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_check),
            contentDescription = null,
        )
        Text(
            text = textResource(Res.string.settings_sync_connected_as, accountName),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
