package com.pandulapeter.campfire.feature.shared.widget

import android.animation.Animator
import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.util.dimension

class FloatingActionButton @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : AppCompatImageView(context, attrs, defStyleAttr) {

    private val hideListener = object : Animator.AnimatorListener {

        override fun onAnimationRepeat(animation: Animator?) = Unit

        override fun onAnimationEnd(animation: Animator?) {
            visibility = GONE
        }

        override fun onAnimationCancel(animation: Animator?) = Unit

        override fun onAnimationStart(animation: Animator?) = Unit
    }
    private val showListener = object : Animator.AnimatorListener {

        override fun onAnimationRepeat(animation: Animator?) = Unit

        override fun onAnimationEnd(animation: Animator?) = Unit

        override fun onAnimationCancel(animation: Animator?) = Unit

        override fun onAnimationStart(animation: Animator?) {
            visibility = VISIBLE
        }
    }

    init {
        setBackgroundResource(R.drawable.bg_floating_action_button)
        scaleType = ScaleType.CENTER
        elevation = context.dimension(R.dimen.floating_action_button_elevation).toFloat()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        context.dimension(R.dimen.floating_action_button_size).let { setMeasuredDimension(it, it) }
    }

    fun show() {
        visibility = VISIBLE
        animate().cancel()
        rotation = 0f
        animate().scaleX(1f).scaleY(1f).alpha(1f).apply {
            duration = ANIMATION_DURATION
            if (!isVisible()) {
                rotationBy(360f)
            }
            setListener(showListener)
        }.start()
    }

    fun hide() {
        visibility = VISIBLE
        animate().cancel()
        rotation = 0f
        animate().scaleX(0f).scaleY(0f).alpha(0f).apply {
            duration = ANIMATION_DURATION
            if (isVisible()) {
                rotationBy(360f)
            }
            setListener(hideListener)
        }.start()
    }

    fun isVisible() = alpha > 0

    companion object {
        private const val ANIMATION_DURATION = 300L
    }
}