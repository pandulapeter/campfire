package com.pandulapeter.campfire.feature.shared.widget

import android.content.Context
import android.graphics.drawable.Drawable
import android.support.design.widget.FloatingActionButton
import android.util.AttributeSet
import com.pandulapeter.campfire.util.obtainColor


/**
 * Custom [FloatingActionButton] that sets the drawable tint based on the current theme
 */
class FloatingActionButton @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    FloatingActionButton(context, attrs, defStyleAttr) {

    override fun setImageDrawable(drawable: Drawable?) {
        drawable?.setTint(context.obtainColor(android.R.attr.windowBackground))
        super.setImageDrawable(drawable)
    }
}