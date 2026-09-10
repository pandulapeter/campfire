/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
@file:OptIn(ExperimentalTime::class)

package com.pandulapeter.campfire.presentation.ui.screens.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.domain.SyncFailureReason
import com.pandulapeter.campfire.data.model.domain.SyncOutcome
import com.pandulapeter.campfire.data.model.domain.SyncProgress
import com.pandulapeter.campfire.data.model.domain.SyncProviderId
import com.pandulapeter.campfire.data.model.domain.SyncState
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_cloud
import com.pandulapeter.campfire.presentation.resources.ic_cloud_off
import com.pandulapeter.campfire.presentation.resources.ic_clear
import com.pandulapeter.campfire.presentation.resources.ic_sync
import com.pandulapeter.campfire.presentation.resources.cancel
import com.pandulapeter.campfire.presentation.resources.settings_sync_cancel
import com.pandulapeter.campfire.presentation.resources.settings_sync_conflicts
import com.pandulapeter.campfire.presentation.resources.settings_sync_connect_dropbox
import com.pandulapeter.campfire.presentation.resources.settings_sync_connected_as
import com.pandulapeter.campfire.presentation.resources.settings_sync_connecting
import com.pandulapeter.campfire.presentation.resources.settings_sync_date_time
import com.pandulapeter.campfire.presentation.resources.settings_sync_description
import com.pandulapeter.campfire.presentation.resources.settings_sync_disconnect
import com.pandulapeter.campfire.presentation.resources.settings_sync_failed_authorization
import com.pandulapeter.campfire.presentation.resources.settings_sync_failed_network
import com.pandulapeter.campfire.presentation.resources.settings_sync_failed_storage
import com.pandulapeter.campfire.presentation.resources.settings_sync_failed_unknown
import com.pandulapeter.campfire.presentation.resources.settings_sync_interrupted
import com.pandulapeter.campfire.presentation.resources.settings_sync_last_synced
import com.pandulapeter.campfire.presentation.resources.settings_sync_never
import com.pandulapeter.campfire.presentation.resources.settings_sync_now
import com.pandulapeter.campfire.presentation.resources.settings_sync_preparing
import com.pandulapeter.campfire.presentation.resources.settings_sync_progress
import com.pandulapeter.campfire.presentation.resources.settings_sync_redirect_page_message
import com.pandulapeter.campfire.presentation.resources.settings_sync_redirect_page_title
import com.pandulapeter.campfire.presentation.resources.settings_sync_unavailable
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.data.source.remote.api.model.AuthorizationCompletionPage
import com.pandulapeter.campfire.presentation.ui.components.ActionListItem
import com.pandulapeter.campfire.presentation.ui.platform.withSyncCounts
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.painterResource

/**
 * The sync section of the settings screen: one paragraph saying what it does, then either an invitation to connect
 * or the account that is connected, what the last run did, and the two things one can do about it.
 *
 * Every row is keyed and animated like the rest of the list, so that connecting and disconnecting move the section
 * rather than swapping it in a single frame.
 */
internal fun LazyListScope.syncSettings(
    viewModel: CampfireViewModel,
    syncState: SyncState,
    completionPage: AuthorizationCompletionPage
) {
    if (viewModel.syncProviders.isEmpty()) {
        item(key = "sync_unavailable") {
            SyncMessage(modifier = Modifier.animateItem(), text = stringResource(Res.string.settings_sync_unavailable))
        }
        return
    }
    item(key = "sync_description") {
        SyncMessage(modifier = Modifier.animateItem(), text = stringResource(Res.string.settings_sync_description))
    }
    when (syncState) {
        SyncState.Disconnected -> item(key = "sync_connect") {
            ActionListItem(
                modifier = Modifier.animateItem(),
                title = stringResource(Res.string.settings_sync_connect_dropbox),
                icon = painterResource(Res.drawable.ic_cloud),
                isEnabled = viewModel.syncProviders.contains(SyncProviderId.DROPBOX),
                onClick = { viewModel.connectSyncProvider(SyncProviderId.DROPBOX, completionPage) }
            )
        }

        is SyncState.Connecting -> {
            item(key = "sync_connecting") {
                ListItem(
                    modifier = Modifier.animateItem(),
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text(stringResource(Res.string.settings_sync_connecting)) }
                )
            }
            // The way out. Each platform does try to notice that the browser was closed, but a consent page is
            // somewhere the app cannot see, so this state must never be one the user has to restart the app to leave.
            item(key = "sync_cancel_connecting") {
                ActionListItem(
                    modifier = Modifier.animateItem(),
                    title = stringResource(Res.string.cancel),
                    icon = painterResource(Res.drawable.ic_clear),
                    isEmphasized = false,
                    onClick = viewModel::cancelSyncConnection
                )
            }
        }

        is SyncState.Connected -> {
            item(key = "sync_account") {
                ListItem(
                    modifier = Modifier.animateItem(),
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text(stringResource(Res.string.settings_sync_connected_as, syncState.account.displayName)) },
                    supportingContent = { Text(syncState.statusText()) }
                )
            }
            syncState.progress?.let { progress ->
                item(key = "sync_progress") {
                    SyncProgressIndicator(modifier = Modifier.animateItem(), progress = progress)
                }
            }
            item(key = "sync_now") {
                if (syncState.isSyncing) {
                    ActionListItem(
                        modifier = Modifier.animateItem(),
                        title = stringResource(Res.string.settings_sync_cancel),
                        icon = painterResource(Res.drawable.ic_clear),
                        isEmphasized = false,
                        onClick = viewModel::cancelSynchronization
                    )
                } else {
                    ActionListItem(
                        modifier = Modifier.animateItem(),
                        title = stringResource(Res.string.settings_sync_now),
                        icon = painterResource(Res.drawable.ic_sync),
                        isEmphasized = false,
                        onClick = viewModel::synchronizeLibrary
                    )
                }
            }
            item(key = "sync_disconnect") {
                ActionListItem(
                    modifier = Modifier.animateItem(),
                    title = stringResource(Res.string.settings_sync_disconnect),
                    icon = painterResource(Res.drawable.ic_cloud_off),
                    isEmphasized = false,
                    onClick = { viewModel.showDialog(CampfireViewModel.DialogType.DisconnectSync(syncState.account.displayName)) }
                )
            }
        }
    }
}

