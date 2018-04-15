package com.pandulapeter.campfire.feature.shared.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.support.design.widget.CoordinatorLayout
import android.util.AttributeSet
import android.view.WindowInsets
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.util.color

class RootCoordinatorLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : CoordinatorLayout(context, attrs, defStyleAttr) {

    //    private val statusBarHeight by lazy {
//        val rectangle = Rect()
//        val window = (context as Activity).window
//        window.decorView.getWindowVisibleDisplayFrame(rectangle)
//        val top = rectangle.top
//        if (top > height / 3) 0f else top - window.findViewById<View>(Window.ID_ANDROID_CONTENT).top.toFloat()
//    }
    private val paint = Paint().apply { color = context.color(R.color.primary) }
    var insetChangeListener: (statusBarHeight: Int) -> Unit = {}
        set(value) {
            field = value
            value(statusBarHeight)
        }
    private var statusBarHeight = 0

    init {
        fitsSystemWindows = true
    }

    override fun dispatchApplyWindowInsets(insets: WindowInsets?): WindowInsets {
        insets?.systemWindowInsetTop?.let {
            if (statusBarHeight != it) {
                statusBarHeight = it
                insetChangeListener(it)
            }
        }
        return super.dispatchApplyWindowInsets(insets)
    }

    override fun dispatchDraw(canvas: Canvas?) {
        canvas?.run {
            drawRect(0f, 0f, width.toFloat(), statusBarHeight.toFloat(), paint)
            super.dispatchDraw(this)
        }
    }
}