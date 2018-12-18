package com.pandulapeter.campfire.feature.shared.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.WindowInsets
import androidx.coordinatorlayout.widget.CoordinatorLayout

class RootCoordinatorLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : CoordinatorLayout(context, attrs, defStyleAttr) {

    var paint: Paint? = null
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }
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
        canvas?.run { paint?.let { paint -> drawRect(0f, 0f, width.toFloat(), statusBarHeight.toFloat(), paint) } }
        super.dispatchDraw(canvas)
    }
}