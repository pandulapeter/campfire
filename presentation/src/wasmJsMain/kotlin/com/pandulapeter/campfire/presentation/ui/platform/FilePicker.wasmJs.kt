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
import com.pandulapeter.campfire.data.model.domain.ImportBudget
import com.pandulapeter.campfire.data.model.domain.ImportedFile
import com.pandulapeter.campfire.data.model.domain.LibraryFiles
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.await
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
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
 * Resolves with the picked files, or with an empty list when the dialog was dismissed. A dismissed dialog is the
 * input's `cancel` event where the browser fires one. Where it does not, the window regaining focus without a
 * `change` following it is taken to mean one - after a wait long enough for a large selection, which some browsers
 * deliver well after the window already has its focus back, to arrive first.
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
                setTimeout(function () { finish([]); }, 1500);
            }
            input.addEventListener('change', function () { finish(Array.prototype.slice.call(input.files)); });
            input.addEventListener('cancel', function () { finish([]); });
            window.addEventListener('focus', onFocus);
            input.click();
        });
    })()"""
)

private fun fileName(file: JsAny): JsString = js("file.name")

private fun fileSize(file: JsAny): Double = js("file.size")

/**
 * Resolves with null instead of rejecting for a file the browser cannot read: a dropped directory where the
 * browser hands one over as a `File`, or a file that was moved or deleted after it was chosen.
 */
private fun fileBytes(file: JsAny): Promise<Int8Array?> = js(
    """file.arrayBuffer().then(
        function (buffer) { return new Int8Array(buffer); },
        function () { return null; }
    )"""
)

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
}.catch { exception ->
    // The collector is a LaunchedEffect at the root of the app, and what leaves one of those takes the
    // composition with it. Losing drag and drop is the smaller loss.
    println("Could not read the dropped files: ${exception.message}")
}

/** Read within one [ImportBudget], which is what keeps a file the import would not look inside out of memory. */
private suspend fun JsArray<JsAny>.toImportedFiles(): List<ImportedFile> {
    val budget = ImportBudget()
    return (0 until length).mapNotNull { index -> get(index)?.toImportedFile(budget) }
}

/**
 * One unreadable file must not lose the ones that came with it, and a drop has no caller to catch for it: whatever
 * is thrown here ends [droppedFiles] for the rest of the session. A file that cannot be read arrives empty
 * instead of being left out, because an empty file is one the import reports as skipped - so the user is told,
 * where leaving it out would say nothing at all.
 *
 * `Throwable` rather than `Exception`: what a `js(...)` block throws arrives as a `JsException`, which is not an
 * `Exception`, and what a rejected promise arrives as is the coroutines library's business.
 */
private suspend fun JsAny.toImportedFile(budget: ImportBudget): ImportedFile? {
    val (name, size) = try {
        fileName(this).toString() to fileSize(this).toLong()
    } catch (_: Throwable) {
        return null
    }
    return budget.read(name = name, size = size) {
        try {
            fileBytes(this).await<Int8Array?>()?.toByteArray()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Throwable) {
            println("Could not read \"$name\": ${exception.message}")
            null
        } ?: ByteArray(0)
    }
}

/**
 * A dropped folder is opened one level deep: its own files are taken, in name order, and the folders inside it
 * are not. That is what "my folder of songs" is, and it keeps a home directory dropped by accident from being read
 * into memory. Names starting with a dot are left out of a folder, as they are out of an archive - nobody chose
 * those, and the `._` companions macOS writes next to every file on a foreign volume carry a song's extension.
 *
 * `webkitGetAsEntry` and `getAsFile` are only answered while the `drop` handler is running, so every item is asked
 * before the handler returns and the reading happens afterwards. Nothing here rejects: an entry that cannot be
 * read resolves to nothing, and a browser without `webkitGetAsEntry` hands a folder over as a `File` that
 * [fileBytes] then reports as unreadable.
 */
private fun listenForDrops(): Unit = js(
    """(function () {
        if (window.__campfireDropsReady) return;
        window.__campfireDropsReady = true;
        window.__campfireDrops = [];
        window.__campfireDropResolve = null;
        function stop(event) { event.preventDefault(); event.stopPropagation(); }
        function fileOf(entry) {
            return new Promise(function (resolve) {
                entry.file(resolve, function () { resolve(null); });
            });
        }
        function childrenOf(directory) {
            return new Promise(function (resolve) {
                var reader = directory.createReader();
                var children = [];
                function read() {
                    reader.readEntries(function (batch) {
                        if (batch.length === 0) { resolve(children); return; }
                        children = children.concat(Array.prototype.slice.call(batch));
                        read();
                    }, function () { resolve(children); });
                }
                read();
            });
        }
        function filesIn(directory) {
            return childrenOf(directory).then(function (children) {
                return Promise.all(children
                    .filter(function (child) { return child.isFile && child.name.charAt(0) !== '.'; })
                    .sort(function (first, second) { return first.name < second.name ? -1 : first.name > second.name ? 1 : 0; })
                    .map(fileOf));
            });
        }
        function collect(dataTransfer) {
            var pending = [];
            var items = dataTransfer.items;
            if (items && items.length > 0) {
                for (var i = 0; i < items.length; i++) {
                    if (items[i].kind !== 'file') continue;
                    var entry = items[i].webkitGetAsEntry ? items[i].webkitGetAsEntry() : null;
                    if (entry && entry.isDirectory) {
                        pending.push(filesIn(entry));
                    } else {
                        var file = items[i].getAsFile();
                        if (file) pending.push(Promise.resolve([file]));
                    }
                }
            } else if (dataTransfer.files && dataTransfer.files.length > 0) {
                pending.push(Promise.resolve(Array.prototype.slice.call(dataTransfer.files)));
            }
            if (pending.length === 0) return null;
            return Promise.all(pending).then(function (groups) {
                var files = [];
                groups.forEach(function (group) {
                    group.forEach(function (file) { if (file) files.push(file); });
                });
                return files;
            }, function () { return []; });
        }
        window.addEventListener('dragover', stop);
        window.addEventListener('drop', function (event) {
            stop(event);
            var files = event.dataTransfer ? collect(event.dataTransfer) : null;
            if (!files) return;
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
