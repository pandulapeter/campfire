package com.pandulapeter.campfire.feature.detail

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.cardview.widget.CardView

class ZoomCardView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : CardView(context, attrs, defStyleAttr) {

    var detector: ScaleGestureDetector? = null

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        super.dispatchTouchEvent(ev)
        return detector?.onTouchEvent(ev) ?: false
    }
}