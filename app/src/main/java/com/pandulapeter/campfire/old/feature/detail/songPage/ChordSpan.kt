package com.pandulapeter.campfire.old.feature.detail.songPage

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Paint.FontMetricsInt
import android.text.style.ReplacementSpan

/**
 * Custom Spannable that can be used to display chords.
 */

class ChordSpan(private var chordName: String) : ReplacementSpan() {

    override fun draw(canvas: Canvas, text: CharSequence, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
        paint.fontMetricsInt?.let {
            val space = it.ascent - it.descent + it.leading
            canvas.drawText(chordName, x, y.toFloat() + space, paint)
        }
    }

    override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fontMetricsInt: FontMetricsInt?): Int {
        fontMetricsInt?.let {
            val space = paint.getFontMetricsInt(it)
            it.ascent -= space
            it.top -= space
            it.top -= space
        }
        return Math.round(paint.measureText(text, start, start))
    }
}