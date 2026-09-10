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
import com.pandulapeter.campfire.presentation.resources.settings_sync_description
import com.pandulapeter.campfire.presentation.resources.settings_sync_disconnect
import com.pandulapeter.campfire.presentation.resources.settings_sync_failed_authorization
import com.pandulapeter.campfire.presentation.resources.settings_sync_failed_network
import com.pandulapeter.campfire.presentation.resources.settings_sync_failed_storage
import com.pandulapeter.campfire.presentation.resources.settings_sync_failed_unknown
import com.pandulapeter.campfire.presentation.resources.settings_sync_interrupted
import com.pandulapeter.campfire.presentation.resources.settings_sync_last_synced_days
import com.pandulapeter.campfire.presentation.resources.settings_sync_last_synced_hours
import com.pandulapeter.campfire.presentation.resources.settings_sync_last_synced_minutes
import com.pandulapeter.campfire.presentation.resources.settings_sync_last_synced_moments_ago
import com.pandulapeter.campfire.presentation.resources.settings_sync_never
import com.pandulapeter.campfire.presentation.resources.settings_sync_now
import com.pandulapeter.campfire.presentation.resources.settings_sync_preparing
import com.pandulapeter.campfire.presentation.resources.settings_sync_progress
import com.pandulapeter.campfire.presentation.resources.settings_sync_redirect_page_message
import com.pandulapeter.campfire.presentation.resources.settings_sync_redirect_page_title
import com.pandulapeter.campfire.presentation.resources.settings_sync_result
import com.pandulapeter.campfire.presentation.resources.settings_sync_unavailable
import com.pandulapeter.campfire.presentation.resources.settings_sync_up_to_date
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.data.source.remote.api.model.AuthorizationCompletionPage
import com.pandulapeter.campfire.presentation.ui.components.ActionListItem
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
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
                    onClick = viewModel::disconnectSyncProvider
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

        is SyncOutcome.Success -> when {
            outcome.summary.conflicts.isNotEmpty() -> stringResource(
                Res.string.settings_sync_conflicts,
                outcome.summary.conflicts.joinToString()
            )

            outcome.summary.hasChanges -> stringResource(
                Res.string.settings_sync_result,
                outcome.summary.downloaded,
                outcome.summary.uploaded,
                outcome.summary.deletedLocally + outcome.summary.deletedRemotely
            )

            else -> stringResource(Res.string.settings_sync_up_to_date)
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
    stringResource(Res.string.settings_sync_progress, completed, total)
}

/**
 * Coarse on purpose: sync is not something the user times, so "a moment ago" and "3 hours ago" say everything a
 * clock time would, in a form that needs no date formatting on four platforms.
 */
@Composable
private fun lastSyncedText(lastSyncedAt: Long?): String {
    if (lastSyncedAt == null) return stringResource(Res.string.settings_sync_never)
    val elapsedMinutes = ((Clock.System.now().toEpochMilliseconds() - lastSyncedAt) / 60_000L).coerceAtLeast(0)
    return when {
        elapsedMinutes < 1 -> stringResource(Res.string.settings_sync_last_synced_moments_ago)
        elapsedMinutes < 60 -> stringResource(Res.string.settings_sync_last_synced_minutes, elapsedMinutes.toInt())
        elapsedMinutes < 60 * 24 -> stringResource(Res.string.settings_sync_last_synced_hours, (elapsedMinutes / 60).toInt())
        else -> stringResource(Res.string.settings_sync_last_synced_days, (elapsedMinutes / (60 * 24)).toInt())
    }
}

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
