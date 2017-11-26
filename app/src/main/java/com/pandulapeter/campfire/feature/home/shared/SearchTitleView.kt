package com.pandulapeter.campfire.feature.home.shared

import android.content.Context
import android.databinding.DataBindingUtil
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
 * TODO: Set up two-way data binding for the query and the state.
 */
class SearchTitleView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : ViewSwitcher(context, attrs) {
    private val binding = DataBindingUtil.inflate<SearchTitleBinding>(LayoutInflater.from(context), R.layout.view_search_title, this, true)
    var title: String
        get() = binding.title.text.toString()
        set(value) {
            binding.title.text = value
        }
    var searchInputVisible: Boolean
        get() = displayedChild == 1
        set(value) {
            displayedChild = if (value) 1 else 0
            binding.query.run {
                if (value) {
                    requestFocus()
                    post { showKeyboard(this) }
                } else {
                    hideKeyboard(this)
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
    }
}