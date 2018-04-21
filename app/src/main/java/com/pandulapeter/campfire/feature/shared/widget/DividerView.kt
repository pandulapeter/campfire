package com.pandulapeter.campfire.feature.shared.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View
import com.pandulapeter.campfire.util.obtainColor

class DividerView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    View(context, attrs, defStyleAttr) {

    init {
        setBackgroundColor(context.obtainColor(android.R.attr.textColorPrimary))
        alpha = 0.4f
    }
}