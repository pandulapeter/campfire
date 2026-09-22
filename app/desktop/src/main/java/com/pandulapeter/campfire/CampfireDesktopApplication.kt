/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.pandulapeter.campfire.di.startCampfireDependencyGraph
import com.pandulapeter.campfire.presentation.ui.CampfireDesktopApp
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.handleKeyEvent
import com.pandulapeter.campfire.presentation.ui.handlePreviewKeyEvent
import com.pandulapeter.campfire.presentation.ui.resetEscapeKey
import com.pandulapeter.campfire.presentation.ui.platform.desktopDataDirectory
import com.pandulapeter.campfire.resources.Res
import com.pandulapeter.campfire.resources.app_icon
import java.awt.Desktop
import java.awt.Dimension
import java.awt.event.WindowEvent
import java.awt.event.WindowFocusListener
import java.io.File
import javax.swing.SwingUtilities
import kotlin.system.exitProcess
import kotlinx.coroutines.channels.Channel
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * @param args Paths handed over by the operating system, which is how "open with" reaches a desktop application on
 *   Windows and Linux: it launches the app with the file as an argument. macOS sends an event instead, see
 *   [OpenedFiles].
 */
fun main(args: Array<String>) {
    val activations = Channel<Unit>(Channel.CONFLATED)
    val isFirstInstance = claimSingleInstance(
        dataDirectory = desktopDataDirectory(),
        paths = args.map { File(it).absolutePath },
        onActivated = { paths ->
            OpenedFiles.open(paths)
            activations.trySend(Unit)
        },
    )
    // Nothing has been started yet, so there is nothing to wind down - and Koin must not be, since its singletons
    // are what would read the library a second time.
    if (!isFirstInstance) exitProcess(0)
    OpenedFiles.listenForSystemRequests()
    OpenedFiles.open(args.toList())
    startCampfireDependencyGraph()
    application {
        val windowState = rememberWindowState()
        // The view model is created inside the window (which owns the ViewModelStore), but the key handler needs it here.
        val viewModel = remember { mutableStateOf<CampfireViewModel?>(null) }
        // From the moment the app decides to go, another process's files are not accepted any more: this one would only
        // acknowledge them and exit. The lock stays until the process is gone, so a newcomer waits for it
        // (claimSingleInstance).
        val exit = {
            stopListeningForOtherInstances()
            exitApplication()
        }
        // Closing the window leaves the editor as surely as Escape does, so it asks about unsaved text the same way,
        // and it waits for a save that is still being written, since exitApplication ends the process.
        val requestExit = { viewModel.value?.requestExit(exit) ?: exit() }
        // Quitting from the macOS application menu or with Cmd+Q never reaches onCloseRequest: without a handler of
        // its own the JDK answers it with System.exit. The quit is cancelled and asked for the way closing the window
        // is, which ends in exitApplication all the same once there is nothing left to lose.
        DisposableEffect(Unit) {
            val desktop = if (Desktop.isDesktopSupported()) Desktop.getDesktop().takeIf { it.isSupported(Desktop.Action.APP_QUIT_HANDLER) } else null
            desktop?.setQuitHandler { _, response ->
                response.cancelQuit()
                SwingUtilities.invokeLater { requestExit() }
            }
            onDispose { desktop?.setQuitHandler(null) }
        }
        Window(
            state = windowState,
            title = "Campfire",
            onCloseRequest = requestExit,
            icon = painterResource(Res.drawable.app_icon),
            onPreviewKeyEvent = ::handlePreviewKeyEvent,
            onKeyEvent = { keyEvent -> viewModel.value?.handleKeyEvent(keyEvent, onExit = exit) == true },
        ) {
            window.minimumSize = Dimension(400, 400)
            DisposableEffect(window) {
                val focusListener = object : WindowFocusListener {
                    override fun windowGainedFocus(event: WindowEvent) = Unit
                    override fun windowLostFocus(event: WindowEvent) = resetEscapeKey()
                }
                window.addWindowFocusListener(focusListener)
                onDispose { window.removeWindowFocusListener(focusListener) }
            }
            // Another process was asked to open Campfire and handed over to this one, so this is the window the
            // user is looking for.
            LaunchedEffect(Unit) {
                for (activation in activations) {
                    windowState.isMinimized = false
                    window.bringForward()
                }
            }
            CompositionLocalProvider(
                LocalLayoutDirection.providesDefault(LayoutDirection.Ltr)
            ) {
                val currentViewModel = koinViewModel<CampfireViewModel>()
                SideEffect { viewModel.value = currentViewModel }
                CampfireDesktopApp(
                    viewModel = currentViewModel,
                    filesToImport = OpenedFiles.files,
                )
            }
        }
    }
}

/**
 * Raises the window as far as the platform lets an application raise itself. Windows only lets the foreground
 * process take the focus, and this one is not - the process the user just started is - so `toFront` alone ends in
 * a flashing task bar button there; being always on top for a moment is what moves the window above the others
 * regardless. A Wayland compositor may refuse both and show its own "Campfire is ready" notice, which is its call.
 */
private fun ComposeWindow.bringForward() {
    isVisible = true
    val wasAlwaysOnTop = isAlwaysOnTop
    isAlwaysOnTop = true
    toFront()
    isAlwaysOnTop = wasAlwaysOnTop
    requestFocus()
    if (Desktop.isDesktopSupported()) {
        Desktop.getDesktop().takeIf { it.isSupported(Desktop.Action.APP_REQUEST_FOREGROUND) }?.requestForeground(true)
    }
}
