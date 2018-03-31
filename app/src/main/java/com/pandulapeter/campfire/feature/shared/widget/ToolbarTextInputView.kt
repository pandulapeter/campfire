package com.pandulapeter.campfire.feature.shared.widget

import android.content.Context
import android.databinding.DataBindingUtil
import android.os.Parcelable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.ViewToolbarTextInputBinding
import com.pandulapeter.campfire.util.animatedVisibilityEnd
import com.pandulapeter.campfire.util.hideKeyboard
import com.pandulapeter.campfire.util.showKeyboard
import com.pandulapeter.campfire.util.visibleOrInvisible
import kotlinx.android.parcel.Parcelize


class ToolbarTextInputView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding = DataBindingUtil.inflate<ViewToolbarTextInputBinding>(LayoutInflater.from(context), R.layout.view_toolbar_text_input, this, true).apply {
        textInput.run {
            hint = context.getString(R.string.library_search)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            visibleOrInvisible = false
        }
    }

    init {
        id = R.id.toolbar_text_input_view
    }

    val title = binding.title
    var isTextInputVisible: Boolean
        get() = binding.textInput.visibleOrInvisible
        set(value) {
            if (isTextInputVisible != value) {
                binding.title.animatedVisibilityEnd = !value
                binding.textInput.animatedVisibilityEnd = value
                if (value) {
                    showKeyboard(binding.textInput)
                } else {
                    hideKeyboard(binding.textInput)
                }
            }
        }

    override fun onSaveInstanceState(): Parcelable = State(super.onSaveInstanceState(), isTextInputVisible)

    override fun onRestoreInstanceState(state: Parcelable) {
        (state as? State)?.let {
            super.onRestoreInstanceState(state.superParcelable)
            if (it.isTextInputVisible) {
                binding.textInput.visibleOrInvisible = true
                binding.title.visibleOrInvisible = false
            }
        } ?: super.onRestoreInstanceState(state)
    }

    @Parcelize
    private data class State(val superParcelable: Parcelable?, val isTextInputVisible: Boolean) : BaseSavedState(superParcelable)
}