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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.pandulapeter.campfire.data.model.domain.SyncDeletionDirection
import com.pandulapeter.campfire.data.model.domain.SyncDeletionPolicy
import com.pandulapeter.campfire.data.model.domain.SyncFailureReason
import com.pandulapeter.campfire.data.model.domain.SyncOutcome
import com.pandulapeter.campfire.data.model.domain.SyncProgress
import com.pandulapeter.campfire.data.model.domain.SyncProviderId
import com.pandulapeter.campfire.data.model.domain.SyncState
import com.pandulapeter.campfire.presentation.localization.pluralStringResource
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_cloud
import com.pandulapeter.campfire.presentation.resources.ic_cloud_off
import com.pandulapeter.campfire.presentation.resources.ic_delete
import com.pandulapeter.campfire.presentation.resources.ic_clear
import com.pandulapeter.campfire.presentation.resources.ic_sync
import com.pandulapeter.campfire.presentation.resources.cancel
import com.pandulapeter.campfire.presentation.resources.settings_sync_cancel
import com.pandulapeter.campfire.presentation.resources.settings_sync_connect_dropbox
import com.pandulapeter.campfire.presentation.resources.settings_sync_conflicts
import com.pandulapeter.campfire.presentation.resources.settings_sync_connected_as
import com.pandulapeter.campfire.presentation.resources.settings_sync_connecting
import com.pandulapeter.campfire.presentation.resources.settings_sync_connection_failed_authorization
import com.pandulapeter.campfire.presentation.resources.settings_sync_connection_failed_network
import com.pandulapeter.campfire.presentation.resources.settings_sync_connection_failed_unknown
import com.pandulapeter.campfire.presentation.resources.settings_sync_date_time
import com.pandulapeter.campfire.presentation.resources.settings_sync_delete_locally
import com.pandulapeter.campfire.presentation.resources.settings_sync_delete_remotely
import com.pandulapeter.campfire.presentation.resources.settings_sync_deletions_pending
import com.pandulapeter.campfire.presentation.resources.settings_sync_description
import com.pandulapeter.campfire.presentation.resources.settings_sync_disconnect
import com.pandulapeter.campfire.presentation.resources.settings_sync_files_failed
import com.pandulapeter.campfire.presentation.resources.settings_sync_failed_authorization
import com.pandulapeter.campfire.presentation.resources.settings_sync_failed_network
import com.pandulapeter.campfire.presentation.resources.settings_sync_failed_remote_full
import com.pandulapeter.campfire.presentation.resources.settings_sync_failed_storage
import com.pandulapeter.campfire.presentation.resources.settings_sync_failed_unknown
import com.pandulapeter.campfire.presentation.resources.settings_sync_interrupted
import com.pandulapeter.campfire.presentation.resources.settings_sync_keep_and_download
import com.pandulapeter.campfire.presentation.resources.settings_sync_keep_and_upload
import com.pandulapeter.campfire.presentation.resources.settings_sync_last_synced
import com.pandulapeter.campfire.presentation.resources.settings_sync_never
import com.pandulapeter.campfire.presentation.resources.settings_sync_now
import com.pandulapeter.campfire.presentation.resources.settings_sync_preparing
import com.pandulapeter.campfire.presentation.resources.settings_sync_progress
import com.pandulapeter.campfire.presentation.resources.settings_sync_redirect_page_message
import com.pandulapeter.campfire.presentation.resources.settings_sync_redirect_page_title
import com.pandulapeter.campfire.presentation.resources.settings_sync_remote_deletions_pending
import com.pandulapeter.campfire.presentation.resources.settings_sync_unavailable
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.data.source.remote.api.model.AuthorizationCompletionPage
import com.pandulapeter.campfire.presentation.ui.components.ActionListItem
import com.pandulapeter.campfire.presentation.ui.components.pluralTextResource
import com.pandulapeter.campfire.presentation.ui.components.textResource
import com.pandulapeter.campfire.presentation.ui.platform.withSyncCounts
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.painterResource

/**
 * The rows of the sync section of the settings screen: one paragraph saying what it does, then either an invitation to
 * connect or the account that is connected, what the last run did, and the two things one can do about it.
 *
 * The three stages cross fade, the section resizing under them, and the rows that come and go within a stage expand and
 * shrink, so that connecting, disconnecting and a run starting move the section rather than swapping it in a single
 * frame. The stage is what is animated between and not the state, which changes with every file a run moves.
 */
