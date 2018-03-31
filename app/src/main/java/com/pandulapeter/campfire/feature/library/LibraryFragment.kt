package com.pandulapeter.campfire.feature.library

import android.content.Context
import android.os.Bundle
import android.view.View
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.FragmentLibraryBinding
import com.pandulapeter.campfire.feature.CampfireFragment
import com.pandulapeter.campfire.feature.shared.widget.ToolbarButton
import com.pandulapeter.campfire.feature.shared.widget.ToolbarTextInputView
import com.pandulapeter.campfire.util.drawable

class LibraryFragment : CampfireFragment<FragmentLibraryBinding>(R.layout.fragment_library) {

    private val toolbarTextInputView by lazy { ToolbarTextInputView(mainActivity.toolbarContext).apply { toolbarTextView.updateToolbarTitle(R.string.home_library) } }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.root.setOnClickListener { mainActivity.navigateToSettings() }
    }

    override fun inflateToolbarTitle(context: Context) = toolbarTextInputView

    override fun inflateToolbarButtons(context: Context) = listOf<View>(
        ToolbarButton(context).apply {
            setImageDrawable(context.drawable(R.drawable.ic_search_24dp))
            setOnClickListener { toolbarTextInputView.run { isTextInputVisible = !isTextInputVisible } }
        },
        ToolbarButton(context).apply {
            setImageDrawable(context.drawable(R.drawable.ic_view_options_24dp))
            setOnClickListener { binding.root.makeSnackbar("Work in progress").setAction(R.string.got_it) { }.show() }
        }
    )
}