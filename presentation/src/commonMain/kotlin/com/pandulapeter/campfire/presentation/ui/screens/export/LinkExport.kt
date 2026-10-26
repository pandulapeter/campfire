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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboard
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.messages.Message
import com.pandulapeter.campfire.presentation.ui.platform.LocalFilePicker
import com.pandulapeter.campfire.presentation.ui.platform.isCopyConfirmedBySystem
import com.pandulapeter.campfire.presentation.ui.platform.plainTextClipEntry
import com.pandulapeter.campfire.presentation.ui.print.PrintSource
import com.pandulapeter.campfire.presentation.ui.print.qrCodeFileName
import com.pandulapeter.campfire.presentation.ui.share.QrCode
import com.pandulapeter.campfire.presentation.ui.share.ShareTarget
import com.pandulapeter.campfire.presentation.ui.share.qrCodePng
import com.pandulapeter.campfire.presentation.ui.sync.rememberAuthorizationCompletionPage
import kotlinx.coroutines.launch

/**
 * The Link format of one song or setlist as the export screen draws it: its [state] and what each control does.
 * [isStopAsked] is the Stop sharing question, which the screen puts up itself.
 */
internal class LinkExport(
    val state: LinkExportState,
    isStopAsked: MutableState<Boolean>,
    val onAction: (LinkAction) -> Unit,
    val onShare: () -> Unit,
    val onSaveQrCode: () -> Unit,
    val onStopSharing: () -> Unit,
) {
    var isStopAsked by isStopAsked

    val action get() = linkActionOf(state)

    val shared get() = state as? LinkExportState.Shared
}

/**
 * The Link format of [dialog], which shares [target] under [title]. Every control is checked against the screen still
 * being the dialog on screen ([isOpen]), as Save is: the screen stays drawn while it slides away.
 */
@Composable
internal fun rememberLinkExport(
    viewModel: CampfireViewModel,
    dialog: DialogType.Export,
    target: ShareTarget,
    title: String,
    source: PrintSource?,
    isOpen: Boolean,
): LinkExport {
    val controller = viewModel.shareLinkController
    val syncState by viewModel.syncController.syncState.collectAsStateWithLifecycle()
    val shares by controller.shares.collectAsStateWithLifecycle()
    val creations by controller.creations.collectAsStateWithLifecycle()
    val stoppingShareIds by controller.stoppingShareIds.collectAsStateWithLifecycle()
    val state = linkExportStateOf(syncState = syncState, target = target, shares = shares, creation = creations[target], stoppingShareIds = stoppingShareIds)
    // Into the process log, so that a report says when an open screen followed a share made or stopped elsewhere.
    LaunchedEffect(state::class) { println("Share screen: link ${state::class.simpleName}") }
    val link = (state as? LinkExportState.Shared)?.share?.link?.landingUrl
    val isStopAsked = rememberSaveable(dialog) { mutableStateOf(false) }
    // A question restored after process death over a share stopped meanwhile is dropped, so it does not come back over
    // the next link made here.
    LaunchedEffect(state) {
        if (state == LinkExportState.NotShared) isStopAsked.value = false
    }
    val filePicker = LocalFilePicker.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val completionPage = rememberAuthorizationCompletionPage()
    val isOpenNow by rememberUpdatedState(isOpen)
    return LinkExport(
        state = state,
        isStopAsked = isStopAsked,
        onAction = { action ->
            if (isOpenNow) when (action) {
                LinkAction.CONNECT, LinkAction.CONNECT_AGAIN -> {
                    // A connection made again for the permission to make links is what the failure asked for, so it is
                    // not shown again once the account is back.
                    controller.forgetCreation(target)
                    viewModel.syncController.offeredSyncProvider?.let { viewModel.connectSyncProvider(it, completionPage) }
                }
                LinkAction.CANCEL -> viewModel.syncController.cancelSyncConnection()
                LinkAction.CREATE, LinkAction.TRY_AGAIN -> controller.create(target, title)
                LinkAction.COPY -> link?.let {
                    scope.launch {
                        clipboard.setClipEntry(plainTextClipEntry(it))
                        if (!isCopyConfirmedBySystem) viewModel.messageSink.sendMessage(Message.LinkCopied)
                    }
                }
            }
        },
        onShare = { if (isOpenNow && link != null) viewModel.exportController.shareLink(filePicker, dialog, link = link, subject = title) },
        onSaveQrCode = {
            val qrCode = link?.let { QrCode.encode(it) }
            if (isOpenNow && qrCode != null && source != null) {
                viewModel.exportController.exportQrCode(filePicker, dialog, fileName = qrCodeFileName(source)) { qrCodePng(qrCode) }
            }
        },
        onStopSharing = {
            val share = (state as? LinkExportState.Shared)?.share
            isStopAsked.value = false
            if (isOpenNow && share != null) controller.stop(share)
        },
    )
}
