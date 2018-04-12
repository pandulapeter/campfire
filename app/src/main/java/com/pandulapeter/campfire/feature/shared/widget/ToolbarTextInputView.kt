package com.pandulapeter.campfire.feature.shared.widget

import android.content.Context
import android.databinding.DataBindingUtil
import android.os.Parcelable
import android.text.InputFilter
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.ViewToolbarTextInputBinding
import com.pandulapeter.campfire.util.*
import kotlinx.android.parcel.Parcelize


class ToolbarTextInputView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding = DataBindingUtil.inflate<ViewToolbarTextInputBinding>(LayoutInflater.from(context), R.layout.view_toolbar_text_input, this, true).apply {
        textInput.run {
            hint = context.getString(R.string.library_search)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            filters = arrayOfNulls<InputFilter>(1).apply { this[0] = InputFilter.LengthFilter(context.resources.getInteger(R.integer.search_query_limit)) }
            visibleOrInvisible = false
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == imeOptions) {
                    hideKeyboard(this)
                }
                true
            }
        }
    }

    init {
        id = R.id.toolbar_text_input_view
        clipChildren = false
    }

    val title = binding.title
    val textInput = binding.textInput
    var isTextInputVisible = false

    fun animateTextInputVisibility(isVisible: Boolean) {
        if (isTextInputVisible != isVisible) {
            isTextInputVisible = isVisible
            title.animatedVisibilityStart = !isVisible
            textInput.animatedVisibilityEnd = isVisible
            if (isVisible) {
                textInput.requestFocus()
                showKeyboard(textInput)
            } else {
                hideKeyboard(textInput)
            }
        }
    }

    fun showTextInput() {
        textInput.visibleOrInvisible = true
        title.visibleOrInvisible = false
        isTextInputVisible = true
    }

    override fun onSaveInstanceState(): Parcelable = State(super.onSaveInstanceState(), isTextInputVisible)

    override fun onRestoreInstanceState(state: Parcelable?) {
        (state as? State)?.let {
            super.onRestoreInstanceState(it.state)
            if (it.isTextInputVisible) {
                showTextInput()
            }
        } ?: super.onRestoreInstanceState(state)
    }

    @Parcelize
    private data class State(val state: Parcelable, val isTextInputVisible: Boolean) : BaseSavedState(state), Parcelable
}