/**
 * The one line under the account name: what is happening now, or what the last run ended in. A failure stays there
 * until something replaces it, rather than going past in a snackbar the user may not have been looking at.
 */
@Composable
private fun SyncState.Connected.statusText(): String = when (val current = progress) {
    null -> when (val outcome = lastOutcome) {
        SyncOutcome.Interrupted -> stringResource(Res.string.settings_sync_interrupted)
        is SyncOutcome.Failure -> stringResource(
            when (outcome.reason) {
                SyncFailureReason.NETWORK -> Res.string.settings_sync_failed_network
                SyncFailureReason.AUTHORIZATION -> Res.string.settings_sync_failed_authorization
                SyncFailureReason.STORAGE -> Res.string.settings_sync_failed_storage
                SyncFailureReason.UNKNOWN -> Res.string.settings_sync_failed_unknown
            }
        )

        // What a successful run moved is not something the user has to be told: the library is simply the same on
        // both sides now. The one thing only the app knows is that it did finish, and when.
        is SyncOutcome.Success -> if (outcome.summary.conflicts.isEmpty()) {
            lastSyncedText(lastSyncedAt)
        } else {
            stringResource(Res.string.settings_sync_conflicts, outcome.summary.conflicts.joinToString())
        }

        null -> lastSyncedText(lastSyncedAt)
    }

    // While a run is going, what it is doing is on the progress row just below; repeating it here would say the
    // same thing twice, so this line keeps answering the question the progress row does not: when it last finished.
    else -> lastSyncedText(lastSyncedAt)
}

/**
 * Determinate as soon as there is anything to count, and indeterminate before that: the number of files a run has
 * to move is only known once both sides have been listed, and a bar sitting at zero for that whole time reads as a
 * sync that is stuck rather than one that is still looking.
 */
@Composable
private fun SyncProgressIndicator(
    modifier: Modifier = Modifier,
    progress: SyncProgress
) = Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
    Text(
        text = progress.text(),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    val fraction = progress.fraction
    if (fraction == null) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
    } else {
        // Animated, or a run that finishes several files between frames would make the bar jump.
        val animatedFraction by animateFloatAsState(targetValue = fraction, label = "syncProgress")
        LinearProgressIndicator(
            progress = { animatedFraction },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
    }
}

@Composable
private fun SyncProgress.text() = if (isPreparing) {
    stringResource(Res.string.settings_sync_preparing)
} else {
    stringResource(Res.string.settings_sync_progress).withSyncCounts(completed, total)
}

/**
 * An exact local date and time rather than "3 hours ago": this line is the answer to "did my library get where I
 * think it did", and a run that happened at a moment the user remembers answers that better than an elapsed time.
 * The order of the parts is a string resource, so each language puts them where it puts them.
 */
@Composable
private fun lastSyncedText(lastSyncedAt: Long?): String {
    if (lastSyncedAt == null) return stringResource(Res.string.settings_sync_never)
    val local = Instant.fromEpochMilliseconds(lastSyncedAt).toLocalDateTime(TimeZone.currentSystemDefault())
    return stringResource(
        Res.string.settings_sync_last_synced,
        stringResource(
            Res.string.settings_sync_date_time,
            local.year.toString(),
            (local.month.ordinal + 1).padded(),
            local.day.padded(),
            local.hour.padded(),
            local.minute.padded()
        )
    )
}

private fun Int.padded() = toString().padStart(length = 2, padChar = '0')

@Composable
private fun SyncMessage(
    modifier: Modifier = Modifier,
    text: String
) = Column(modifier = modifier.fillMaxWidth()) {
    Text(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
