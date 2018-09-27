package com.pandulapeter.campfire.feature.shared.widget

import android.content.Context
import android.support.v7.widget.AppCompatImageView
import android.util.AttributeSet
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.util.dimension

class FloatingActionButton @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : AppCompatImageView(context, attrs, defStyleAttr) {

    init {
        setBackgroundResource(R.drawable.bg_floating_action_button)
        scaleType = ScaleType.CENTER
        elevation = context.dimension(R.dimen.floating_action_button_elevation).toFloat()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        context.dimension(R.dimen.floating_action_button_size).let { setMeasuredDimension(it, it) }
    }

    fun show() {
        animate().cancel()
        rotation = 0f
        animate().scaleX(1f).scaleY(1f).alpha(1f).apply {
            duration = ANIMATION_DURATION
            if (!isVisible()) {
                rotationBy(360f)
            }
        }.start()
    }

    fun hide() {
        animate().cancel()
        rotation = 0f
        animate().scaleX(0f).scaleY(0f).alpha(0f).apply {
            duration = ANIMATION_DURATION
            if (isVisible()) {
                rotationBy(360f)
            }
        }.start()
    }

    fun isVisible() = alpha > 0

    companion object {
        private const val ANIMATION_DURATION = 500L
    }
}