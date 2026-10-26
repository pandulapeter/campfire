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
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboard
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.data.model.library.LibraryFiles
import com.pandulapeter.campfire.data.model.preferences.PrintSettings
import com.pandulapeter.campfire.data.model.share.Share
import com.pandulapeter.campfire.data.model.share.SharedLinkKind
import com.pandulapeter.campfire.data.model.share.SharingFailure
import com.pandulapeter.campfire.data.model.sync.SyncState
import com.pandulapeter.campfire.presentation.localization.pluralStringResource
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_cloud
import com.pandulapeter.campfire.presentation.resources.ic_delete
import com.pandulapeter.campfire.presentation.resources.settings_group_all_links
import com.pandulapeter.campfire.presentation.resources.settings_group_shared_links
import com.pandulapeter.campfire.presentation.resources.settings_sharing_description
import com.pandulapeter.campfire.presentation.resources.settings_sharing_nothing_shared
import com.pandulapeter.campfire.presentation.resources.settings_sharing_stale
import com.pandulapeter.campfire.presentation.resources.settings_sharing_stop_all
import com.pandulapeter.campfire.presentation.resources.settings_sharing_stop_all_confirmation
import com.pandulapeter.campfire.presentation.resources.share_authorization
import com.pandulapeter.campfire.presentation.resources.share_connect_again
import com.pandulapeter.campfire.presentation.resources.share_newer_version
import com.pandulapeter.campfire.presentation.resources.share_scope_missing
import com.pandulapeter.campfire.presentation.resources.share_stop
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.ActionListItem
import com.pandulapeter.campfire.presentation.ui.dialogs.ConfirmationDialog
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.messages.Message
import com.pandulapeter.campfire.presentation.ui.navigation.SettingsTab
import com.pandulapeter.campfire.presentation.ui.platform.isCopyConfirmedBySystem
import com.pandulapeter.campfire.presentation.ui.platform.plainTextClipEntry
import com.pandulapeter.campfire.presentation.ui.settingsPage.AnimatedSettingsRow
import com.pandulapeter.campfire.presentation.ui.settingsPage.SECTION_GAP
import com.pandulapeter.campfire.presentation.ui.settingsPage.SettingsMessage
import com.pandulapeter.campfire.presentation.ui.settingsPage.SettingsSection
import com.pandulapeter.campfire.presentation.ui.share.StopSharingConfirmation
import com.pandulapeter.campfire.presentation.ui.sync.rememberAuthorizationCompletionPage
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

/**
 * The Sharing tab: everything shared as a link from the connected account, on any device, or the invitation to connect
 * one. Its stages ([sharingStageOf]) crossfade, each a user's tap or the connection's answer to one, except the list
 * arriving over "Nothing shared yet", which is data arriving and is simply there.
 *
 * The tab settling into view reads the account's list once per visit; until that lands, and where it cannot (offline),
 * the list this device last read is shown.
 */
