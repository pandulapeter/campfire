package com.pandulapeter.campfire.feature.home.shared

import android.content.Context
import android.databinding.*
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ViewSwitcher
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.SearchTitleBinding
import com.pandulapeter.campfire.util.dimension
import com.pandulapeter.campfire.util.hideKeyboard
import com.pandulapeter.campfire.util.showKeyboard

/**
 * Custom view that either displays the title of the screen or a text input field.
 *
 * TODO: Implement search-to-close and close-to-search vector animations.
 * TODO: Implement state saving and restoration.
 */
@BindingMethods(BindingMethod(type = SearchTitleView::class, attribute = "onQueryChanged", method = "setOnQueryChangeListener"))
@InverseBindingMethods(InverseBindingMethod(type = SearchTitleView::class, attribute = "query"))
class SearchTitleView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : ViewSwitcher(context, attrs) {
    private val binding = DataBindingUtil.inflate<SearchTitleBinding>(LayoutInflater.from(context), R.layout.view_search_title, this, true)
    private var onQueryChangedListener: OnQueryChangedListener? = null
    var title: String
        get() = binding.title.text.toString()
        set(value) {
            binding.title.text = value
        }
    var query: String
        get() = binding.query.text.toString()
        set(value) {
            if (value != query) {
                binding.query.setText(value)
            }
        }
    var searchInputVisible: Boolean
        get() = displayedChild == 1
        set(value) {
            if (value != searchInputVisible) {
                displayedChild = if (value) 1 else 0
                binding.query.run {
                    if (value) {
                        requestFocus()
                        post { showKeyboard(this) }
                    } else {
                        query = ""
                        hideKeyboard(this)
                    }
                }
            }
        }

    init {
        clipChildren = false
        setPadding(0, 0, context.dimension(R.dimen.action_button_margin), 0)
        context.obtainStyledAttributes(attrs, R.styleable.SearchTitleView, 0, 0)?.apply {
            getString(R.styleable.SearchTitleView_title)?.let { title = it }
            recycle()
        }
        binding.search.setOnClickListener { searchInputVisible = true }
        binding.close.setOnClickListener { searchInputVisible = false }
        binding.query.addTextChangedListener(object : TextWatcher {

            override fun afterTextChanged(editable: Editable?) {
                onQueryChangedListener?.onQueryChanged(editable.toString())
            }

            override fun beforeTextChanged(charSequence: CharSequence?, p1: Int, p2: Int, p3: Int) = Unit

            override fun onTextChanged(charSequence: CharSequence?, p1: Int, p2: Int, p3: Int) = Unit
        })
        setInAnimation(context, android.R.anim.slide_in_left)
        setOutAnimation(context, android.R.anim.slide_out_right)
    }

    fun setOnQueryChangeListener(listener: OnQueryChangedListener?) {
        onQueryChangedListener = listener
    }

    interface OnQueryChangedListener {
        fun onQueryChanged(newQuery: String)
    }
}