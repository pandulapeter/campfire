/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
@file:OptIn(ExperimentalWasmJsInterop::class)

package com.pandulapeter.campfire.presentation.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.presentation.ui.navigation.BrowserHistoryEffect
import com.pandulapeter.campfire.presentation.ui.navigation.navigateToBrowserAddress
import com.pandulapeter.campfire.presentation.ui.platform.LocalFilePicker
import com.pandulapeter.campfire.presentation.ui.platform.WebFilePicker
import com.pandulapeter.campfire.presentation.ui.platform.appIconColor
import com.pandulapeter.campfire.presentation.ui.platform.appIconThemeColor
import com.pandulapeter.campfire.presentation.ui.platform.droppedFiles
import com.pandulapeter.campfire.presentation.ui.theme.colorSchemePair
import com.pandulapeter.campfire.presentation.ui.theme.isDarkTheme
import kotlinx.browser.window
import org.w3c.dom.AddEventListenerOptions
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.events.WheelEvent
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.unsafeCast
import kotlin.math.exp
import org.koin.compose.viewmodel.koinViewModel

/**
 * Web shell of the shared UI. Links open in a new browser tab, files dropped on the page are imported, and the
 * browser asks before the page is left with unsaved text in the editor. Escape reaches Compose from a focused text
 * field too ([startForwardingEscapeKey]), Ctrl / Cmd + S is kept from the browser's own "Save page as"
 * ([startSuppressingBrowserSave]), and Ctrl / Cmd + F opens the search of a list screen in place of the browser's
 * find bar ([SearchShortcutEffect]). The browser's zoom shortcuts change the text size of the song details screen
 * instead of zooming the page ([SongTextZoomEffect]). Every screen has an address of its own, and the browser's
 * history follows the app's back stack ([BrowserHistoryEffect]). The browser's own toolbar is painted in the palette
 * the user chose ([BrowserThemeColorEffect]), and the favicon is the icon of that palette ([FaviconEffect]).
 */
@Composable
fun CampfireWebApp(
    viewModel: CampfireViewModel = koinViewModel(),
) = CompositionLocalProvider(
    LocalFilePicker provides WebFilePicker
) {
    DisposableEffect(Unit) {
        startForwardingEscapeKey()
        startSuppressingBrowserSave()
        onDispose {
            stopForwardingEscapeKey()
            stopSuppressingBrowserSave()
        }
    }
    // Collected in an effect rather than read as lifecycle-aware state: a tab that is closed from the tab strip
    // while another one is in front is a hidden page, and that is no moment to have stopped listening.
    // In the composition rather than in an effect, which is what navigateOnLaunch asks of its caller: the launch
    // screen has to know it is waiting for the address before the library it waits for can have been read.
    remember(viewModel) { viewModel.navigateToBrowserAddress() }
    BrowserHistoryEffect(viewModel)
    SearchShortcutEffect(viewModel)
    SongTextZoomEffect(viewModel)
    BrowserThemeColorEffect(viewModel)
    FaviconEffect(viewModel)
    LaunchedEffect(viewModel) {
        viewModel.hasUnsavedEditorChanges.collect { hasUnsavedChanges ->
            if (hasUnsavedChanges) startWarningBeforeUnload() else stopWarningBeforeUnload()
        }
    }
    DisposableEffect(Unit) {
        onDispose { stopWarningBeforeUnload() }
    }
    CampfireApp(
        viewModel = viewModel,
        urlOpener = ::openInNewTab,
        filesToImport = remember { droppedFiles() },
        onAppReady = ::dismissLoadingScreen,
    )
}

/**
 * Opens [url] in a new tab that holds no reference back to this one. A song's links come from files other people wrote,
 * and unlike a link with `target="_blank"`, `window.open` hands the opened page a live `window.opener` unless it is
 * told not to, which would let that page replace the Campfire tab with a look-alike of it. `noreferrer` also keeps the
 * app's address, which names the song file, out of the linked site's `Referer`.
 */
private fun openInNewTab(url: String) {
    window.open(url, "_blank", "noopener,noreferrer")
}

