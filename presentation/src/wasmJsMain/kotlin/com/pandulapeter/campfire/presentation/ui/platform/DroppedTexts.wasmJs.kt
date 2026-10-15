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

package com.pandulapeter.campfire.presentation.ui.platform

import kotlinx.coroutines.await
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsString
import kotlin.js.Promise

/**
 * Text dropped on the page - a selection dragged from another tab, a link - where the drop holds no file: [droppedFiles]
 * takes those, and a drop of both is a drop of files. Handed over one at a time through a promise, as the files are; the
 * browser's default for the drop is already prevented by the listeners [droppedFiles] attaches.
 */
internal fun droppedTexts(): Flow<String> = flow {
    listenForTextDrops()
    while (true) {
        emit(nextTextDrop().await<JsString>().toString())
    }
}.catch { exception ->
    // Collected at the root of the app, like droppedFiles, which this would otherwise take down with it.
    println("Could not read the dropped text: ${exception.message}")
}

private fun listenForTextDrops(): Unit = js(
    """(function () {
        if (window.__campfireTextDropsReady) return;
        window.__campfireTextDropsReady = true;
        window.__campfireTextDrops = [];
        window.__campfireTextDropResolve = null;
        window.addEventListener('drop', function (event) {
            var transfer = event.dataTransfer;
            if (!transfer || window.__campfireDropsRefused) return;
            var items = transfer.items;
            if (items) {
                for (var i = 0; i < items.length; i++) if (items[i].kind === 'file') return;
            }
            if (transfer.files && transfer.files.length > 0) return;
            var text = transfer.getData('text/plain');
            if (!text) return;
            if (window.__campfireTextDropResolve) {
                var resolve = window.__campfireTextDropResolve;
                window.__campfireTextDropResolve = null;
                resolve(text);
            } else {
                window.__campfireTextDrops.push(text);
            }
        });
    })()"""
)

private fun nextTextDrop(): Promise<JsString> = js(
    """(function () {
        if (window.__campfireTextDrops.length > 0) return Promise.resolve(window.__campfireTextDrops.shift());
        return new Promise(function (resolve) { window.__campfireTextDropResolve = resolve; });
    })()"""
)
