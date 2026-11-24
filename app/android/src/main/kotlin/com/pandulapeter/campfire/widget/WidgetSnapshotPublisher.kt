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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

/**
 * Writes the snapshot the composition hands over ([WidgetSnapshotFile]) and redraws both widgets from it. The work is
 * the process's rather than the activity's: a write the activity's end cancelled after the file was replaced would leave
 * the widgets drawn from the old one, and the next publish, finding the file unchanged, would not redraw them either.
 */
internal object WidgetSnapshotPublisher {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    fun publish(context: Context, snapshot: WidgetSnapshot) {
        val applicationContext = context.applicationContext
        scope.launch {
            mutex.withLock {
                val isWritten = try {
                    WidgetSnapshotFile.write(context = applicationContext, json = snapshot.encode())
                } catch (exception: IOException) {
                    println("Could not write the widgets' snapshot: ${exception.message}")
                    return@withLock
                }
                if (isWritten) {
                    println("Widget snapshot written: ${snapshot.songs.size} songs, ${snapshot.setlists.size} setlists")
                    RandomPicksWidget().refreshAll(applicationContext)
                    SetlistWidget().refreshAll(applicationContext)
                }
            }
        }
    }
}