/**
 * Opens the search of the list screen that is on top on Ctrl / Cmd + F ([CampfireViewModel.openCurrentSearch]), and
 * keeps the browser's own find bar shut when it does: that bar searches the page's text, and the whole of this page is
 * one canvas with none. Everywhere else - another screen, a dialog - the key is left alone and the browser opens its
 * bar as it always does.
 *
 * It is a listener on the window in the capture phase rather than a key handler inside the composition, for two
 * reasons. Nothing on a list screen is focused until its search is, and a key event only reaches Compose along the
 * focus path. And a key pressed in the hidden `<input>` that holds the caret of a field reaches Compose only after
 * the browser has acted on it (see [startSuppressingBrowserSave]), so a Compose handler could never have stopped the
 * find bar opening over the app. The listener is a Kotlin function rather than a `js(...)` block because what it has
 * to decide is the view model's, and has to be decided before the browser moves on.
 */
@Composable
private fun SearchShortcutEffect(viewModel: CampfireViewModel) = DisposableEffect(viewModel) {
    val listener: (Event) -> Unit = { event ->
        val keyEvent = event.unsafeCast<KeyboardEvent>()
        // code, as Compose goes by it (Key.F is the physical key); key for a virtual keyboard, which has none.
        val isF = keyEvent.code == "KeyF" || keyEvent.key == "f" || keyEvent.key == "F"
        if ((keyEvent.ctrlKey || keyEvent.metaKey) && !keyEvent.altKey && isF && viewModel.openCurrentSearch()) {
            keyEvent.preventDefault()
        }
    }
    window.addEventListener(EVENT_KEY_DOWN, listener, true)
    onDispose { window.removeEventListener(EVENT_KEY_DOWN, listener, true) }
}

/**
 * Keeps the `theme-color` of the page - what Chrome on Android and the browsers built on it paint their toolbar and the
 * status bar in - on the palette the app is drawn in. index.html can only name the app's own palette, in the system's
 * light or dark half, since the preferences are in OPFS and nothing reads them before the app does.
 *
 * A light scheme hands over its `surfaceContainerHigh`, the tone of the search pill, rather than its background:
 * Chrome ignores a color whose HSL lightness is above 0.94 and paints its own default toolbar instead, and every light
 * background is well above that, while every palette's `surfaceContainerHigh` is below it. A dark scheme hands over its
 * background, which Chrome takes as it is - but only while the system is in its light theme. With the system in
 * dark, Chrome keeps its own dark toolbar whatever the page asks for, so an app in the system's own theme only gets
 * a toolbar of its colors in the light half.
 *
 * It follows the preferences rather than the colors on screen, the way the Android shell's system bars do: the theme
 * cross fades between two schemes on every frame of a change, and the browsers animate a new `theme-color` of their
 * own accord. Both of the page's tags are written, so the one whose media query matches carries it whichever it is.
 */
@Composable
private fun BrowserThemeColorEffect(viewModel: CampfireViewModel) {
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val isDarkTheme = userPreferences?.uiMode.isDarkTheme()
    val colorSchemePair = colorSchemePair(userPreferences?.themeColor)
    val toolbarColor = if (isDarkTheme) colorSchemePair.dark.background else colorSchemePair.light.surfaceContainerHigh
    // Nothing is known until the preferences are, and index.html's own tags are the better guess until then.
    if (userPreferences != null) {
        LaunchedEffect(toolbarColor) {
            setBrowserThemeColor("#" + (toolbarColor.toArgb() and 0xFFFFFF).toString(16).padStart(6, '0'))
        }
    }
}

/** Writes [color], a `#rrggbb` string, into every `theme-color` tag of the page. */
private fun setBrowserThemeColor(color: String) {
    js(
        """document.querySelectorAll('meta[name="theme-color"]').forEach(function (meta) {
            meta.setAttribute('content', color);
        })"""
    )
}

