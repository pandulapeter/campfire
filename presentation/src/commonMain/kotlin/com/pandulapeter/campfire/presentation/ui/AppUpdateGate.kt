/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.app_update_available
import com.pandulapeter.campfire.presentation.resources.app_update_available_hint
import com.pandulapeter.campfire.presentation.resources.app_update_downloaded
import com.pandulapeter.campfire.presentation.resources.app_update_downloaded_hint
import com.pandulapeter.campfire.presentation.resources.app_update_later
import com.pandulapeter.campfire.presentation.resources.app_update_required
import com.pandulapeter.campfire.presentation.resources.app_update_required_hint
import com.pandulapeter.campfire.presentation.resources.app_update_restart
import com.pandulapeter.campfire.presentation.resources.app_update_update
import com.pandulapeter.campfire.presentation.resources.ic_update
import com.pandulapeter.campfire.presentation.ui.components.EmptyState
import com.pandulapeter.campfire.presentation.ui.platform.AppUpdateState
import com.pandulapeter.campfire.presentation.ui.platform.rememberAppUpdateController
import org.jetbrains.compose.resources.painterResource

/**
 * Wraps the app in whatever the store has to say about a newer build of it, which on three of the four platforms is
 * nothing at all (see `AppUpdateState`).
 *
 * The blocking screen is drawn *over* the app rather than in place of it, so that the app behind it is never torn
 * down and rebuilt: a required update is answered by leaving for the Play Store and coming back, and the library
 * the user was looking at should still be where they left it if the update turns out not to install.
 */
@Composable
internal fun AppUpdateGate(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val controller = rememberAppUpdateController()
    val state = controller.state
    Box(modifier = modifier.fillMaxSize()) {
        content()
        AnimatedVisibility(
            visible = state == AppUpdateState.Required,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            AppUpdateRequiredScreen(onUpdate = controller::startUpdate)
        }
    }
    when (state) {
        AppUpdateState.Optional -> AppUpdateDialog(
            title = stringResource(Res.string.app_update_available),
            text = stringResource(Res.string.app_update_available_hint),
            confirmLabel = stringResource(Res.string.app_update_update),
            onConfirm = controller::startUpdate,
            onDismiss = controller::postponeUpdate,
        )

        AppUpdateState.ReadyToInstall -> AppUpdateDialog(
            title = stringResource(Res.string.app_update_downloaded),
            text = stringResource(Res.string.app_update_downloaded_hint),
            confirmLabel = stringResource(Res.string.app_update_restart),
            onConfirm = controller::installUpdate,
            onDismiss = controller::postponeUpdate,
        )

        // A download in progress is the store's own business to report, and it does so in the notification drawer.
        AppUpdateState.NotAvailable, AppUpdateState.Required, AppUpdateState.Downloading -> Unit
    }
}

/**
 * Opaque and edge to edge, because it has to cover an app the user must not be able to go on using, chrome included.
 *
 * `Surface` is what does the covering rather than a `Box` with a background: a non-interactive one swallows every
 * pointer event that reaches it, which is the only thing standing between a tap and the app still composed behind
 * it. The back gesture is the other way through and is taken here, composed after the app so that this handler is
 * the one the dispatcher reaches first; the update and leaving Campfire altogether are the only two ways out.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun AppUpdateRequiredScreen(
    modifier: Modifier = Modifier,
    onUpdate: () -> Unit,
) = Surface(
    modifier = modifier.fillMaxSize(),
    color = MaterialTheme.colorScheme.background,
) {
    BackHandler { }
    Box(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center,
    ) {
        EmptyState(
            icon = painterResource(Res.drawable.ic_update),
            title = stringResource(Res.string.app_update_required),
            hint = stringResource(Res.string.app_update_required_hint),
            actionText = stringResource(Res.string.app_update_update),
            onAction = onUpdate,
        )
    }
}

/**
 * Dismissible on purpose, in every way a dialog can be: an update offered is still an update the user may say no to,
 * and the tap outside means the same thing the "Later" button does.
 */
@Composable
private fun AppUpdateDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) = AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = { Text(text) },
    confirmButton = {
        TextButton(onClick = onConfirm) { Text(confirmLabel) }
    },
    dismissButton = {
        TextButton(onClick = onDismiss) { Text(stringResource(Res.string.app_update_later)) }
    },
)
