/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import com.pandulapeter.campfire.data.model.domain.ExportedFile
import com.pandulapeter.campfire.data.model.domain.ImportBudget
import com.pandulapeter.campfire.data.model.domain.ImportedFile
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.coroutines.resume

/**
 * The storage access framework, bridged into suspend functions. The launchers can only be registered from a
 * composition, so they are attached here, for as long as the composition holds them, to the one [AndroidFilePicker]
 * there is, which is then handed to the shared UI through [LocalFilePicker].
 */
@Composable
internal fun rememberAndroidFilePicker(): AndroidFilePicker {
    val picker = koinInject<AndroidFilePicker>()
    // "* / *" rather than a list of types: .cho has no registered MIME type, and anything narrower would grey the
    // songs out in the system picker. What is not a song is skipped by the import and reported afterwards.
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments(), picker::onFilesPicked)
    // The contract bakes the type in, so there is one launcher per type the app can hand out. A song leaves as the .cho
    // file it is, and .cho maps to no MIME type, so a storage provider asked for text/plain appends .txt to the name (it
    // keeps a name's extension only where the extension maps to the type asked for). The generic binary type is the
    // one it leaves every name alone for. Sharing still says text/plain, which is what decides the apps a share is
    // offered to.
    val createText = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(SONG_EXPORT_MIME_TYPE),
        picker::onSaveLocationPicked,
    )
    val createArchive = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ExportedFile.ZIP_MIME_TYPE),
        picker::onSaveLocationPicked,
    )
    // Declared after the launchers, so that it is disposed before they unregister: a launcher is never left in the
    // singleton once it can no longer launch, and a finished Activity is not kept reachable through it.
    DisposableEffect(picker, open, createText, createArchive) {
        val launchers = AndroidFilePicker.Launchers(
            open = open,
            createText = createText,
            createArchive = createArchive,
        )
        picker.launchers.value = launchers
        // Only its own: the next Activity's composition may already have put its launchers here.
        onDispose { picker.launchers.compareAndSet(launchers, null) }
    }
    return picker
}

/**
 * A singleton rather than something remembered by the composition, because the coroutine waiting for the system
 * picker is suspended on this object, and the Activity that opened the picker does not have to be there when it
 * answers: a rotation, or the system reclaiming the Activity in the background, recreates it while the picker is up.
 * The result is still delivered - the launchers are registered under a saved key - but to the callbacks of whatever
 * picker the new composition holds, so that has to be the same object the waiting coroutine is suspended on. The
 * launchers themselves are the old Activity's and dead with it, which is why they are attached only for as long as a
 * composition holds them, and taken at the moment of launching rather than when a pick or an export began.
 *
 * A process that dies under the system picker takes this object with it, and the result then arrives at one that
 * nobody is suspended on. That is what an orphaned result is, and neither kind is dropped: picked files are read
 * and offered through [orphanedFiles], and the location of an export is filled from the copy [saveFile] left in
 * the cache directory before it opened the picker, the outcome going to [orphanedExportResults].
 */
@Single
internal class AndroidFilePicker(@Provided private val context: Context) : FilePicker {

    /** The launchers of the composition that is on screen; null between one Activity and the next. */
    class Launchers(
        val open: ActivityResultLauncher<Array<String>>,
        val createText: ActivityResultLauncher<String>,
        val createArchive: ActivityResultLauncher<String>,
    )

    val launchers = MutableStateFlow<Launchers?>(null)

    private var pickContinuation: CancellableContinuation<List<Uri>>? = null
    private var saveContinuation: CancellableContinuation<Uri?>? = null

    /**
     * For the results nobody is waiting for. Its own scope rather than the composition's: the read and the write
     * must not end with an Activity that happens to be recreated while they run.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _orphanedFiles = Channel<List<ImportedFile>>(Channel.BUFFERED)
    private val _orphanedExportResults = Channel<Boolean>(Channel.BUFFERED)

    /** Files picked for a process that did not live to read them, to be imported like any the system hands over. */
    val orphanedFiles = _orphanedFiles.receiveAsFlow()

    /** Whether an export whose process died under the "save as" screen was written after all. */
    val orphanedExportResults = _orphanedExportResults.receiveAsFlow()

    private val pendingExport get() = java.io.File(java.io.File(context.cacheDir, PENDING_EXPORT_DIRECTORY), PENDING_EXPORT_NAME)

    override suspend fun pickFiles(): List<ImportedFile> {
        // The view model runs one pick at a time, so a continuation still waiting here is one whose answer is never
        // coming; it is let go as a last resort rather than left hanging.
        pickContinuation?.takeIf { it.isActive }?.resume(emptyList())
        // Between two Activities there is no launcher until the next one has composed, so it is waited for.
        val launcher = launchers.filterNotNull().first().open
        val uris = suspendCancellableCoroutine { continuation ->
            pickContinuation = continuation
            continuation.invokeOnCancellation { pickContinuation = null }
            launcher.launch(arrayOf(ANY_MIME_TYPE))
        }
        return withContext(Dispatchers.IO) { uris.toImportedFiles(context) }
    }

