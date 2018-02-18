package com.pandulapeter.campfire.feature.shared.widget

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import android.view.Window
import android.widget.FrameLayout
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.util.color


/**
 * Fixes fitsSystemWindow and status bar color problems introduced by multiple drawers and dynamic theming.
 */
class ContainerView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    FrameLayout(context, attrs, defStyleAttr) {

    private val statusBarHeight by lazy {
        val rectangle = Rect()
        val window = (context as Activity).window
        window.decorView.getWindowVisibleDisplayFrame(rectangle)
        rectangle.top.toFloat() - window.findViewById<View>(Window.ID_ANDROID_CONTENT).top
    }
    private val paint = Paint().apply { color = context.color(R.color.primary) }

    init {
        fitsSystemWindows = true
    }

    override fun dispatchDraw(canvas: Canvas?) {
        canvas?.run { drawRect(0f, 0f, width.toFloat(), statusBarHeight, paint) }
        super.dispatchDraw(canvas)
    }
}