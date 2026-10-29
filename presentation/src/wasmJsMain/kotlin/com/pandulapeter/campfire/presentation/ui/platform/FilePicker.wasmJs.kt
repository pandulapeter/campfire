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

import com.pandulapeter.campfire.data.model.library.ExportedFile
import com.pandulapeter.campfire.data.model.importing.ImportBudget
import com.pandulapeter.campfire.data.model.importing.ImportLimits
import com.pandulapeter.campfire.data.model.importing.ImportedFile
import com.pandulapeter.campfire.data.model.library.LibraryFiles
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
 *
 * Sharing is the Web Share API where the browser has it (the phones' browsers, Safari, Chrome and Edge on Windows and
 * ChromeOS; not Firefox on a computer), each kind of file asked about separately, since every browser keeps its own list
 * of the kinds it lets out.
 */
internal object WebFilePicker : FilePicker {

    override suspend fun pickFiles(): List<ImportedFile> =
        openFileInput().await<JsArray<JsAny>?>()?.toImportedFiles().orEmpty()

    /**
     * A download rather than a dialog: the browser decides where it lands, and there is no way to hear whether the
     * user kept it, so this is optimistic by necessity.
     */
    override suspend fun saveFile(file: ExportedFile): Boolean {
        downloadFile(name = file.name, mimeType = file.mimeType, bytes = file.bytes.toInt8Array())
        return true
    }

    override val canShare = isWebShareAvailable()

    override fun canShareFile(fileName: String, mimeType: String) = canShareFiles(name = fileName, mimeType = mimeType)

    /**
     * The browser lets a share out only within a few seconds of a press, which a long PDF can outlast while it is drawn: a
     * share refused for that, or for anything but the user closing the sheet, is downloaded instead, so the file the user
     * asked for still comes out.
     */
    override suspend fun shareFile(file: ExportedFile): Boolean {
        if (!canShareFile(fileName = file.name, mimeType = file.mimeType)) return saveFile(file)
        return when (shareFileData(name = file.name, mimeType = file.mimeType, bytes = file.bytes.toInt8Array()).await<JsString>().toString()) {
            SHARED -> true
            DISMISSED -> false
            else -> saveFile(file)
        }
    }

    override suspend fun shareText(text: String, subject: String) = shareUrl(url = text, title = subject).await<JsString>().toString() == SHARED
}

private const val SHARED = "shared"
private const val DISMISSED = "dismissed"

private fun isWebShareAvailable(): Boolean = js("typeof navigator.share === 'function'")

/** Asked with an empty file, since the browser decides by the name and the type alone. */
private fun canShareFiles(name: String, mimeType: String): Boolean = js(
    """(function () {
        try {
            return typeof navigator.canShare === 'function' && navigator.canShare({ files: [new File([], name, { type: mimeType })] });
        } catch (error) {
            return false;
        }
    })()"""
)

/** Resolves with `shared`, with `dismissed` where the user closed the sheet (an `AbortError`), or with `refused`. */
private fun shareFileData(name: String, mimeType: String, bytes: Int8Array): Promise<JsString> = js(
    """navigator.share({ files: [new File([bytes], name, { type: mimeType })] }).then(
        function () { return 'shared'; },
        function (error) { return error && error.name === 'AbortError' ? 'dismissed' : 'refused'; }
    )"""
)

private fun shareUrl(url: String, title: String): Promise<JsString> = js(
    """navigator.share({ url: url, title: title }).then(
        function () { return 'shared'; },
        function (error) { return error && error.name === 'AbortError' ? 'dismissed' : 'refused'; }
    )"""
)

/**
 * Resolves with the picked files, or with an empty list when the dialog was dismissed. A dismissed dialog is the
 * input's `cancel` event where the browser fires one. Where it does not, the window regaining focus without a
 * `change` following it is taken to mean one - after a wait long enough for a large selection, which some browsers
 * deliver well after the window already has its focus back, to arrive first.
 */
