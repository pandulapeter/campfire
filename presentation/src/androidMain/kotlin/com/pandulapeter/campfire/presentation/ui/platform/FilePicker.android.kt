package com.pandulapeter.campfire.presentation.ui.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
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
        return withContext(Dispatchers.IO) { uris.mapNotNull { it.toImportedFile(context) } }
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

    override val canShare = true

    /**
     * The song is copied into the cache directory first: the library lives in the app's private storage, which no
     * other app can read, and the provider declared in the manifest only exposes that one directory.
     */
    override suspend fun shareFile(file: ExportedFile): Boolean {
        val uri = withContext(Dispatchers.IO) {
            try {
                val directory = java.io.File(context.cacheDir, SHARED_DIRECTORY).apply { mkdirs() }
                val target = java.io.File(directory, file.name).apply { writeBytes(file.bytes) }
                FileProvider.getUriForFile(context, "${context.packageName}.files", target)
            } catch (exception: Exception) {
                println("Could not prepare \"${file.name}\" for sharing: ${exception.message}")
                null
            }
        } ?: return false
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = file.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    fun onFilesPicked(uris: List<Uri>) {
        pickContinuation?.takeIf { it.isActive }?.resume(uris)
        pickContinuation = null
    }

    fun onSaveLocationPicked(uri: Uri?) {
        saveContinuation?.takeIf { it.isActive }?.resume(uri)
        saveContinuation = null
    }

    private companion object {
        const val ANY_MIME_TYPE = "*/*"
        const val SHARED_DIRECTORY = "shared"
    }
}

/**
 * Reads a document the system handed over - picked, opened with Campfire, shared to it or dropped onto it. Null when
 * it cannot be read, so that one bad file does not lose the ones next to it.
 */
fun Uri.toImportedFile(context: Context): ImportedFile? = try {
    context.contentResolver.openInputStream(this)?.use { ImportedFile(name = displayName(context), bytes = it.readBytes()) }
} catch (exception: Exception) {
    println("Could not read \"$this\": ${exception.message}")
    null
}

/** The extension is what the import goes by, and for a content URI only the display name carries it. */
private fun Uri.displayName(context: Context): String = context.contentResolver
    .query(this, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
    ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    ?: lastPathSegment.orEmpty().substringAfterLast('/')
