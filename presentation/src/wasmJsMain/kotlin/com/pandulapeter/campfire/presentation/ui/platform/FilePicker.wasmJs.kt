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

import com.pandulapeter.campfire.data.model.domain.ExportedFile
import com.pandulapeter.campfire.data.model.domain.ImportedFile
import com.pandulapeter.campfire.data.model.domain.LibraryFiles
import kotlinx.coroutines.await
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.khronos.webgl.Int8Array
import org.khronos.webgl.toByteArray
import org.khronos.webgl.toInt8Array
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.JsArray
import kotlin.js.JsString
import kotlin.js.get
import kotlin.js.Promise

/**
 * The browser has no file dialog of its own to call: picking is a hidden `<input type="file">` that is clicked, and
 * saving is a `Blob` behind an `<a download>`. Both live in a `js(...)` block for the same reason the OPFS storage
 * does - crossing the Kotlin/Wasm boundary once per step is far cheaper than once per element.
 */
internal object WebFilePicker : FilePicker {

    override suspend fun pickFiles(): List<ImportedFile> =
        pickFiles(ACCEPTED_TYPES).await<JsArray<JsAny>?>()?.toImportedFiles().orEmpty()

    /**
     * A download rather than a dialog: the browser decides where it lands, and there is no way to hear whether the
     * user kept it, so this is optimistic by necessity.
     */
    override suspend fun saveFile(file: ExportedFile): Boolean {
        downloadFile(name = file.name, mimeType = file.mimeType, bytes = file.bytes.toInt8Array())
        return true
    }

    /** Extensions rather than MIME types: `.cho` and friends have none, so a type filter would hide them all. */
    private val ACCEPTED_TYPES = LibraryFiles.IMPORTABLE_EXTENSIONS.joinToString(separator = ",")
}

/**
 * Resolves with the picked files, or with an empty list when the dialog was dismissed. No browser fires an event for
 * a cancelled file dialog, so the window regaining focus without a `change` following it is taken to mean one.
 */
private fun pickFiles(accept: String): Promise<JsArray<JsAny>?> = js(
    """(function () {
        return new Promise(function (resolve) {
            var input = document.createElement('input');
            input.type = 'file';
            input.multiple = true;
            input.accept = accept;
            input.style.display = 'none';
            document.body.appendChild(input);
            var isDone = false;
            function finish(files) {
                if (isDone) return;
                isDone = true;
                window.removeEventListener('focus', onFocus);
                input.remove();
                resolve(files);
            }
            function onFocus() {
                setTimeout(function () { finish([]); }, 500);
            }
            input.addEventListener('change', function () { finish(Array.prototype.slice.call(input.files)); });
            window.addEventListener('focus', onFocus);
            input.click();
        });
    })()"""
)

private fun fileName(file: JsAny): JsString = js("file.name")

private fun fileBytes(file: JsAny): Promise<Int8Array?> =
    js("file.arrayBuffer().then(function (buffer) { return new Int8Array(buffer); })")

private fun downloadFile(name: String, mimeType: String, bytes: Int8Array): Unit = js(
    """(function () {
        var url = URL.createObjectURL(new Blob([bytes], { type: mimeType }));
        var link = document.createElement('a');
        link.href = url;
        link.download = name;
        document.body.appendChild(link);
        link.click();
        link.remove();
        setTimeout(function () { URL.revokeObjectURL(url); }, 10000);
    })()"""
)

/**
 * Files dropped anywhere on the page. The browser navigates away to a dropped file unless both events have their
 * default prevented, so the listeners are attached for the lifetime of the page the first time this is collected.
 *
 * Drops are handed over one list at a time through a promise rather than a callback: a Kotlin lambda cannot cross
 * into a `js` block, and a promise is what the rest of this file already speaks.
 */
internal fun droppedFiles(): Flow<List<ImportedFile>> = flow {
    listenForDrops()
    while (true) {
        emit(nextDrop().await<JsArray<JsAny>?>()?.toImportedFiles().orEmpty())
    }
}

private suspend fun JsArray<JsAny>.toImportedFiles() = (0 until length).mapNotNull { index ->
    get(index)?.let { file ->
        ImportedFile(
            name = fileName(file).toString(),
            bytes = fileBytes(file).await<Int8Array?>()?.toByteArray() ?: ByteArray(0),
        )
    }
}

private fun listenForDrops(): Unit = js(
    """(function () {
        if (window.__campfireDropsReady) return;
        window.__campfireDropsReady = true;
        window.__campfireDrops = [];
        window.__campfireDropResolve = null;
        function stop(event) { event.preventDefault(); event.stopPropagation(); }
        window.addEventListener('dragover', stop);
        window.addEventListener('drop', function (event) {
            stop(event);
            var files = Array.prototype.slice.call(event.dataTransfer.files);
            if (window.__campfireDropResolve) {
                var resolve = window.__campfireDropResolve;
                window.__campfireDropResolve = null;
                resolve(files);
            } else {
                window.__campfireDrops.push(files);
            }
        });
    })()"""
)

private fun nextDrop(): Promise<JsArray<JsAny>?> = js(
    """(function () {
        if (window.__campfireDrops.length > 0) return Promise.resolve(window.__campfireDrops.shift());
        return new Promise(function (resolve) { window.__campfireDropResolve = resolve; });
    })()"""
)
