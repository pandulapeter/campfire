package com.pandulapeter.campfire.feature.shared.widget

import android.animation.LayoutTransition
import android.content.Context
import android.databinding.DataBindingUtil
import android.support.annotation.StringRes
import android.support.constraint.ConstraintLayout
import android.util.AttributeSet
import android.view.LayoutInflater
import com.pandulapeter.campfire.PlaceholderBinding
import com.pandulapeter.campfire.R


/**
 * Displays a placeholder text with an optional call to action button over empty lists.
 */
class PlaceholderView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    ConstraintLayout(context, attrs, defStyleAttr) {

    private val binding = DataBindingUtil.inflate<PlaceholderBinding>(LayoutInflater.from(context), R.layout.view_placeholder, this, true)

    init {
        clipChildren = false
        layoutTransition = LayoutTransition()
        val styledAttrs = context.obtainStyledAttributes(attrs, R.styleable.PlaceholderView)
        styledAttrs.getString(R.styleable.PlaceholderView_text)?.let { setText(it) }
        styledAttrs.getString(R.styleable.PlaceholderView_buttonText)?.let { setButtonText(it) }
        styledAttrs.recycle()
    }

    override fun setOnClickListener(listener: OnClickListener?) {
        binding.button.setOnClickListener(listener)
        binding.button.visibility = if (listener == null) GONE else VISIBLE
    }

    fun setButtonVisibility(isVisible: Boolean) {
        binding.button.visibility = if (isVisible) VISIBLE else GONE
    }

    fun setText(@StringRes resource: Int) = setText(context.getString(resource))

    private fun setText(text: String) {
        binding.text.text = text
    }

    fun setButtonText(@StringRes resource: Int) = setButtonText(context.getString(resource))

    private fun setButtonText(buttonText: String) {
        binding.button.text = buttonText
    }
}