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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalPlatformWindowInsets
import androidx.compose.ui.platform.PlatformInsets
import androidx.compose.ui.platform.PlatformWindowInsets
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jetbrains.JBR
import com.jetbrains.WindowDecorations
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.theme.isDarkTheme
import java.awt.AWTEvent
import java.awt.Toolkit
import java.awt.event.MouseEvent
import javax.swing.SwingUtilities

/**
 * What is left of the system's title bar once the window's content is laid out under it: a strip of [height] at the
 * top that the system still drags the window by, with its window buttons drawn over the app's own background.
 *
 * @param windowsTitleBar The JetBrains Runtime's title bar on Windows, which is where the buttons' colors are set.
 */
internal class ExtendedTitleBar(
    val height: Dp,
    val windowsTitleBar: WindowDecorations.CustomTitleBar?,
)

/**
 * The title bar the system draws for a Java window is its own light gray (or on Windows, the accent or the system's
 * theme) whatever the app looks like, so on macOS and Windows the window's content is laid out under it instead,
 * keeping only the window buttons: the app's own surface is then the title bar, in every theme and color. The title
 * is not drawn, since the app bar is where the screens say where the user is. Called before the window is shown,
 * since the JDK does not lay the content out again when this changes on a window already on screen.
 *
 * @return The strip the content is laid out under, or null where the window keeps the system's title bar: on Linux,
 *   where the window manager draws it, and on a runtime other than the JetBrains Runtime on Windows.
 */
internal fun ComposeWindow.extendContentIntoTitleBar(): ExtendedTitleBar? = when {
    isMacOs -> {
        // The system handles the mouse over the strip before the content sees it, so it drags the window by itself.
        rootPane.putClientProperty("apple.awt.fullWindowContent", true)
        rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
        rootPane.putClientProperty("apple.awt.windowTitleVisible", false)
        ExtendedTitleBar(height = MAC_TITLE_BAR_HEIGHT, windowsTitleBar = null)
    }
    isWindows -> extendContentIntoWindowsTitleBar()
    else -> null
}

/**
 * The JetBrains Runtime's custom title bar, which draws the caption buttons over the content and gives the rest of
 * the strip the title bar's behavior - dragging, snapping, a double click that maximizes, the system menu - but only
 * where nothing in the window listens to the mouse, and Compose's canvas listens everywhere. So every mouse event over
 * the strip is marked as the title bar's, which the runtime asks for per event: an event listener of the toolkit runs
 * after it has made its own guess and before it acts on it. Nothing of the app is ever under the strip, which the
 * screens keep clear of ([TitleBarInsets]), so there is nothing there to take the mouse away from. The listener lives
 * as long as the process does, like the one window it serves.
 */
private fun ComposeWindow.extendContentIntoWindowsTitleBar(): ExtendedTitleBar? {
    val decorations = JBR.getWindowDecorations() ?: return null
    val titleBar = decorations.createCustomTitleBar().apply { height = WINDOWS_TITLE_BAR_HEIGHT.value }
    decorations.setCustomTitleBar(this, titleBar)
    Toolkit.getDefaultToolkit().addAWTEventListener(
        { event ->
            if (event is MouseEvent && event.id != MouseEvent.MOUSE_EXITED && event.id != MouseEvent.MOUSE_WHEEL) {
                val component = event.component
                if (component != null && SwingUtilities.getWindowAncestor(component) === this) {
                    // The height is measured from the top of the client area, which is where the root pane starts.
                    if (SwingUtilities.convertPoint(component, event.point, rootPane).y < titleBar.height) {
                        titleBar.forceHitTest(false)
                    }
                }
            }
        },
        AWTEvent.MOUSE_EVENT_MASK or AWTEvent.MOUSE_MOTION_EVENT_MASK,
    )
    return ExtendedTitleBar(height = WINDOWS_TITLE_BAR_HEIGHT, windowsTitleBar = titleBar)
}

/**
 * Draws the window buttons, and on macOS the rim along the window's top edge, for the theme the app is in rather than
 * the system's, since with the content laid out under them the app's background is theirs: dark mode buttons on a
 * light theme are all but invisible, and the other way around. The macOS property exists only in the JetBrains
 * Runtime, which the build packages for this; any other JDK ignores it and keeps the system's appearance.
 */
@Composable
internal fun TitleBarAppearance(
    window: ComposeWindow,
    titleBar: ExtendedTitleBar,
    viewModel: CampfireViewModel,
) {
    val isDarkTheme = viewModel.userPreferences.collectAsState().value?.uiMode.isDarkTheme()
    SideEffect {
        if (isMacOs) {
            window.rootPane.putClientProperty("apple.awt.windowAppearance", if (isDarkTheme) "NSAppearanceNameDarkAqua" else "NSAppearanceNameAqua")
        }
        // Every property set redraws the title bar, and this runs with every recomposition.
        titleBar.windowsTitleBar?.takeIf { it.properties[WINDOWS_DARK_CONTROLS] != isDarkTheme }?.putProperty(WINDOWS_DARK_CONTROLS, isDarkTheme)
    }
}

/**
 * Tells the shared UI about the strip [extendContentIntoTitleBar] lays the content under, as the system bar inset at
 * the top that Android's status bar and iOS's are, so that every screen already keeps its content clear of the window
 * buttons while its background reaches under them. A full screen window has no title bar (on macOS, until the pointer
 * reaches the top of the screen, and then the system draws it over the content), so it gets none.
 *
 * Compose Desktop has no public way to set the insets; this is the composition local its own `WindowInsets` read.
 */
@OptIn(InternalComposeUiApi::class)
@Composable
internal fun TitleBarInsets(
    titleBar: ExtendedTitleBar?,
    isFullscreen: Boolean,
    content: @Composable () -> Unit,
) {
    if (titleBar == null || isFullscreen) return content()
    val platformInsets = LocalPlatformWindowInsets.current
    val titleBarHeight = with(LocalDensity.current) { titleBar.height.roundToPx() }
    val insets = remember(platformInsets, titleBarHeight) {
        object : PlatformWindowInsets by platformInsets {
            override val captionBar = PlatformInsets(top = titleBarHeight)
            override val systemBars = PlatformInsets(top = titleBarHeight)

            // A dialog or a popup asks for the insets without the ones it has already kept clear of.
            override fun excluding(safeInsets: Boolean, ime: Boolean) = if (safeInsets) platformInsets.excluding(safeInsets, ime) else this
        }
    }
    CompositionLocalProvider(LocalPlatformWindowInsets provides insets, content = content)
}

private val isMacOs = System.getProperty("os.name").orEmpty().lowercase().contains("mac")

private val isWindows = System.getProperty("os.name").orEmpty().lowercase().contains("windows")

/**
 * The height of a macOS title bar without a toolbar, in points, which is what a dp is at the window's own density.
 */
private val MAC_TITLE_BAR_HEIGHT = 28.dp

/**
 * The height of a Windows 11 title bar and its caption buttons, in the scaled pixels a dp is at the window's density.
 */
private val WINDOWS_TITLE_BAR_HEIGHT = 32.dp

/** Whether the caption buttons are drawn for a dark background (light icons) or a light one. */
private const val WINDOWS_DARK_CONTROLS = "controls.dark"
