package com.pandulapeter.campfire.feature.shared.widget

import android.content.Context
import android.databinding.DataBindingUtil
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.animation.AnimationUtils
import android.widget.ViewFlipper
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.ViewStateLayoutBinding
import com.pandulapeter.campfire.util.useStyledAttributes
import com.pandulapeter.campfire.util.visibleOrGone

class StateLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : ViewFlipper(context, attrs) {

    private val binding = DataBindingUtil.inflate<ViewStateLayoutBinding>(LayoutInflater.from(context), R.layout.view_state_layout, this, true)
    var state: State = State.LOADING
        set(value) {
            if (field != value) {
                field = value
                displayedChild = value.childIndex
            }
        }
    var onButtonClicked: OnClickListener? = null
    var buttonText: String? = null
        set(value) {
            field = value
            binding.button.run {
                visibleOrGone = value != null
                text = value
            }
        }
    var text = ""
        set(value) {
            field = value
            binding.text.text = value
        }

    init {
        inAnimation = AnimationUtils.loadAnimation(context, android.R.anim.fade_in)
        outAnimation = AnimationUtils.loadAnimation(context, android.R.anim.fade_out)
        binding.button.setOnClickListener { onButtonClicked?.onClick(this) }
        useStyledAttributes(attrs, R.styleable.StateLayout) {
            buttonText = getString(R.styleable.StateLayout_buttonText)
            text = getString(R.styleable.StateLayout_text)
        }
    }

    enum class State(val childIndex: Int) {
        LOADING(0), ERROR(1), NORMAL(2);

        companion object {
            fun fromInt(int: Int) = when (int) {
                0 -> LOADING
                1 -> ERROR
                2 -> NORMAL
                else -> throw IllegalArgumentException("Invalid child index: $int")
            }
        }
    }
}