package com.pandulapeter.campfire.feature.shared.widget

import android.content.Context
import android.databinding.DataBindingUtil
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.animation.AnimationUtils
import android.widget.ViewFlipper
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.ViewStateLayoutBinding

class StateLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : ViewFlipper(context, attrs) {

    var state: State = State.LOADING
        set(value) {
            if (field != value) {
                field = value
                displayedChild = value.childIndex
            }
        }

    init {
        DataBindingUtil.inflate<ViewStateLayoutBinding>(LayoutInflater.from(context), R.layout.view_state_layout, this, true)
        inAnimation = AnimationUtils.loadAnimation(context, android.R.anim.fade_in)
        outAnimation = AnimationUtils.loadAnimation(context, android.R.anim.fade_out)
        animateFirstView = false
    }

    enum class State(val childIndex: Int) {
        LOADING(0), ERROR(1), NORMAL(2)
    }
}