/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.widget

import android.content.Context
import com.pandulapeter.campfire.presentation.ui.widget.WidgetSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * `widget/snapshot.json` in the app's files directory: everything the widgets show, written by the app
 * ([WidgetFilesImpl]) and read by the widgets alone, which never read the library. Outside the backup rules'
 * allow-list, since it names songs and setlists and the app writes it again from the restored library.
 */
internal object WidgetSnapshotFile {

    private val _snapshot = MutableStateFlow<WidgetSnapshot?>(null)
    private var isLoaded = false
    private val mutex = Mutex()

    /** What the file holds, once [load]ed in this process, kept current by [write]. */
    val snapshot: StateFlow<WidgetSnapshot?> = _snapshot.asStateFlow()

    /** Reads the file into [snapshot] unless this process already has, off the main thread. */
    suspend fun load(context: Context) = mutex.withLock {
        if (!isLoaded) {
            _snapshot.value = withContext(Dispatchers.IO) { read(context) }
            isLoaded = true
        }
    }

    /** The snapshot, or null before the app has ever written one (or where it cannot be read). */
    fun read(context: Context): WidgetSnapshot? = try {
        file(context).takeIf { it.exists() }?.readText()?.let(WidgetSnapshot::decode)
    } catch (exception: IOException) {
        println("Could not read the widgets' snapshot: ${exception.message}")
        null
    }

    /**
     * Writes [snapshot] unless the file already holds it, and says whether it did. Written beside the file and renamed
     * over it, which replaces it in one step, so that a widget drawn meanwhile reads the old snapshot or the new one,
     * never half of one.
     */
    suspend fun write(context: Context, snapshot: WidgetSnapshot): Boolean = mutex.withLock {
        val isWritten = write(context = context, json = snapshot.encode())
        WidgetCoverFiles.forgetAllBut(snapshot.songs.mapNotNullTo(mutableSetOf()) { it.cover })
        _snapshot.value = snapshot
        isLoaded = true
        isWritten
    }

    private fun write(context: Context, json: String): Boolean {
        val file = file(context)
        if (file.exists() && file.readText() == json) return false
        val directory = file.parentFile ?: throw IOException("No directory for ${file.name}")
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Could not create ${directory.name}")
        val temporary = File(directory, "$FILE_NAME.tmp")
        temporary.writeText(json)
        if (!temporary.renameTo(file)) {
            temporary.delete()
            throw IOException("Could not replace $FILE_NAME")
        }
        return true
    }

    private fun file(context: Context) = File(File(context.filesDir, WIDGET_DIRECTORY_NAME), FILE_NAME)

    private const val FILE_NAME = "snapshot.json"
}

/** The folder in the app's files directory that holds everything the widgets read. */
internal const val WIDGET_DIRECTORY_NAME = "widget"