@Composable
internal fun SharingSections(viewModel: CampfireViewModel) {
    val controller = viewModel.shareLinkController
    val syncState = viewModel.syncController.syncState.collectAsStateWithLifecycle()
    val shares by controller.shares.collectAsStateWithLifecycle()
    val stoppingShareIds by controller.stoppingShareIds.collectAsStateWithLifecycle()
    val isStoppingEverything by controller.isStoppingEverything.collectAsStateWithLifecycle()
    var refreshFailure by remember { mutableStateOf<SharingFailure?>(null) }
    // Derived, so that a run reporting every file it moves recomposes none of this.
    val isConnected by remember(syncState) { derivedStateOf { syncState.value is SyncState.Connected } }
    val stage by remember(syncState) { derivedStateOf { sharingStageOf(syncState.value, refreshFailure, shares) } }
    // The pager composes every tab with the screen, so the tab being composed is no sign of anybody looking at it.
    val isShown = viewModel.navigator.settingsTab == SettingsTab.SHARING
    LaunchedEffect(isShown, isConnected) {
        if (!isConnected) refreshFailure = null else if (isShown) refreshFailure = controller.refresh()
    }
    // Saved, so that a confirmation outlives the process being stopped behind it; by id, since a share is no saveable value.
    var shareIdAskedToStop by rememberSaveable { mutableStateOf<String?>(null) }
    var isStopEverythingAsked by rememberSaveable { mutableStateOf(false) }
    val completionPage = rememberAuthorizationCompletionPage()
    AnimatedContent(
        targetState = stage,
        transitionSpec = {
            if (initialState == SharingStage.NOTHING_SHARED && targetState == SharingStage.SHARED) {
                EnterTransition.None togetherWith ExitTransition.None using SizeTransform(clip = false) { _, _ -> snap() }
            } else {
                fadeIn() togetherWith fadeOut() using SizeTransform(clip = false)
            }
        },
    ) { shown ->
        Column(verticalArrangement = Arrangement.spacedBy(SECTION_GAP)) {
            SettingsSection(title = stringResource(Res.string.settings_group_shared_links)) {
                when (shown) {
                    SharingStage.NOT_CONNECTED -> {
                        SettingsMessage(text = stringResource(Res.string.settings_sharing_description))
                        DisconnectedSyncSettings(viewModel = viewModel, syncState = syncState.value)
                    }
                    SharingStage.CONNECTING -> {
                        SettingsMessage(text = stringResource(Res.string.settings_sharing_description))
                        ConnectingSyncSettings(viewModel = viewModel)
                    }
                    SharingStage.CONNECT_AGAIN -> {
                        SettingsMessage(
                            text = stringResource(
                                if (refreshFailure == SharingFailure.AUTHORIZATION) Res.string.share_authorization else Res.string.share_scope_missing,
                            ),
                        )
                        ActionListItem(
                            title = stringResource(Res.string.share_connect_again),
                            icon = painterResource(Res.drawable.ic_cloud),
                            onClick = {
                                (syncState.value as? SyncState.Connected)?.let { viewModel.connectSyncProvider(it.account.providerId, completionPage) }
                            },
                        )
                    }
                    SharingStage.NOTHING_SHARED -> SettingsMessage(text = stringResource(Res.string.settings_sharing_nothing_shared))
                    SharingStage.SHARED -> SharedLinks(
                        viewModel = viewModel,
                        shares = shares,
                        busyShareIds = if (isStoppingEverything) shares.mapTo(mutableSetOf()) { it.shareId } else stoppingShareIds,
                        onStop = { shareIdAskedToStop = it.shareId },
                    )
                }
                // A newer version's list is neither shown afresh nor changed; the one last read here still is.
                AnimatedSettingsRow(isVisible = isConnected && refreshFailure == SharingFailure.NEWER_VERSION) {
                    SettingsMessage(text = stringResource(Res.string.share_newer_version))
                }
                AnimatedSettingsRow(isVisible = isConnected && isStaleSharingList(refreshFailure)) {
                    SettingsMessage(text = stringResource(Res.string.settings_sharing_stale))
                }
            }
            // Alone in its own section and last, as the Library tab keeps the rows that cannot be undone.
            if (shown == SharingStage.SHARED) {
                SettingsSection(title = stringResource(Res.string.settings_group_all_links)) {
                    ActionListItem(
                        title = stringResource(Res.string.settings_sharing_stop_all),
                        icon = painterResource(Res.drawable.ic_delete),
                        isEnabled = !isStoppingEverything,
                        isEmphasized = false,
                        onClick = { isStopEverythingAsked = true },
                    )
                }
            }
        }
    }
    shares.firstOrNull { it.shareId == shareIdAskedToStop }?.let { share ->
        StopSharingConfirmation(
            title = share.title,
            onDismiss = { shareIdAskedToStop = null },
            onConfirm = {
                shareIdAskedToStop = null
                controller.stop(share)
            },
        )
    }
    if (isStopEverythingAsked && shares.isNotEmpty()) {
        ConfirmationDialog(
            title = stringResource(Res.string.settings_sharing_stop_all),
            text = pluralStringResource(Res.plurals.settings_sharing_stop_all_confirmation, shares.size, shares.size),
            confirmLabel = stringResource(Res.string.share_stop),
            onDismiss = { isStopEverythingAsked = false },
            onConfirm = {
                isStopEverythingAsked = false
                controller.stopEverything()
            },
        )
    }
}

/**
 * The list of shares, with what its menus do: Copy link as the export screen copies it, and Show QR code opening the
 * export screen of the item on the Link format, which draws the code and offers to save it.
 */
@Composable
private fun ColumnScope.SharedLinks(
    viewModel: CampfireViewModel,
    shares: List<Share>,
    busyShareIds: Set<String>,
    onStop: (Share) -> Unit,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    SharedLinkList(
        shares = shares,
        busyShareIds = busyShareIds,
        canShowQrCode = { exportDialogOf(viewModel, it) != null },
        onCopy = { share ->
            scope.launch {
                clipboard.setClipEntry(plainTextClipEntry(share.link.landingUrl))
                if (!isCopyConfirmedBySystem) viewModel.messageSink.sendMessage(Message.LinkCopied)
            }
        },
        onShowQrCode = { share -> exportDialogOf(viewModel, share)?.let(viewModel.dialogHost::showDialog) },
        onStop = onStop,
    )
}

/**
 * The export screen of [share]'s item, opened on the Link format to show its QR code, for this opening only, the format
 * the user exports in kept, or null where the library lacks it.
 */
private fun exportDialogOf(viewModel: CampfireViewModel, share: Share) = when (share.kind) {
    SharedLinkKind.SONG -> viewModel.libraryState.songsByFileName.value.let { songs ->
        songs[share.fileName] ?: songs.entries.firstOrNull { LibraryFiles.isSameLibraryName(it.key, share.fileName) }?.value
    }?.let { DialogType.Export(song = it, format = PrintSettings.Format.LINK) }
    SharedLinkKind.SETLIST -> viewModel.libraryState.setlists.value
        .firstOrNull { LibraryFiles.isSameLibraryName(it.fileName, share.fileName) }
        ?.let { DialogType.Export(setlist = it, format = PrintSettings.Format.LINK) }
}
