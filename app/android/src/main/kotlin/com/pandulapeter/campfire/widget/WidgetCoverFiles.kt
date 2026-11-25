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
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import com.pandulapeter.campfire.presentation.ui.widget.WidgetSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.IOException

/**
 * `widget/covers/` in the app's files directory: the thumbnails of the covers the snapshot's songs name, written and
 * deleted by the app (`:presentation`'s `WidgetPublisher`), and decoded here for the widgets. Outside the backup rules'
 * allow-list with the snapshot, as everything under `widget/` is.
 */
internal object WidgetCoverFiles {

    private val _bitmaps = MutableStateFlow<Map<String, Bitmap>>(emptyMap())

    /**
     * The covers of the snapshot last [decode]d, by name. One bitmap for each, shared by every size and row that draws it:
     * the widget's views carry their bitmaps along, and the same bitmap is carried once.
     */
    val bitmaps: StateFlow<Map<String, Bitmap>> = _bitmaps.asStateFlow()

    fun names(context: Context): Set<String> = folder(context).list()?.filterNot { it.endsWith(TEMPORARY_SUFFIX) }?.toSet().orEmpty()

    /** Writes [bytes] as [name], beside it first and renamed over it, so a widget never decodes half a file. */
    fun write(context: Context, name: String, bytes: ByteArray) {
        val folder = folder(context)
        if (!folder.isDirectory && !folder.mkdirs()) throw IOException("Could not create ${folder.name}")
        val temporary = File(folder, name + TEMPORARY_SUFFIX)
        temporary.writeBytes(bytes)
        if (!temporary.renameTo(File(folder, name))) {
            temporary.delete()
            throw IOException("Could not write a cover")
        }
    }

    fun delete(context: Context, name: String) {
        File(folder(context), name).delete()
    }

    /**
     * Decodes the covers [snapshot] names that are not decoded yet, and forgets those it no longer names; off the main
     * thread, before the snapshot is shown. A cover that cannot be read leaves its song without one.
     */
    fun decode(context: Context, snapshot: WidgetSnapshot?) {
        val decoded = _bitmaps.value
        val names = snapshot?.songs?.mapNotNullTo(mutableSetOf()) { it.cover }.orEmpty()
        _bitmaps.value = names.mapNotNull { name ->
            (decoded[name] ?: decodeFile(File(folder(context), name)))?.let { name to it }
        }.toMap()
    }

    private fun decodeFile(file: File): Bitmap? = try {
        // A JPEG has no alpha, so half the memory of the default, against Android's limit on a widget's bitmaps.
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 })
    } catch (exception: OutOfMemoryError) {
        println("Could not decode a widget cover: out of memory")
        null
    }

    private fun folder(context: Context) = File(File(context.filesDir, WIDGET_DIRECTORY_NAME), FOLDER_NAME)

    private const val FOLDER_NAME = "covers"
    private const val TEMPORARY_SUFFIX = ".tmp"
}

/** The covers of the snapshot shown ([WidgetCoverFiles.bitmaps]), by the name its songs give them. */
@Composable
internal fun rememberWidgetCovers(): Map<String, Bitmap> = WidgetCoverFiles.bitmaps.collectAsState().value
