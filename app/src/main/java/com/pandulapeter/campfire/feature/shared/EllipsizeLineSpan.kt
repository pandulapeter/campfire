package com.pandulapeter.campfire.feature.shared

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.text.style.LineBackgroundSpan
import android.text.style.ReplacementSpan

/**
 * Ellipsizes individual lines of a text.
 */
class EllipsizeLineSpan : ReplacementSpan(), LineBackgroundSpan {
    private var layoutLeft = 0
    private var layoutRight = 0

    override fun drawBackground(
        canvas: Canvas,
        paint: Paint,
        left: Int,
        right: Int,
        top: Int,
        baseline: Int,
        bottom: Int,
        text: CharSequence,
        start: Int,
        end: Int,
        lnum: Int
    ) {
        val clipRect = Rect()
        canvas.getClipBounds(clipRect)
        layoutLeft = clipRect.left
        layoutRight = clipRect.right
    }

    override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?) = layoutRight - layoutLeft

    override fun draw(canvas: Canvas, text: CharSequence, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
        var newEnd = end
        val textWidth = paint.measureText(text, start, newEnd)
        if (x + Math.ceil(textWidth.toDouble()).toInt() < layoutRight) {  //text fits
            canvas.drawText(text, start, newEnd, x, y.toFloat(), paint)
        } else {
            val ellipsizeWidth = paint.measureText("\u2026")
            newEnd = start + paint.breakText(text, start, newEnd, true, layoutRight.toFloat() - x - ellipsizeWidth, null)
            canvas.drawText(text, start, newEnd, x, y.toFloat(), paint)
            canvas.drawText("\u2026", x + paint.measureText(text, start, newEnd), y.toFloat(), paint)
        }
    }
}