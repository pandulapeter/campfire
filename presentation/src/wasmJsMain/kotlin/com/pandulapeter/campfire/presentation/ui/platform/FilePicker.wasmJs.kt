@file:OptIn(ExperimentalWasmJsInterop::class)

package com.pandulapeter.campfire.presentation.ui.platform

import com.pandulapeter.campfire.data.model.domain.ExportedFile
import com.pandulapeter.campfire.data.model.domain.ImportedFile
import kotlinx.coroutines.await
import org.khronos.webgl.Int8Array
import org.khronos.webgl.toByteArray
import org.khronos.webgl.toInt8Array
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.JsArray
import kotlin.js.JsString
import kotlin.js.Promise

/**
 * The browser has no file dialog of its own to call: picking is a hidden `<input type="file">` that is clicked, and
 * saving is a `Blob` behind an `<a download>`. Both live in a `js(...)` block for the same reason the OPFS storage
 * does - crossing the Kotlin/Wasm boundary once per step is far cheaper than once per element.
 */
internal object WebFilePicker : FilePicker {

    override suspend fun pickFiles(): List<ImportedFile> {
        val files = pickFiles(ACCEPTED_TYPES).await<JsArray<JsAny>?>() ?: return emptyList()
        return (0 until files.length).mapNotNull { index ->
            files[index]?.let { file ->
                ImportedFile(
                    name = fileName(file).toString(),
                    bytes = fileBytes(file).await<Int8Array?>()?.toByteArray() ?: ByteArray(0)
                )
            }
        }
    }

    /**
     * A download rather than a dialog: the browser decides where it lands, and there is no way to hear whether the
     * user kept it, so this is optimistic by necessity.
     */
    override suspend fun saveFile(file: ExportedFile): Boolean {
        downloadFile(name = file.name, mimeType = file.mimeType, bytes = file.bytes.toInt8Array())
        return true
    }

    /** Extensions rather than MIME types: `.cho` and friends have none, so a type filter would hide them all. */
    private const val ACCEPTED_TYPES = ".cho,.chordpro,.chopro,.crd,.pro,.txt,.zip,.json"
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
