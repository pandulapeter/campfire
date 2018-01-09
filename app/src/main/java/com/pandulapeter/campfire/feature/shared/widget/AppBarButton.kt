package com.pandulapeter.campfire.feature.shared.widget

import android.content.Context
import android.graphics.Rect
import android.support.v7.widget.AppCompatImageView
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.TouchDelegate
import android.view.View
import android.view.ViewGroup
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.util.dimension


/**
 * Custom ImageView that sets its own background and padding and provides a fake touch area that is
 * larger than the actual View bounds. Used for icons that are part of the app bar.
 */
class AppBarButton @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : AppCompatImageView(context, attrs, defStyleAttr) {

    init {
        val padding = context.dimension(R.dimen.toolbar_action_button_padding)
        setPadding(padding, padding, padding, padding)
        val outValue = TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true)
        setBackgroundResource(outValue.resourceId)
        post {
            layoutParams = (layoutParams as ViewGroup.MarginLayoutParams).apply { marginEnd = context.dimension(R.dimen.toolbar_action_button_margin_fix) }
            (parent as View).let { parent ->
                val extraTouchArea = context.dimension(R.dimen.toolbar_action_button_extra_touch_area)
                val bounds = Rect()
                getHitRect(bounds)
                bounds.left -= extraTouchArea
                bounds.top -= extraTouchArea
                bounds.right += extraTouchArea
                bounds.bottom += extraTouchArea
                //TODO: Does not seem to be working for all children.
                parent.touchDelegate = (parent.touchDelegate as? TouchDelegateComposite) ?: TouchDelegateComposite(this).apply {
                    addDelegate(TouchDelegate(bounds, this@AppBarButton))
                }
            }
        }
    }

    private inner class TouchDelegateComposite(view: View) : TouchDelegate(Rect(), view) {

        private val delegates = mutableListOf<TouchDelegate>()

        override fun onTouchEvent(event: MotionEvent): Boolean {
            var res = false
            val x = event.x
            val y = event.y
            for (delegate in delegates) {
                event.setLocation(x, y)
                res = delegate.onTouchEvent(event) || res
            }
            return res
        }

        fun addDelegate(delegate: TouchDelegate) = delegates.add(delegate)
    }
}