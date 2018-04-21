package com.pandulapeter.campfire.feature.shared.widget

import android.annotation.SuppressLint
import android.content.Context
import android.databinding.DataBindingUtil
import android.os.Parcelable
import android.support.annotation.StringRes
import android.text.InputFilter
import android.view.LayoutInflater
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.ViewToolbarTextInputBinding
import com.pandulapeter.campfire.util.*
import kotlinx.android.parcel.Parcelize


@SuppressLint("ViewConstructor")
class ToolbarTextInputView(context: Context, @StringRes hintText: Int, isSearch: Boolean) : FrameLayout(context, null, 0) {

    private val binding = DataBindingUtil.inflate<ViewToolbarTextInputBinding>(LayoutInflater.from(context), R.layout.view_toolbar_text_input, this, true).apply {
        textInput.run {
            hint = context.getString(hintText)
            imeOptions = if (isSearch) EditorInfo.IME_ACTION_SEARCH else EditorInfo.IME_ACTION_DONE
            filters = arrayOfNulls<InputFilter>(1).apply {
                this[0] = InputFilter.LengthFilter(context.resources.getInteger(if (isSearch) R.integer.search_query_limit else R.integer.playlist_name_length_limit))
            }
            visibleOrInvisible = false
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == imeOptions) {
                    hideKeyboard(this)
                    onDoneButtonPressed()
                }
                true
            }
        }
    }
    var onDoneButtonPressed: () -> Unit = {}

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