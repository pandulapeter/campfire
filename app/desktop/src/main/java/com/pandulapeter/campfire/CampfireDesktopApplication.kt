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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.configureSwingGlobalsForCompose
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
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
import java.awt.Component
import java.awt.Desktop
import java.awt.Dimension
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.event.WindowFocusListener
import java.beans.PropertyChangeListener
import java.io.File
import javax.swing.SwingUtilities
import kotlin.math.ceil
import kotlin.system.exitProcess
import kotlinx.coroutines.channels.Channel
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * @param args Paths handed over by the operating system, which is how "open with" reaches a desktop application on
 *   Windows and Linux: it launches the app with the file as an argument. macOS sends an event instead, see
 *   [OpenedFiles].
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main(args: Array<String>) {
    // Compose's own set-up, which application() would only do later, has to come before anything that starts the
    // AWT toolkit: on Linux it is what puts the display's scale into sun.java2d.uiScale, which the toolkit reads once,
    // when it starts. Behind the same property application() checks, so that this is the same decision made earlier
    // rather than a second one (application() calling it again is harmless - the library loads once).
    if (System.getProperty("compose.application.configure.swing.globals") == "true") configureSwingGlobalsForCompose()
    // After Compose's set-up and before the first window, since the toolkit reads the name when that window is
    // created - and asking for the toolkit is what starts it.
    setLinuxWindowClassName()
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
        val windowState = rememberWindowState(size = INITIAL_WINDOW_SIZE)
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
        // its own the JDK answers it with System.exit. The request is kept and answered once the editor's unsaved
        // text has been dealt with - performQuit lets the logout, restart or shut down that asked carry on, and
        // cancelQuit is said only where the user actually chose to stay. Cancelling it up front would be
        // NSTerminateCancel, which aborts the whole sequence and has macOS report that Campfire interrupted it, with
        // nothing unsaved anywhere.
        DisposableEffect(Unit) {
            val desktop = if (Desktop.isDesktopSupported()) Desktop.getDesktop().takeIf { it.isSupported(Desktop.Action.APP_QUIT_HANDLER) } else null
            desktop?.setQuitHandler { _, response ->
                // The handler returns before anything is decided: what follows waits for a save, and may put a
                // dialog on screen.
                SwingUtilities.invokeLater {
                    // The process ends with the system's reply rather than with exitApplication, so the listener
                    // for other instances is closed here the way `exit` closes it.
                    val performQuit = {
                        stopListeningForOtherInstances()
                        response.performQuit()
                    }
                    viewModel.value?.requestExit(onExit = performQuit, onCancelled = response::cancelQuit) ?: performQuit()
                }
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
            DisposableEffect(window) {
                window.fitSizeToScreen(windowState)
                // Showing the window hands the unscaled minimum to Windows again, and moving it to a display of another
                // scale changes what the scaled one is.
                val openedListener = object : WindowAdapter() {
                    override fun windowOpened(event: WindowEvent) = window.scaleNativeMinimumSize()
                }
                val displayListener = PropertyChangeListener { window.scaleNativeMinimumSize() }
                window.addWindowListener(openedListener)
                window.addPropertyChangeListener("graphicsConfiguration", displayListener)
                onDispose {
                    window.removeWindowListener(openedListener)
                    window.removePropertyChangeListener("graphicsConfiguration", displayListener)
                }
            }
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
 * Both are in AWT's units, which are the scaled ones Compose's dp map to, so they look the same at any display scaling.
 * The initial size gives the lists room without covering a laptop's screen.
 */
private val MINIMUM_WINDOW_SIZE = DpSize(480.dp, 480.dp)
private val INITIAL_WINDOW_SIZE = DpSize(800.dp, 600.dp)

/**
 * A window smaller than its minimum is grown to it, past the edge of the screen if need be, so on a display with less
 * room than these sizes ask for, both are brought down to the area the task bar or the dock leaves free.
 */
private fun ComposeWindow.fitSizeToScreen(windowState: WindowState) {
    val insets = Toolkit.getDefaultToolkit().getScreenInsets(graphicsConfiguration)
    val bounds = graphicsConfiguration.bounds
    val available = DpSize(
        width = (bounds.width - insets.left - insets.right).dp,
        height = (bounds.height - insets.top - insets.bottom).dp,
    )
    windowState.size = DpSize(min(windowState.size.width, available.width), min(windowState.size.height, available.height))
    minimumSize = Dimension(
        min(MINIMUM_WINDOW_SIZE.width, available.width).value.toInt(),
        min(MINIMUM_WINDOW_SIZE.height, available.height).value.toInt(),
    )
    scaleNativeMinimumSize()
}

/**
 * OpenJDK on Windows hands the minimum size to the system in the units it was given, which Windows takes for physical
 * pixels, while AWT sizes the window itself in scaled ones: at 200% the window could be dragged down to half the
 * minimum. Scaling [Window.setMinimumSize]'s own value up is no way around it, since AWT would then grow the window to
 * that. So the scaled value goes to the peer's private setter directly, which is what the `--add-opens` of the Windows
 * build are for (see build.gradle.kts). The JetBrains Runtime scales it by itself, and anything that goes wrong here
 * leaves the unscaled minimum, which is too small rather than harmful.
 */
private fun ComposeWindow.scaleNativeMinimumSize() {
    if (!System.getProperty("os.name").orEmpty().lowercase().contains("windows")) return
    if (System.getProperty("java.vendor").orEmpty().contains("JetBrains")) return
    if (!isMinimumSizeSet) return
    runCatching {
        val peer = Component::class.java.getDeclaredField("peer").apply { isAccessible = true }.get(this) ?: return
        val transform = graphicsConfiguration.defaultTransform
        Class.forName("sun.awt.windows.WWindowPeer")
            .getDeclaredMethod("setMinSize", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            .apply { isAccessible = true }
            .invoke(
                peer,
                ceil(minimumSize.width * transform.scaleX).toInt(),
                ceil(minimumSize.height * transform.scaleY).toInt(),
            )
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

/**
 * What GNOME and KDE match a window against to find the launcher it came from. AWT derives it from the class at the
 * bottom of the stack with the dots turned into dashes, so the window would announce itself as
 * "com-pandulapeter-campfire-CampfireDesktopApplicationKt": the string alt-tab shows, and the name a pinned shortcut
 * is created under, beside the one the package installed. There is no API for it - the field belongs to the X11
 * toolkit, which is why the packaged Linux launcher opens that package (see build.gradle.kts) - and no other
 * platform has the field at all, so a failure here is a cosmetic loss and never a reason not to start. The value has
 * to stay what `addStartupWmClassToDeb` writes into the installed desktop entry. Asking for the toolkit starts it, and on
 * Linux it reads the display scale when it starts, so this has to come after `configureSwingGlobalsForCompose`.
 */
private fun setLinuxWindowClassName() {
    if (!System.getProperty("os.name").orEmpty().lowercase().contains("linux")) return
    runCatching {
        val toolkit = Toolkit.getDefaultToolkit()
        toolkit.javaClass.getDeclaredField("awtAppClassName").apply {
            isAccessible = true
            set(toolkit, "Campfire")
        }
    }
}
