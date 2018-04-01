package com.pandulapeter.campfire.feature.shared.widget

import android.content.Context
import android.databinding.DataBindingUtil
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.ViewToolbarTextInputBinding
import com.pandulapeter.campfire.util.*


class ToolbarTextInputView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding = DataBindingUtil.inflate<ViewToolbarTextInputBinding>(LayoutInflater.from(context), R.layout.view_toolbar_text_input, this, true).apply {
        textInput.run {
            hint = context.getString(R.string.library_search)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            visibleOrInvisible = false
        }
    }

    init {
        clipChildren = false
    }

    val title = binding.title
    val textInput = binding.textInput
    var isTextInputVisible: Boolean
        get() = textInput.visibleOrInvisible
        set(value) {
            if (isTextInputVisible != value) {
                title.animatedVisibilityStart = !value
                textInput.animatedVisibilityEnd = value
                if (value) {
                    showKeyboard(textInput)
                } else {
                    hideKeyboard(textInput)
                }
            }
        }

    fun showTextInput() {
        textInput.visibleOrInvisible = true
        title.visibleOrInvisible = false
    }
}