    override suspend fun saveFile(file: ExportedFile): Boolean {
        withContext(Dispatchers.IO) { keepPendingExport(file) }
        // Taken now rather than when the call began: an Activity recreated in between has unregistered the old ones,
        // and between two Activities there are none until the next one has composed.
        val launcher = launchers.filterNotNull().first().let { if (file.mimeType == ExportedFile.ZIP_MIME_TYPE) it.createArchive else it.createText }
        saveContinuation?.takeIf { it.isActive }?.resume(null)
        val uri = suspendCancellableCoroutine<Uri?> { continuation ->
            saveContinuation = continuation
            continuation.invokeOnCancellation { saveContinuation = null }
            launcher.launch(file.name)
        }
        // The document exists from the moment the dialog is confirmed, so from here on the write is owed: a scope
        // cancelled at this point would leave it empty.
        return withContext(NonCancellable + Dispatchers.IO) {
            clearPendingExport()
            when {
                uri == null -> false
                write(uri) { it.write(file.bytes) } -> true
                else -> throw IOException("Could not write \"${file.name}\".")
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
            val directory = java.io.File(context.cacheDir, SHARED_DIRECTORY).apply { mkdirs() }
            val target = java.io.File(directory, file.name).apply { writeBytes(file.bytes) }
            FileProvider.getUriForFile(context, "${context.packageName}.files", target)
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = file.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    fun onFilesPicked(uris: List<Uri>) {
        val continuation = pickContinuation?.takeIf { it.isActive }
        pickContinuation = null
        when {
            continuation != null -> continuation.resume(uris)
            uris.isNotEmpty() -> scope.launch { _orphanedFiles.send(uris.toImportedFiles(context)) }
        }
    }

    fun onSaveLocationPicked(uri: Uri?) {
        val continuation = saveContinuation?.takeIf { it.isActive }
        saveContinuation = null
        when {
            continuation != null -> continuation.resume(uri)
            uri == null -> scope.launch { clearPendingExport() }
            else -> scope.launch {
                // A copy that is no longer there fails the write like anything else, which removes the document.
                val isWritten = write(uri) { output -> pendingExport.inputStream().use { it.copyTo(output) } }
                clearPendingExport()
                _orphanedExportResults.send(isWritten)
            }
        }
    }

    /**
     * Fills the document the system picker created, and removes it again where that fails: it is created empty the
     * moment the dialog is confirmed, and an empty "campfire_library.zip" in Downloads reads as a backup.
     */
    private fun write(uri: Uri, content: (OutputStream) -> Unit): Boolean {
        val isWritten = try {
            context.contentResolver.openOutputStream(uri)?.use(content) != null
        } catch (exception: Exception) {
            println("Could not write \"$uri\": ${exception.message}")
            false
        }
        if (!isWritten) {
            try {
                DocumentsContract.deleteDocument(context.contentResolver, uri)
            } catch (exception: Exception) {
                // Not every provider lets a document be deleted. The message is all that is left to do then.
                println("Could not remove \"$uri\": ${exception.message}")
            }
        }
        return isWritten
    }

    /** A failure here costs only the safety net, never the export it is for. */
    private fun keepPendingExport(file: ExportedFile) {
        try {
            clearPendingExport()
            pendingExport.apply { parentFile?.mkdirs() }.writeBytes(file.bytes)
        } catch (exception: Exception) {
            println("Could not keep a copy of \"${file.name}\": ${exception.message}")
        }
    }

    private fun clearPendingExport() {
        pendingExport.parentFile?.deleteRecursively()
    }

    private companion object {
        const val ANY_MIME_TYPE = "*/*"
        const val SHARED_DIRECTORY = "shared"
        const val PENDING_EXPORT_DIRECTORY = "pending_export"
        const val PENDING_EXPORT_NAME = "export"
    }
}

/**
 * Reads the documents the system handed over - picked, opened with Campfire, shared to it or dropped onto it - within
 * one [ImportBudget]. One that cannot be read is handed over empty, which the import reports as skipped: the user asked
 * for it, and leaving it out would answer them with nothing at all - while one bad file still does not lose the ones
 * next to it.
 */
fun List<Uri>.toImportedFiles(context: Context): List<ImportedFile> {
    val budget = ImportBudget()
    return map { uri ->
        // The query that knows the display name can be what fails, so it is asked on its own.
        val (name, size) = try {
            uri.nameAndSize(context)
        } catch (exception: Exception) {
            uri.fallbackName to null
        }
        try {
            budget.read(name = name, size = size) { limit ->
                // The size is the provider's own claim and is missing as often as not, so the read stops by itself.
                context.contentResolver.openInputStream(uri)?.use { it.readAtMost(limit.toInt() + 1) }
            }
        } catch (exception: Exception) {
            println("Could not read \"$uri\": ${exception.message}")
            null
        } ?: ImportedFile.unread(name)
    }
}

/** The extension is what the import goes by, and for a content URI only the display name carries it. */
private fun Uri.nameAndSize(context: Context): Pair<String, Long?> = context.contentResolver
    .query(this, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
    ?.use { cursor ->
        if (cursor.moveToFirst()) (cursor.getString(0) ?: fallbackName) to (if (cursor.isNull(1)) null else cursor.getLong(1)) else null
    }
    ?: (fallbackName to null)

private const val SONG_EXPORT_MIME_TYPE = "application/octet-stream"

private val Uri.fallbackName get() = lastPathSegment.orEmpty().substringAfterLast('/')

/** To the end of the stream or to [limit] bytes, whichever comes first. `readNBytes` would do, from API 33 on. */
private fun InputStream.readAtMost(limit: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (output.size() < limit) {
        val count = read(buffer, 0, minOf(buffer.size, limit - output.size()))
        if (count < 0) break
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
