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
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
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
        BitmapFactory.decodeFile(file.path)?.let(::roundedSquareOf)
    } catch (exception: OutOfMemoryError) {
        println("Could not decode a widget cover: out of memory")
        null
    }

    /**
     * [source] cropped to its centered square, its corners rounded as the iOS widget's are (the same share of the side),
     * drawn into the bitmap itself: a widget below Android 12 cannot clip an image, and `cornerRadius` from 12 on would
     * round a different share on a launcher's own sizes. A thumbnail is a small JPEG, so its transparent corners cost
     * little against Android's limit on a widget's bitmap memory.
     */
    private fun roundedSquareOf(source: Bitmap): Bitmap {
        val side = minOf(source.width, source.height)
        val left = (source.width - side) / 2f
        val top = (source.height - side) / 2f
        val rounded = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                setLocalMatrix(Matrix().apply { setTranslate(-left, -top) })
            }
        }
        val radius = side * CORNER_RADIUS_SHARE
        Canvas(rounded).drawRoundRect(RectF(0f, 0f, side.toFloat(), side.toFloat()), radius, radius, paint)
        source.recycle()
        return rounded
    }

    private fun folder(context: Context) = File(File(context.filesDir, WIDGET_DIRECTORY_NAME), FOLDER_NAME)

    private const val FOLDER_NAME = "covers"
    private const val TEMPORARY_SUFFIX = ".tmp"

    /** The iOS widget's 6 pt corners on its 28 pt cover. */
    private const val CORNER_RADIUS_SHARE = 6f / 28f
}

/** The covers of the snapshot shown ([WidgetCoverFiles.bitmaps]), by the name its songs give them. */
@Composable
internal fun rememberWidgetCovers(): Map<String, Bitmap> = WidgetCoverFiles.bitmaps.collectAsState().value
