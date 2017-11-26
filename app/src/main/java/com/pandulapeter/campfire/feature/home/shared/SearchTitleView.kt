package com.pandulapeter.campfire.feature.home.shared

import android.content.Context
import android.databinding.DataBindingUtil
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ViewSwitcher
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.SearchTitleBinding
import com.pandulapeter.campfire.util.dimension

/**
 * Custom view that either displays the title of the screen or a text input field.
 *
 * TODO: Implement search-to-close and close-to-search vector animations.
 */
class SearchTitleView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : ViewSwitcher(context, attrs) {
    private val binding = DataBindingUtil.inflate<SearchTitleBinding>(LayoutInflater.from(context), R.layout.view_search_title, this, true)
    var title: String
        get() = binding.title.text.toString()
        set(value) {
            binding.title.text = value
        }

    init {
        clipChildren = false
        setPadding(0, 0, context.dimension(R.dimen.action_button_margin), 0)
        context.obtainStyledAttributes(attrs, R.styleable.SearchTitleView, 0, 0)?.apply {
            getString(R.styleable.SearchTitleView_title)?.let { title = it }
            recycle()
        }
        binding.search.setOnClickListener { displayedChild = 1 }
        binding.close.setOnClickListener { displayedChild = 0 }
    }
}