@Composable
internal fun ColumnScope.SyncSettings(
    viewModel: CampfireViewModel,
    syncState: SyncState,
) {
    if (viewModel.syncProviders.isEmpty()) {
        SettingsMessage(text = stringResource(Res.string.settings_sync_unavailable))
        return
    }
    SettingsMessage(text = stringResource(Res.string.settings_sync_description))
    AnimatedContent(
        targetState = syncState,
        contentKey = { it.stage },
        transitionSpec = { fadeIn() togetherWith fadeOut() using SizeTransform(clip = false) },
    ) { state ->
        Column {
            when (state) {
                SyncState.Disconnected, is SyncState.ConnectionFailed -> DisconnectedSyncSettings(viewModel = viewModel, syncState = state)
                is SyncState.Connecting -> ConnectingSyncSettings(viewModel = viewModel)
                is SyncState.Connected -> ConnectedSyncSettings(viewModel = viewModel, syncState = state)
            }
        }
    }
}

/**
 * What [SyncSettings] animates between. A failed connection is the invitation to connect with the reason under it,
 * so the two are one stage and the reason is a row that comes and goes within it.
 */
private enum class SyncStage { DISCONNECTED, CONNECTING, CONNECTED }

private val SyncState.stage
    get() = when (this) {
        SyncState.Disconnected, is SyncState.ConnectionFailed -> SyncStage.DISCONNECTED
        is SyncState.Connecting -> SyncStage.CONNECTING
        is SyncState.Connected -> SyncStage.CONNECTED
    }

@Composable
private fun ColumnScope.DisconnectedSyncSettings(
    viewModel: CampfireViewModel,
    syncState: SyncState,
) {
    // Resolved out here rather than in the data layer, which can see neither the translations nor the language the
    // user picked.
    val completionPage = AuthorizationCompletionPage(
        title = stringResource(Res.string.settings_sync_redirect_page_title),
        message = stringResource(Res.string.settings_sync_redirect_page_message),
    )
    ActionListItem(
        title = stringResource(Res.string.settings_sync_connect_dropbox),
        icon = painterResource(Res.drawable.ic_cloud),
        isEnabled = viewModel.syncProviders.contains(SyncProviderId.DROPBOX),
        onClick = { viewModel.connectSyncProvider(SyncProviderId.DROPBOX, completionPage) },
    )
    // Under the Connect row rather than above it, so that Connect is the first row of its stage with or without a
    // reason: the first row of the connecting stage is text that takes no taps, where it would otherwise be the Cancel
    // that the second click of a double click lands on.
    AnimatedSettingsRow(value = (syncState as? SyncState.ConnectionFailed)?.reason) { reason ->
        SettingsMessage(
            text = stringResource(
                when (reason) {
                    SyncFailureReason.NETWORK -> Res.string.settings_sync_connection_failed_network
                    SyncFailureReason.AUTHORIZATION -> Res.string.settings_sync_connection_failed_authorization
                    SyncFailureReason.STORAGE,
                    SyncFailureReason.REMOTE_STORAGE_FULL,
                    SyncFailureReason.UNKNOWN -> Res.string.settings_sync_connection_failed_unknown
                }
            ),
        )
    }
}

@Composable
private fun ConnectingSyncSettings(viewModel: CampfireViewModel) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(stringResource(Res.string.settings_sync_connecting)) },
    )
    // The way out. Each platform does try to notice that the browser was closed, but a consent page is somewhere
    // the app cannot see, so this state must never be one the user has to restart the app to leave.
    ActionListItem(
        title = stringResource(Res.string.cancel),
        icon = painterResource(Res.drawable.ic_clear),
        isEmphasized = false,
        onClick = viewModel::cancelSyncConnection,
    )
}

