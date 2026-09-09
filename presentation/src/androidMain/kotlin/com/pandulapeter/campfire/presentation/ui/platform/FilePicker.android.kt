package com.pandulapeter.campfire.presentation.ui.platform

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.pandulapeter.campfire.data.model.domain.ExportedFile
import com.pandulapeter.campfire.data.model.domain.ImportedFile
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * The storage access framework, bridged into suspend functions. The launchers can only be registered from a
 * composition, so the picker is built here and handed to the shared UI through [LocalFilePicker].
 */
@Composable
internal fun rememberAndroidFilePicker(): FilePicker {
    val context = LocalContext.current.applicationContext
    val picker = remember(context) { AndroidFilePicker(context) }
    // "* / *" rather than a list of types: .cho has no registered MIME type, and anything narrower would grey the
    // songs out in the system picker. What is not a song is skipped by the import and reported afterwards.
    picker.openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments(), picker::onFilesPicked)
    // The contract bakes the type in, so there is one launcher per type the app can hand out.
    picker.createTextLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ExportedFile.TEXT_MIME_TYPE),
        picker::onSaveLocationPicked
    )
    picker.createArchiveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ExportedFile.ZIP_MIME_TYPE),
        picker::onSaveLocationPicked
    )
    return picker
}

private class AndroidFilePicker(private val context: Context) : FilePicker {

    var openLauncher: ActivityResultLauncher<Array<String>>? = null
    var createTextLauncher: ActivityResultLauncher<String>? = null
    var createArchiveLauncher: ActivityResultLauncher<String>? = null

    private var pickContinuation: CancellableContinuation<List<Uri>>? = null
    private var saveContinuation: CancellableContinuation<Uri?>? = null

    override suspend fun pickFiles(): List<ImportedFile> {
        val uris = suspendCancellableCoroutine { continuation ->
            pickContinuation = continuation
            continuation.invokeOnCancellation { pickContinuation = null }
            openLauncher?.launch(arrayOf(ANY_MIME_TYPE)) ?: continuation.resume(emptyList())
        }
        return withContext(Dispatchers.IO) { uris.mapNotNull { it.read() } }
    }

    override suspend fun saveFile(file: ExportedFile): Boolean {
        val launcher = if (file.mimeType == ExportedFile.ZIP_MIME_TYPE) createArchiveLauncher else createTextLauncher
        val uri = suspendCancellableCoroutine<Uri?> { continuation ->
            saveContinuation = continuation
            continuation.invokeOnCancellation { saveContinuation = null }
            launcher?.launch(file.name) ?: continuation.resume(null)
        } ?: return false
        return withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { it.write(file.bytes) } != null
            } catch (exception: Exception) {
                println("Could not write \"${file.name}\": ${exception.message}")
                false
            }
        }
    }

    fun onFilesPicked(uris: List<Uri>) {
        pickContinuation?.takeIf { it.isActive }?.resume(uris)
        pickContinuation = null
    }

    fun onSaveLocationPicked(uri: Uri?) {
        saveContinuation?.takeIf { it.isActive }?.resume(uri)
        saveContinuation = null
    }

    /** Null when the document cannot be read, so that one bad pick does not lose the files next to it. */
    private fun Uri.read(): ImportedFile? = try {
        context.contentResolver.openInputStream(this)?.use { ImportedFile(name = displayName(), bytes = it.readBytes()) }
    } catch (exception: Exception) {
        println("Could not read \"$this\": ${exception.message}")
        null
    }

    /** The extension is what the import goes by, and only the display name carries it for a content URI. */
    private fun Uri.displayName(): String = context.contentResolver
        .query(this, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        ?: lastPathSegment.orEmpty().substringAfterLast('/')

    private companion object {
        const val ANY_MIME_TYPE = "*/*"
    }
}
