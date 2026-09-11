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
import androidx.compose.runtime.remember
import com.pandulapeter.campfire.presentation.ui.platform.LocalFilePicker
import com.pandulapeter.campfire.presentation.ui.platform.WebFilePicker
import com.pandulapeter.campfire.presentation.ui.platform.droppedFiles
import kotlinx.browser.window
import kotlin.js.ExperimentalWasmJsInterop
import org.koin.compose.viewmodel.koinViewModel

/**
 * Web shell of the shared UI. Links open in a new browser tab, and files dropped on the page are imported.
 */
@Composable
fun CampfireWebApp(
    viewModel: CampfireViewModel = koinViewModel(),
) = CompositionLocalProvider(
    LocalFilePicker provides WebFilePicker
) {
    DisposableEffect(Unit) {
        startForwardingEscapeKey()
        onDispose { stopForwardingEscapeKey() }
    }
    CampfireApp(
        viewModel = viewModel,
        urlOpener = { url -> window.open(url, "_blank") },
        filesToImport = remember { droppedFiles() },
        onAppReady = ::dismissLoadingScreen,
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
            window.addEventListener('keydown', window.campfireEscapeForwarder);
            window.addEventListener('keyup', window.campfireEscapeForwarder);
        })()"""
    )
}

/** Undoes [startForwardingEscapeKey]. */
private fun stopForwardingEscapeKey() {
    js(
        """(function () {
            if (!window.campfireEscapeForwarder) return;
            window.removeEventListener('keydown', window.campfireEscapeForwarder);
            window.removeEventListener('keyup', window.campfireEscapeForwarder);
            window.campfireEscapeForwarder = null;
        })()"""
    )
}
