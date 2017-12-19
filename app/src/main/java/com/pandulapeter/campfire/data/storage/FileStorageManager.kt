package com.pandulapeter.campfire.data.storage

import android.content.Context
import java.io.File

/**
 * Wrapper for storing files in the internal storage.
 */
class FileStorageManager(private val context: Context) {

    fun saveDownloadedSongText(id: String, text: String) = getFile(id).writeText(text)

    fun loadDownloadedSongText(id: String) = getFile(id).readText()

    fun deleteDownloadedSongText(id: String) = getFile(id).delete() //TODO: Move to background thread.

    private fun getFile(id: String) = File(context.filesDir, "song_$id")
}