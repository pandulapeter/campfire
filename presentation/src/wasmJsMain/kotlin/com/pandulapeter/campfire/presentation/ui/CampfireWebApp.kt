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
import androidx.compose.runtime.remember
import com.pandulapeter.campfire.metronome.api.model.MetronomePlayback
import com.pandulapeter.campfire.presentation.ui.navigation.BrowserHistoryEffect
import com.pandulapeter.campfire.presentation.ui.navigation.navigateToBrowserAddress
import com.pandulapeter.campfire.presentation.ui.platform.LocalFilePicker
import com.pandulapeter.campfire.presentation.ui.platform.LocalMetronomeNotifier
import com.pandulapeter.campfire.presentation.ui.platform.ProvideKeyboardInsets
import com.pandulapeter.campfire.presentation.ui.platform.WebFilePicker
import com.pandulapeter.campfire.presentation.ui.platform.WebMetronomeNotifier
import com.pandulapeter.campfire.presentation.ui.platform.droppedFiles
import kotlinx.browser.window
import org.w3c.dom.events.KeyboardEvent
import kotlin.js.ExperimentalWasmJsInterop
import org.koin.compose.viewmodel.koinViewModel

/**
 * Web shell of the shared UI. Links open in a new browser tab, files dropped on the page are imported, and the
 * browser asks before the page is left with unsaved text in the editor. Escape reaches Compose from a focused text
 * field too ([startForwardingEscapeKey]), Ctrl / Cmd + S is kept from the browser's own "Save page as"
 * ([startSuppressingBrowserSave]), and Ctrl / Cmd + F opens the search of a list screen in place of the browser's
 * find bar ([SearchShortcutEffect]). The browser's zoom shortcuts change the text size of the song details screen
 * instead of zooming the page ([SongTextZoomEffect]). Every screen has an address of its own, and the browser's
 * history follows the app's back stack ([BrowserHistoryEffect]). The browser's own toolbar is painted in the palette
 * the user chose ([BrowserThemeColorEffect]), and the favicon is the icon of that palette ([FaviconEffect]). The
 * on-screen keyboard is laid over the page and reported as an inset where the browser can say where it is
 * ([ProvideKeyboardInsets]), rather than shortening the page under the app.
 */
@Composable
fun CampfireWebApp(
    viewModel: CampfireViewModel = koinViewModel(),
) = CompositionLocalProvider(
    LocalFilePicker provides WebFilePicker,
    LocalMetronomeNotifier provides WebMetronomeNotifier,
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
    MetronomeShortcutEffect(viewModel)
    MetronomeStartableEffect(viewModel)
    LaunchedEffect(viewModel) { WebMetronomeNotifier.forEachStopRequest(viewModel::stopMetronome) }
    // The composition stops collecting while the tab is hidden (Compose moves the lifecycle to CREATED), and a hidden tab
    // is when the browser's media controls are used: the session follows the engine itself, as Android's service does.
    LaunchedEffect(viewModel) {
        viewModel.metronomePlayback.collect { if (it !is MetronomePlayback.Playing) WebMetronomeNotifier.onMetronomeNotificationChanged(null) }
    }
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
    ProvideKeyboardInsets {
        CampfireApp(
            viewModel = viewModel,
            urlOpener = ::openInNewTab,
            filesToImport = remember { droppedFiles() },
            onAppReady = ::dismissLoadingScreen,
        )
    }
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
 * Keeps Ctrl / Cmd + S from the browser, whose "Save page as" would otherwise open over the app every time something
 * is saved with it: the editor, the sheets and the export screen handle the key themselves (`saveShortcut`), but a key
 * pressed in the hidden `<input>` that holds the caret is handed to Compose only after the browser has already acted on
 * it, so Compose consuming it cannot stop the browser. Only the default is prevented, in the capture phase: the event
 * still reaches that input, and through it Compose. A page of this app saved as HTML is a copy of nothing.
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
