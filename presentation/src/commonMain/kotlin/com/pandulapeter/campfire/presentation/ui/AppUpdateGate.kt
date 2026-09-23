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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
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
import com.pandulapeter.campfire.presentation.ui.components.EmptyStateAction
import com.pandulapeter.campfire.presentation.ui.platform.AppUpdateState
import com.pandulapeter.campfire.presentation.ui.platform.rememberAppUpdateController
import org.jetbrains.compose.resources.painterResource

/**
 * True while the "update required" screen covers the app. That screen is drawn over the app's content, but a dialog,
 * a bottom sheet and a dropdown menu are windows of their own on Android, above the activity's content and so above
 * the screen, where they could still be used. Whatever opens one reads this and composes nothing while it is true;
 * what it was showing stays in its state, so an update that does not install leaves it where it was.
 */
internal val LocalIsCoveredByRequiredUpdate = compositionLocalOf { false }

/**
 * Wraps the app in whatever the store has to say about a newer build of it, which on three of the four platforms is
 * nothing at all (see `AppUpdateState`).
 *
 * The blocking screen is drawn *over* the app rather than in place of it, so that the app behind it is never torn
 * down and rebuilt: a required update is answered by leaving for the Play Store and coming back, and the library
 * the user was looking at should still be where they left it if the update turns out not to install.
 *
 * The controller says what the store has to offer and the gate decides when to act on it. Nothing that ends the
 * process is put in the user's way while the editor holds text that has not been written: the blocking screen, the
 * immediate flow it starts and the Restart offer all wait until that text has been saved or let go of, and Restart
 * waits for a sync run as well, which a required update does not - a run it cuts off is reported as interrupted the
 * ordinary way.
 */
@Composable
internal fun AppUpdateGate(
    modifier: Modifier = Modifier,
    viewModel: CampfireViewModel,
    content: @Composable () -> Unit,
) {
    val controller = rememberAppUpdateController()
    val state = controller.state
    val hasUnsavedEditorChanges by viewModel.hasUnsavedEditorChanges.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    // A required update ends with the process being replaced, and the screen it puts up leaves no way back to
    // the app, so neither is allowed near an editor holding text that has not been written: both wait until it
    // has been saved or let go of. Once the screen is up it stays up - the text can only become unsaved behind
    // it by the file changing underneath an editor nobody typed into - and a rotation must not uncover the app.
    var isRequiredUpdateInTheWay by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state, hasUnsavedEditorChanges) {
        if (state == AppUpdateState.Required && !hasUnsavedEditorChanges) isRequiredUpdateInTheWay = true
    }
    val isRequiredScreenVisible = isRequiredUpdateInTheWay && state == AppUpdateState.Required
    // Started without asking as the screen goes up, which the controller only does once: after that the
    // screen's own button is what starts it again.
    LaunchedEffect(isRequiredScreenVisible) {
        if (isRequiredScreenVisible) controller.startRequiredUpdateOnce()
    }
    Box(modifier = modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalIsCoveredByRequiredUpdate provides isRequiredScreenVisible) {
            content()
        }
        AnimatedVisibility(
            visible = isRequiredScreenVisible,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            AppUpdateRequiredScreen(
                onUpdate = controller::startUpdate,
                onClose = controller::closeApp,
            )
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

        // Restarting is the app ending itself, so it is not offered over text that would go with it, nor over a
        // sync run it would cut off. The offer is still there when the text is saved or the run has finished.
        AppUpdateState.ReadyToInstall -> if (!hasUnsavedEditorChanges && !isSyncing) {
            AppUpdateDialog(
                title = stringResource(Res.string.app_update_downloaded),
                text = stringResource(Res.string.app_update_downloaded_hint),
                confirmLabel = stringResource(Res.string.app_update_restart),
                onConfirm = controller::installUpdate,
                onDismiss = controller::postponeUpdate,
            )
        }

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
 * the one the dispatcher reaches first. It closes the app rather than doing nothing: back on a screen with nothing
 * behind it means leaving, and letting it through instead would navigate an app the user cannot see.
 */
@Composable
private fun AppUpdateRequiredScreen(
    modifier: Modifier = Modifier,
    onUpdate: () -> Unit,
    onClose: () -> Unit,
) = Surface(
    modifier = modifier.fillMaxSize(),
    color = MaterialTheme.colorScheme.background,
) {
    NavigationBackHandler(
        state = rememberNavigationEventState(currentInfo = NavigationEventInfo.None),
        onBackCompleted = onClose,
    )
    Box(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center,
    ) {
        EmptyState(
            icon = painterResource(Res.drawable.ic_update),
            title = stringResource(Res.string.app_update_required),
            hint = stringResource(Res.string.app_update_required_hint),
            actions = listOf(EmptyStateAction(text = stringResource(Res.string.app_update_update), onClick = onUpdate)),
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