@Composable
private fun ColumnScope.ConnectedSyncSettings(
    viewModel: CampfireViewModel,
    syncState: SyncState.Connected,
) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(textResource(Res.string.settings_sync_connected_as, syncState.account.displayName)) },
        supportingContent = { Text(syncState.statusText()) },
    )
    AnimatedSettingsRow(value = (syncState.lastOutcome as? SyncOutcome.DeletionsNeedConfirmation)?.direction) { direction ->
        Column {
            when (direction) {
                SyncDeletionDirection.LOCAL -> {
                    ActionListItem(
                        title = stringResource(Res.string.settings_sync_delete_locally),
                        icon = painterResource(Res.drawable.ic_delete),
                        onClick = { viewModel.synchronizeLibrary(SyncDeletionPolicy.DELETE_LOCALLY) },
                    )
                    ActionListItem(
                        title = stringResource(Res.string.settings_sync_keep_and_upload),
                        icon = painterResource(Res.drawable.ic_cloud),
                        onClick = { viewModel.synchronizeLibrary(SyncDeletionPolicy.KEEP_AND_UPLOAD) },
                    )
                }

                SyncDeletionDirection.REMOTE -> {
                    ActionListItem(
                        title = stringResource(Res.string.settings_sync_delete_remotely),
                        icon = painterResource(Res.drawable.ic_delete),
                        onClick = { viewModel.synchronizeLibrary(SyncDeletionPolicy.DELETE_REMOTELY) },
                    )
                    ActionListItem(
                        title = stringResource(Res.string.settings_sync_keep_and_download),
                        icon = painterResource(Res.drawable.ic_cloud),
                        onClick = { viewModel.synchronizeLibrary(SyncDeletionPolicy.KEEP_AND_DOWNLOAD) },
                    )
                }
            }
        }
    }
    // Never swapped for "Stop syncing" in its own place: the second tap of a double tap lands on whatever the first
    // one put under the finger, and a run would be stopped the moment it started, or started again the moment it
    // stopped. The rows that come and go with a run are the questions above it, which collapse, and the progress row
    // under it, which expands away from it.
    ActionListItem(
        title = stringResource(Res.string.settings_sync_now),
        icon = painterResource(Res.drawable.ic_sync),
        isEnabled = !syncState.isSyncing,
        isEmphasized = false,
        onClick = { viewModel.synchronizeLibrary() },
    )
    AnimatedSettingsRow(value = syncState.progress) { progress ->
        Column {
            SyncProgressIndicator(progress = progress)
            ActionListItem(
                title = stringResource(Res.string.settings_sync_cancel),
                icon = painterResource(Res.drawable.ic_clear),
                isEmphasized = false,
                onClick = viewModel::cancelSynchronization,
            )
        }
    }
    ActionListItem(
        title = stringResource(Res.string.settings_sync_disconnect),
        icon = painterResource(Res.drawable.ic_cloud_off),
        isEmphasized = false,
        onClick = { viewModel.showDialog(CampfireViewModel.DialogType.DisconnectSync(syncState.account.displayName)) },
    )
}

/**
 * The one line under the account name: what is happening now, or what the last run ended in. A failure stays there
 * until something replaces it, rather than going past in a snackbar the user may not have been looking at.
 */
@Composable
private fun SyncState.Connected.statusText(): String = when (val current = progress) {
    null -> when (val outcome = lastOutcome) {
        SyncOutcome.Interrupted -> stringResource(Res.string.settings_sync_interrupted)
        is SyncOutcome.DeletionsNeedConfirmation -> pluralStringResource(
            when (outcome.direction) {
                SyncDeletionDirection.LOCAL -> Res.plurals.settings_sync_deletions_pending
                SyncDeletionDirection.REMOTE -> Res.plurals.settings_sync_remote_deletions_pending
            },
            outcome.count,
            outcome.count,
            outcome.total,
        )
        is SyncOutcome.Failure -> stringResource(
            when (outcome.reason) {
                SyncFailureReason.NETWORK -> Res.string.settings_sync_failed_network
                SyncFailureReason.AUTHORIZATION -> Res.string.settings_sync_failed_authorization
                SyncFailureReason.STORAGE -> Res.string.settings_sync_failed_storage
                SyncFailureReason.REMOTE_STORAGE_FULL -> Res.string.settings_sync_failed_remote_full
                SyncFailureReason.UNKNOWN -> Res.string.settings_sync_failed_unknown
            }
        )

        // What a successful run moved is not something the user has to be told: the library is simply the same on
        // both sides now. What it could not move is, and so is a file that now exists twice; with neither, the one
        // thing only the app knows is that the run did finish, and when.
        is SyncOutcome.Success -> listOfNotNull(
            outcome.summary.failed.takeIf { it.isNotEmpty() }?.let { failed ->
                pluralTextResource(
                    Res.plurals.settings_sync_files_failed,
                    failed.size,
                    failed.size.toString(),
                    failed.take(MAXIMUM_NAMED_FILES).joinToString(),
                )
            },
            // Capped like the failures: a first run on a device edited on both sides can keep hundreds of copies, and
            // this line is not the place to list them.
            outcome.summary.conflicts.takeIf { it.isNotEmpty() }?.let { conflicts ->
                pluralTextResource(
                    Res.plurals.settings_sync_conflicts,
                    conflicts.size,
                    conflicts.size.toString(),
                    conflicts.take(MAXIMUM_NAMED_FILES).joinToString(),
                )
            },
        ).ifEmpty { listOf(lastSyncedText(lastSyncedAt)) }.joinToString(separator = "\n")

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
    progress: SyncProgress,
) = Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
    Text(
        text = progress.text(),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val fraction = progress.fraction
    if (fraction == null) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
    } else {
        // Animated, or a run that finishes several files between frames would make the bar jump.
        val animatedFraction by animateFloatAsState(targetValue = fraction, label = "syncProgress")
        LinearProgressIndicator(
            progress = { animatedFraction },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
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
            local.minute.padded(),
        ),
    )
}

private fun Int.padded() = toString().padStart(length = 2, padChar = '0')

/**
 * A full disk fails every file of a run, and a first run on a device edited on both sides can keep a copy of every song;
 * the line under the account is not the place for all their names.
 */
private const val MAXIMUM_NAMED_FILES = 3