/**
 * Shows the app icon the preferences ask for (`appIconThemeColor`) as the page's favicon: one of the recolored
 * `favicon-<color>.png` files, the app's own one included, since a browser keeps a favicon by its address and an
 * icon that changed under the same name would go on showing as it was - which is also why an icon that is redrawn is
 * given a new name rather than written over the old one. The name is also left in the browser's local
 * storage, where index.html looks for it before anything else is fetched, so that the tab and the loading screen of the
 * next visit are in that color from the start rather than turning into it once the preferences have been read, sixteen
 * megabytes later.
 */
@Composable
private fun FaviconEffect(viewModel: CampfireViewModel) {
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val appIconColor = userPreferences.appIconThemeColor.appIconColor
    if (userPreferences != null) {
        LaunchedEffect(appIconColor) {
            setFavicon("favicon-${appIconColor.id}.png")
        }
    }
}

/**
 * Points the page's icon at [fileName], a file next to index.html, and remembers it for index.html. Storage that
 * cannot be written - blocked for the site, or a private window out of space - costs the next visit its first frames
 * in the app's own icon and nothing else. The icon is asked for by its versioned address, which is the one it is kept
 * in the browser under, so the icon of an app opened without a connection is there too.
 */
private fun setFavicon(fileName: String) {
    js(
        """{
            document.querySelector('link[rel="icon"]').setAttribute('href', window.campfireVersioned ? window.campfireVersioned(fileName) : fileName);
            try {
                window.localStorage.setItem('campfire-icon', fileName);
            } catch (e) {
            }
        }"""
    )
}

/**
 * Turns the browser's zoom shortcuts into the text size of the song details screen while it is on top
 * ([CampfireViewModel.isSongTextZoomable]): Ctrl / Cmd + plus, minus and zero step it the way the app bar's buttons
 * do, and a Ctrl + scroll is kept from the browser so that the screen's own gesture handler can have it (see
 * fontScaleGestures). A zoomed page is the whole app grown around a song that stayed the same size, which is not what
 * anybody reading one asked for. Everywhere else the browser zooms the page as it always does, which is how the rest of
 * the app is made larger on the web.
 *
 * Every browser the app runs in reports a pinch on a touchpad as a Ctrl + scroll too, with Ctrl set although nobody
 * holds it and a distance that is the logarithm of the pinch ([PIXELS_PER_PINCH_E]) - which, taken for notches of a
 * wheel, would move the text a few percent for a whole pinch. So a Ctrl + scroll arriving while the Ctrl key is not
 * down is taken for the pinch it is: it goes to [CampfireViewModel.magnifySongText] and is stopped before the canvas
 * hears of it. Whether the key is down is only known from its own key events, so one held since before the page had the
 * focus reads as not held, and scrolling with it resizes as fast as a pinch does.
 *
 * Window listeners in the capture phase, for the reasons [SearchShortcutEffect] gives. The wheel listener has to be
 * declared not passive, since a wheel listener on the window is passive unless it says otherwise and a passive one
 * cannot prevent anything.
 */
