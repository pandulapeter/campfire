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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.awt.SwingWindow
import androidx.compose.ui.configureSwingGlobalsForCompose
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.window.WindowPlacement
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
import com.pandulapeter.campfire.presentation.ui.theme.interfaceScale
import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.Desktop
import java.awt.Dimension
import java.awt.Toolkit
import java.awt.event.WindowEvent
import java.awt.event.WindowFocusListener
import java.io.File
import javax.swing.SwingUtilities
import kotlin.system.exitProcess
import kotlinx.coroutines.channels.Channel
import org.koin.compose.viewmodel.koinViewModel

/**
 * @param args Paths handed over by the operating system, which is how "open with" reaches a desktop application on
 *   Windows and Linux: it launches the app with the file as an argument. macOS sends an event instead, see
 *   [OpenedFiles].
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main(args: Array<String>) {
    // A Gradle or IDE run has no Campfire.app bundle, so macOS would use the Java launcher or this main class as
    // the application name. AWT reads this once when it first starts, for both the menu bar and app switcher.
    System.setProperty("apple.awt.application.name", "Campfire")
    // The JDK gives the application the light Aqua appearance whatever the system's is. The window follows the app's
    // own theme (TitleBarAppearance), but only once the preferences are read; until then the system's is the better
    // guess, and it is read once, like the name.
    System.setProperty("apple.awt.application.appearance", "system")
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
        // Set before the window is shown, which the content cannot do (see extendContentIntoTitleBar).
        var titleBar by remember { mutableStateOf<ExtendedTitleBar?>(null) }
        // Window itself, with a hook for the window before it is shown.
        SwingWindow(
            state = windowState,
            title = "Campfire",
            onCloseRequest = requestExit,
            icon = appIcon(viewModel.value),
            onPreviewKeyEvent = ::handlePreviewKeyEvent,
            onKeyEvent = { keyEvent -> viewModel.value?.handleKeyEvent(keyEvent, onExit = exit) == true },
            init = { window -> titleBar = window.extendContentIntoTitleBar() },
        ) {
            DisposableEffect(window) {
                window.fitSizeToScreen(windowState)
                onDispose { }
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
                TitleBarInsets(
                    titleBar = titleBar,
                    isFullscreen = windowState.placement == WindowPlacement.Fullscreen,
                ) {
                    val currentViewModel = koinViewModel<CampfireViewModel>()
                    SideEffect { viewModel.value = currentViewModel }
                    titleBar?.let { TitleBarAppearance(window = window, titleBar = it, viewModel = currentViewModel) }
                    CampfireDesktopApp(
                        viewModel = currentViewModel,
                        filesToImport = OpenedFiles.files,
                        onBackgroundColorChanged = { color -> window.setUndrawnAreaColor(Color(color.toArgb())) },
                    )
                }
            }
        }
    }
}

/**
 * The initial size is in AWT's units, which are the scaled ones the platform's dp map to, so it looks the same at any
 * display scaling, and it gives the lists room without covering a laptop's screen. The minimum is in the app's own dp
 * instead - the smallest window its layouts are made for - and the app draws a dp smaller than the platform does
 * (`interfaceScale`), so the window it takes is smaller by the same amount.
 */
private val MINIMUM_CONTENT_SIZE = DpSize(480.dp, 480.dp)
private val INITIAL_WINDOW_SIZE = DpSize(800.dp, 600.dp)

/**
 * What the window shows wherever the app has not been drawn yet - the edge a fast resize uncovers before the next frame
 * fills it - which is white unless it is told otherwise. On macOS that is the window's own background, and the native
 * surface the app is rendered into, a heavyweight component that takes its color when it is created rather than
 * following its ancestors'. On Windows it is the opaque Swing panels between the two as well, which Swing repaints the
 * uncovered edge with before the app's next frame arrives, in the look and feel's panel gray, since Compose installs
 * the system's.
 */
private fun ComposeWindow.setUndrawnAreaColor(color: Color) {
    background = color
    fun Component.paintUndrawnArea() {
        if (isWindows || !isLightweight) background = color
        (this as? Container)?.components?.forEach { it.paintUndrawnArea() }
    }
    rootPane.paintUndrawnArea()
}

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
        min(MINIMUM_CONTENT_SIZE.width * interfaceScale, available.width).value.toInt(),
        min(MINIMUM_CONTENT_SIZE.height * interfaceScale, available.height).value.toInt(),
    )
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
