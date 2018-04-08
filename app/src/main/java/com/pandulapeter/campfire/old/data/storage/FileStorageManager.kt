package com.pandulapeter.campfire.old.data.storage

import android.content.Context
import java.io.File

/**
 * Wrapper for storing files in the internal storage.
 */
class FileStorageManager(private val context: Context) {

    fun loadDownloadedSongText(id: String) = getFile(id).readText()

    private fun getFile(id: String) = File(context.filesDir, "song_$id")
}