@Composable
private fun SongTextZoomEffect(viewModel: CampfireViewModel) = DisposableEffect(viewModel) {
    var isControlKeyDown = false
    val keyDownListener: (Event) -> Unit = listener@{ event ->
        val keyEvent = event.unsafeCast<KeyboardEvent>()
        if (keyEvent.key == KEY_CONTROL) isControlKeyDown = true
        // key rather than code, as the browser's own zoom goes by it: the plus of a Hungarian layout is Shift + 3.
        // Alt is left out because AltGr arrives as Ctrl + Alt on Windows, and AltGr with these keys types a character
        // on some layouts.
        val steps = when (keyEvent.key) {
            "+", "=" -> 1
            "-", "_" -> -1
            "0" -> null
            else -> return@listener
        }
        if ((keyEvent.ctrlKey || keyEvent.metaKey) && !keyEvent.altKey && viewModel.zoomSongText(steps)) {
            keyEvent.preventDefault()
        }
    }
    val keyUpListener: (Event) -> Unit = { event ->
        if (event.unsafeCast<KeyboardEvent>().key == KEY_CONTROL) isControlKeyDown = false
    }
    // A key released while another window has the focus is never reported to this one.
    val blurListener: (Event) -> Unit = { isControlKeyDown = false }
    val wheelListener: (Event) -> Unit = { event ->
        val wheelEvent = event.unsafeCast<WheelEvent>()
        if (wheelEvent.ctrlKey && viewModel.isSongTextZoomable) {
            event.preventDefault()
            if (!isControlKeyDown && wheelEvent.deltaMode == WheelEvent.DOM_DELTA_PIXEL) {
                event.stopPropagation()
                viewModel.magnifySongText(exp(-wheelEvent.deltaY / PIXELS_PER_PINCH_E).toFloat())
            }
        }
    }
    window.addEventListener(EVENT_KEY_DOWN, keyDownListener, true)
    window.addEventListener(EVENT_KEY_UP, keyUpListener, true)
    window.addEventListener(EVENT_BLUR, blurListener)
    window.addEventListener(EVENT_WHEEL, wheelListener, AddEventListenerOptions(passive = false, capture = true))
    onDispose {
        window.removeEventListener(EVENT_KEY_DOWN, keyDownListener, true)
        window.removeEventListener(EVENT_KEY_UP, keyUpListener, true)
        window.removeEventListener(EVENT_BLUR, blurListener)
        window.removeEventListener(EVENT_WHEEL, wheelListener, true)
    }
}

/**
 * Makes the browser ask before the page is left - reloaded, closed, or navigated away from. The browser's Back does
 * not leave it while there is an editor to leave, since the editor has a history entry of its own and going back from
 * it asks the app's own question instead (see BrowserHistoryEffect). The editor only writes when it is told to, so
 * at that moment the page holds the only copy of what was typed.
 *
 * The listener exists only while there is unsaved text ([stopWarningBeforeUnload] otherwise): a page with a
 * `beforeunload` listener is one the browser cannot keep in its back/forward cache, and one that always asks is
 * one the browser stops listening to. What the prompt says is the browser's own business - the text a page
 * supplies has not been shown by any of them for years - so there is nothing here to translate. The browser
 * also only asks once the user has interacted with the page, which typing has taken care of.
 */
private fun startWarningBeforeUnload() {
    js(
        """(function () {
            if (window.campfireUnloadWarning) return;
            window.campfireUnloadWarning = function (event) {
                event.preventDefault();
                // What asks the question in the browsers that predate preventDefault doing so.
                event.returnValue = true;
            };
            window.addEventListener('beforeunload', window.campfireUnloadWarning);
        })()"""
    )
}

/** Undoes [startWarningBeforeUnload]. */
private fun stopWarningBeforeUnload() {
    js(
        """(function () {
            if (!window.campfireUnloadWarning) return;
            window.removeEventListener('beforeunload', window.campfireUnloadWarning);
            window.campfireUnloadWarning = null;
        })()"""
    )
}

/**
 * Tells index.html that the app is on the canvas, which is what fades its loading screen out and finishes its
 * progress bar. The page is the only one of the four startup screens that is not the platform's own, and it is the
 * only one that can be held for as long as it takes without asking anything of the system.
 */
private fun dismissLoadingScreen() {
    js("window.campfireReady && window.campfireReady()")
}

/**
 * Hands Compose an Escape the browser gave to somebody else.
 *
 * Compose listens for keys on its canvas, but a text field with the caret in it is a hidden `<input>` placed beside
 * that canvas rather than inside it, so a key pressed there travels up the page without ever passing the canvas and
 * Compose never hears of it - and every dialog in the app that holds a field opens with that field focused, which is
 * most of them. Such a press is dispatched on the canvas instead, so that it takes the path it would have taken had
 * the canvas been focused: Compose turns an Escape into a back event, and the innermost back handler answers it, so
 * a dialog standing on a bottom sheet closes on its own rather than taking the sheet with it. Deciding here what an
 * Escape means - the way the desktop window's key handler does - could not tell those two apart.
 *
 * Only a press Compose has not already seen is forwarded: the canvas' own listener runs first and calls
 * `preventDefault` on everything it processes, an Escape always among them. The forwarded event does not bubble, so
 * it cannot come back here either.
 *
 * Repeats of a held Escape are stopped in the capture phase, before the canvas or the forwarder sees them: every one
 * would be another back, so holding the key a moment too long emptied the back stack and flickered the editor's
 * unsaved changes question. Being `preventDefault`ed, a stopped repeat is not forwarded either.
 */
