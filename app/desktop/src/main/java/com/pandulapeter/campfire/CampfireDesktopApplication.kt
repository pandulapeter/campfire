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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.pandulapeter.campfire.di.startCampfireDependencyGraph
import com.pandulapeter.campfire.presentation.ui.CampfireDesktopApp
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.handleKeyEvent
import com.pandulapeter.campfire.resources.Res
import com.pandulapeter.campfire.resources.app_icon
import java.awt.Desktop
import java.awt.Dimension
import javax.swing.SwingUtilities
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * @param args Paths handed over by the operating system, which is how "open with" reaches a desktop application on
 *   Windows and Linux: it launches the app with the file as an argument. macOS sends an event instead, see
 *   [OpenedFiles].
 */
fun main(args: Array<String>) {
    OpenedFiles.listenForSystemRequests()
    OpenedFiles.open(args.toList())
    startCampfireDependencyGraph()
    application {
        // The view model is created inside the window (which owns the ViewModelStore), but the key handler needs it here.
        val viewModel = remember { mutableStateOf<CampfireViewModel?>(null) }
        // Closing the window leaves the editor as surely as Escape does, so it asks about unsaved text the same way,
        // and it waits for a save that is still being written, since exitApplication ends the process.
        val requestExit = { viewModel.value?.requestExit(::exitApplication) ?: exitApplication() }
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
            title = "Campfire",
            onCloseRequest = requestExit,
            icon = painterResource(Res.drawable.app_icon),
            onKeyEvent = { keyEvent -> viewModel.value?.handleKeyEvent(keyEvent, onExit = ::exitApplication) == true },
        ) {
            window.minimumSize = Dimension(400, 400)
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
