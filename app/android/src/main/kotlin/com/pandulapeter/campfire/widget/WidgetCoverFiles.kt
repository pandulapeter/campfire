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
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.glance.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * `widget/covers/` in the app's files directory: the thumbnails of the covers the snapshot's songs name, written and
 * deleted by the app (`:presentation`'s `WidgetPublisher`), and decoded here for the widgets, only those they draw.
 * Outside the backup rules' allow-list with the snapshot, as everything under `widget/` is.
 */
internal object WidgetCoverFiles {

    /**
     * The covers decoded lately, by name, bounded: one bitmap for each, shared by every size, widget, and row that draws
     * it, since a widget's views carry their bitmaps along and the same bitmap is carried once. One no longer drawn is let
     * go once newer ones push it out, or at once where the snapshot stops naming it ([forgetAllBut]).
     */
    private val cache = object : LruCache<String, Bitmap>(MAX_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

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

    /** Those of [names] already decoded, read without touching a file, for a widget's first drawing. */
    fun cached(names: Set<String>): Map<String, Bitmap> = names.mapNotNull { name -> cache.get(name)?.let { name to it } }.toMap()

    /**
     * The covers [names] name, decoded off the main thread where they are not yet: only what a widget draws, never every
     * cover the snapshot names. A cover that cannot be read leaves its song without one.
     */
    suspend fun load(context: Context, names: Set<String>): Map<String, Bitmap> = withContext(Dispatchers.IO) {
        names.mapNotNull { name ->
            (cache.get(name) ?: decodeFile(File(folder(context), name))?.also { cache.put(name, it) })?.let { name to it }
        }.toMap()
    }

    /** Lets go of every decoded cover but those of [names], the covers the snapshot written names. */
    fun forgetAllBut(names: Set<String>) = cache.snapshot().keys.filterNot { it in names }.forEach { cache.remove(it) }

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

    /** Room for a few dozen 120 px thumbnails, well within Android's limit on a widget's bitmap memory. */
    private const val MAX_CACHE_BYTES = 2 * 1024 * 1024
}

/**
 * The covers [names] name (those of the songs a widget draws at its size), by name: what is decoded already at once, the
 * rest once [WidgetCoverFiles.load] has read them.
 */
@Composable
internal fun rememberWidgetCovers(names: Set<String>): Map<String, Bitmap> {
    val context = LocalContext.current
    return produceState(initialValue = WidgetCoverFiles.cached(names), names) {
        value = WidgetCoverFiles.load(context = context, names = names)
    }.value
}