private fun startForwardingEscapeKey() {
    js(
        """(function () {
            function findCanvas(root) {
                var canvas = root.querySelector('canvas');
                if (canvas) return canvas;
                var elements = root.querySelectorAll('*');
                for (var i = 0; i < elements.length; i++) {
                    if (elements[i].shadowRoot) {
                        canvas = findCanvas(elements[i].shadowRoot);
                        if (canvas) return canvas;
                    }
                }
                return null;
            }
            var canvas = null;
            window.campfireEscapeForwarder = function (event) {
                if (event.key !== 'Escape' || event.defaultPrevented) return;
                if (!canvas || !canvas.isConnected) canvas = findCanvas(document);
                if (!canvas) return;
                canvas.dispatchEvent(new KeyboardEvent(event.type, {
                    key: event.key,
                    code: event.code,
                    keyCode: event.keyCode,
                    bubbles: false,
                    cancelable: true
                }));
            };
            window.campfireEscapeRepeatFilter = function (event) {
                if (event.key === 'Escape' && event.repeat) {
                    event.preventDefault();
                    event.stopImmediatePropagation();
                }
            };
            window.addEventListener('keydown', window.campfireEscapeRepeatFilter, true);
            window.addEventListener('keydown', window.campfireEscapeForwarder);
            window.addEventListener('keyup', window.campfireEscapeForwarder);
        })()"""
    )
}

/**
 * Keeps Ctrl / Cmd + S from the browser, whose "Save page as" would otherwise open over the editor every time the
 * song is saved with it: the editor handles the key itself (see SongEditorScreen), but a key pressed in the hidden
 * `<input>` that holds the caret is handed to Compose only after the browser has already acted on it, so Compose
 * consuming it cannot stop the browser. Only the default is prevented, in the capture phase: the event still reaches
 * that input, and through it the editor. A page of this app saved as HTML is a copy of nothing.
 */
private fun startSuppressingBrowserSave() {
    js(
        """(function () {
            if (window.campfireSaveSuppressor) return;
            window.campfireSaveSuppressor = function (event) {
                // code, as Compose goes by it (Key.S is the physical key); key for a virtual keyboard, which has none.
                var isS = event.code === 'KeyS' || event.key === 's' || event.key === 'S';
                if ((event.ctrlKey || event.metaKey) && !event.altKey && isS) {
                    event.preventDefault();
                }
            };
            window.addEventListener('keydown', window.campfireSaveSuppressor, true);
        })()"""
    )
}

/** Undoes [startSuppressingBrowserSave]. */
private fun stopSuppressingBrowserSave() {
    js(
        """(function () {
            if (!window.campfireSaveSuppressor) return;
            window.removeEventListener('keydown', window.campfireSaveSuppressor, true);
            window.campfireSaveSuppressor = null;
        })()"""
    )
}

/** Undoes [startForwardingEscapeKey]. */
private fun stopForwardingEscapeKey() {
    js(
        """(function () {
            if (window.campfireEscapeRepeatFilter) {
                window.removeEventListener('keydown', window.campfireEscapeRepeatFilter, true);
                window.campfireEscapeRepeatFilter = null;
            }
            if (!window.campfireEscapeForwarder) return;
            window.removeEventListener('keydown', window.campfireEscapeForwarder);
            window.removeEventListener('keyup', window.campfireEscapeForwarder);
            window.campfireEscapeForwarder = null;
        })()"""
    )
}

private const val EVENT_KEY_DOWN = "keydown"
private const val EVENT_WHEEL = "wheel"
private const val EVENT_KEY_UP = "keyup"
private const val EVENT_BLUR = "blur"
private const val KEY_CONTROL = "Control"
private const val PIXELS_PER_PINCH_E = 100.0 // A pinch that moves the fingers e times farther apart scrolls by -100 px.
