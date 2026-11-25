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
import com.pandulapeter.campfire.presentation.ui.widget.WidgetFiles
import com.pandulapeter.campfire.presentation.ui.widget.WidgetSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * The widgets' files in the app's files directory: the snapshot ([WidgetSnapshotFile]), which redraws both widgets once
 * written, and the cover thumbnails beside it ([WidgetCoverFiles]). A snapshot's write is the process's rather than the
 * caller's: a write the activity's end cancelled after the file was replaced would leave the widgets drawn from the old
 * one, and the next publish, finding the file unchanged, would not redraw them either.
 */
internal class WidgetFilesImpl(private val context: Context) : WidgetFiles {

    override suspend fun writeSnapshot(snapshot: WidgetSnapshot) = scope.launch {
        mutex.withLock {
            val isWritten = try {
                WidgetSnapshotFile.write(context = context, snapshot = snapshot)
            } catch (exception: IOException) {
                println("Could not write the widgets' snapshot: ${exception.message}")
                return@withLock
            }
            if (isWritten) {
                println("Widget snapshot written: ${snapshot.songs.size} songs, ${snapshot.setlists.size} setlists")
                RandomPicksWidget().refreshAll(context)
                SetlistWidget().refreshAll(context)
            }
        }
    }.join()

    override suspend fun coverNames() = withContext(Dispatchers.IO) { WidgetCoverFiles.names(context) }

    override suspend fun writeCover(name: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        try {
            WidgetCoverFiles.write(context = context, name = name, bytes = bytes)
            true
        } catch (exception: IOException) {
            println("Could not write a widget cover: ${exception.message}")
            false
        }
    }

    override suspend fun deleteCover(name: String) = withContext(Dispatchers.IO) { WidgetCoverFiles.delete(context = context, name = name) }

    private companion object {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val mutex = Mutex()
    }
}