private fun openFileInput(): Promise<JsArray<JsAny>?> = js(
    """(function () {
        return new Promise(function (resolve) {
            var input = document.createElement('input');
            input.type = 'file';
            input.multiple = true;
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

/** The first [length] bytes of [file], or null where the browser cannot read it, as [fileBytes] does. */
private fun fileHead(file: JsAny, length: Int): Promise<Int8Array?> = js(
    """file.slice(0, length).arrayBuffer().then(
        function (buffer) { return new Int8Array(buffer); },
        function () { return null; }
    )"""
)

private fun droppedFile(item: JsAny): JsAny = js("item.file")

private fun isDroppedInFolder(item: JsAny): Boolean = js("item.isInFolder")

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
    listenForDrops(
        maxDepth = ImportLimits.MAX_FOLDER_DEPTH,
        maxEntries = ImportLimits.MAX_FOLDER_ENTRIES,
    )
    while (true) {
        emit(nextDrop().await<JsArray<JsAny>?>()?.toImportedFiles(isDrop = true).orEmpty())
    }
}.catch { exception ->
    // The collector is a LaunchedEffect at the root of the app, and what leaves one of those takes the
    // composition with it. Losing drag and drop is the smaller loss.
    println("Could not read the dropped files: ${exception.message}")
}

/**
 * Read within one [ImportBudget], which is what keeps a file the import would not look inside out of memory. A drop's
 * items are the files paired with whether they came out of a folder (see [listenForDrops]); a pick's are the files.
 */
private suspend fun JsArray<JsAny>.toImportedFiles(isDrop: Boolean = false): List<ImportedFile> {
    val budget = ImportBudget()
    return (0 until length).mapNotNull { index ->
        get(index)?.let { item ->
            if (isDrop) droppedFile(item).toImportedFile(budget, isInFolder = isDroppedInFolder(item)) else item.toImportedFile(budget, isInFolder = false)
        }
    }
}

/**
 * One unreadable file must not lose the ones that came with it, and a drop has no caller to catch for it: whatever
 * is thrown here ends [droppedFiles] for the rest of the session. A file that cannot be read arrives empty
 * instead of being left out, because an empty file is one the import reports as skipped - so the user is told,
 * where leaving it out would say nothing at all.
 *
 * A file that came out of a dropped folder under a name the import does not know is read only where its first
 * [ImportLimits.SNIFF_LENGTH] bytes say it is worth it, as the desktop's folder walk decides: a folder of photos would
 * otherwise be read whole, one file at a time. One picked or dropped on its own is read whatever it is called.
 *
 * `Throwable` rather than `Exception`: what a `js(...)` block throws arrives as a `JsException`, which is not an
 * `Exception`, and what a rejected promise arrives as is the coroutines library's business.
 */
private suspend fun JsAny.toImportedFile(budget: ImportBudget, isInFolder: Boolean): ImportedFile? {
    val (name, size) = try {
        fileName(this).toString() to fileSize(this).toLong()
    } catch (_: Throwable) {
        return null
    }
    if (isInFolder && !LibraryFiles.isImportableFileName(name) && (LibraryFiles.isBinaryFileName(name) || !isWorthReading())) {
        return ImportedFile.unread(name)
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

private suspend fun JsAny.isWorthReading() = try {
    fileHead(this, ImportLimits.SNIFF_LENGTH).await<Int8Array?>()?.toByteArray()?.let(ImportBudget::isWorthReadingUnknown) == true
} catch (exception: CancellationException) {
    throw exception
} catch (_: Throwable) {
    false
}

/**
 * A dropped folder is read to the bottom, as the desktop's is (`filesBelow`): each folder's files in name order,
 * within [maxDepth] levels and [maxEntries] entries so that a home directory dropped by accident is not walked for
 * good. Every file is handed over as `{ file, isInFolder }`, and what is read of one from a folder is decided in
 * [toImportedFile], as an archive's entries are decided by the reader. Names starting with a dot are left out of a
 * folder, folders included, and unreported, as they are out of an archive - nobody chose those, and the `._`
 * companions macOS writes next to every file on a foreign volume carry a song's extension.
 *
 * `webkitGetAsEntry` and `getAsFile` are only answered while the `drop` handler is running, so every item is asked
 * before the handler returns and the reading happens afterwards. Nothing here rejects: an entry that cannot be
 * read resolves to nothing, and a browser without `webkitGetAsEntry` hands a folder over as a `File` that
 * [fileBytes] then reports as unreadable.
 */
private fun listenForDrops(maxDepth: Int, maxEntries: Int): Unit = js(
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
        function filesIn(directory, depth, entries) {
            return childrenOf(directory).then(function (children) {
                var groups = [];
                children
                    .sort(function (first, second) { return first.name < second.name ? -1 : first.name > second.name ? 1 : 0; })
                    .forEach(function (child) {
                        if (entries.left-- <= 0 || child.name.charAt(0) === '.') return;
                        if (child.isDirectory) {
                            if (depth < maxDepth) groups.push(filesIn(child, depth + 1, entries));
                        } else if (child.isFile) {
                            groups.push(fileOf(child).then(function (file) { return [{ file: file, isInFolder: true }]; }));
                        }
                    });
                return Promise.all(groups).then(function (found) { return [].concat.apply([], found); });
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
                        pending.push(filesIn(entry, 1, { left: maxEntries }));
                    } else {
                        var file = items[i].getAsFile();
                        if (file) pending.push(Promise.resolve([{ file: file, isInFolder: false }]));
                    }
                }
            } else if (dataTransfer.files && dataTransfer.files.length > 0) {
                pending.push(Promise.resolve(Array.prototype.slice.call(dataTransfer.files).map(function (file) {
                    return { file: file, isInFolder: false };
                })));
            }
            if (pending.length === 0) return null;
            return Promise.all(pending).then(function (groups) {
                var files = [];
                groups.forEach(function (group) {
                    group.forEach(function (item) { if (item.file) files.push(item); });
                });
                return files;
            }, function () { return []; });
        }
        window.addEventListener('dragover', function (event) {
            stop(event);
            if (window.__campfireDropsRefused && event.dataTransfer) event.dataTransfer.dropEffect = 'none';
        });
        window.addEventListener('drop', function (event) {
            stop(event);
            if (window.__campfireDropsRefused) return;
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

/**
 * Makes the page refuse every drop, files and text alike, while [isRefused] (read only mode). The listeners keep
 * preventing the browser's default, which would navigate away to a dropped file, and tell it the drop is not taken
 * instead, so the pointer says so before anything is let go.
 */
internal fun refuseDrops(isRefused: Boolean): Unit = js("window.__campfireDropsRefused = isRefused")

private fun nextDrop(): Promise<JsArray<JsAny>?> = js(
    """(function () {
        if (window.__campfireDrops.length > 0) return Promise.resolve(window.__campfireDrops.shift());
        return new Promise(function (resolve) { window.__campfireDropResolve = resolve; });
    })()"""
)
