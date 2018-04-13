package com.pandulapeter.campfire.feature.shared.widget

import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.support.v7.widget.AppCompatImageView
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.TouchDelegate
import android.view.View
import android.view.ViewGroup
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.util.dimension
import com.pandulapeter.campfire.util.obtainColor

class ToolbarButton @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : AppCompatImageView(context, attrs, defStyleAttr) {

    init {
        val padding = context.dimension(R.dimen.toolbar_action_button_padding)
        setPadding(padding, padding, padding, padding)
        val outValue = TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true)
        setBackgroundResource(outValue.resourceId)
    }

    override fun setImageDrawable(drawable: Drawable?) {
        drawable?.setTint(context.obtainColor(android.R.attr.textColorPrimary))
        super.setImageDrawable(drawable)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
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
                addDelegate(TouchDelegate(bounds, this@ToolbarButton))
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