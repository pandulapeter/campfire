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
import java.io.File
import java.io.IOException

/**
 * `widget/snapshot.json` in the app's files directory: everything the widgets show, written by the app
 * ([WidgetSnapshotPublisher]) and read by the widgets alone, which never read the library. Outside the backup rules'
 * allow-list, since it names songs and setlists and the app writes it again from the restored library.
 */
internal object WidgetSnapshotFile {

    /** The snapshot, or null before the app has ever written one (or where it cannot be read). */
    fun read(context: Context): WidgetSnapshot? = try {
        file(context).takeIf { it.exists() }?.readText()?.let(WidgetSnapshot::decode)
    } catch (exception: IOException) {
        println("Could not read the widgets' snapshot: ${exception.message}")
        null
    }

    /**
     * Writes [json] unless the file already holds it, and says whether it did. Written beside the file and renamed over
     * it, which replaces it in one step, so that a widget drawn meanwhile reads the old snapshot or the new one, never
     * half of one.
     */
    fun write(context: Context, json: String): Boolean {
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

    private fun file(context: Context) = File(File(context.filesDir, DIRECTORY_NAME), FILE_NAME)

    private const val DIRECTORY_NAME = "widget"
    private const val FILE_NAME = "snapshot.json"
}
