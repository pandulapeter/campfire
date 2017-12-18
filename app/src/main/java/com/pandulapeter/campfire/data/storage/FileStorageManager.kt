package com.pandulapeter.campfire.data.storage

import android.content.Context
import java.io.File


/**
 * Wrapper for storing files in the internal storage.
 */
class FileStorageManager(private val context: Context) {

    fun saveDownloadedSongText(id: String, text: String) {
        val file = File(context.filesDir, KEY_SONG + id)
        file.writeText(text)
    }

    fun loadDownloadedSongText(id: String) = File(context.filesDir, KEY_SONG + id).readText()

    fun deleteDownloadedSongText(id: String) = File(context.filesDir, KEY_SONG + id).delete()

    companion object {
        private const val KEY_SONG = "song_"
    }
}