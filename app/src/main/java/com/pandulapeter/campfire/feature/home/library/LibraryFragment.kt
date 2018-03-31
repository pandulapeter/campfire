package com.pandulapeter.campfire.feature.home.library

import android.content.Context
import android.os.Bundle
import android.support.graphics.drawable.AnimatedVectorDrawableCompat
import android.view.View
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.FragmentLibraryBinding
import com.pandulapeter.campfire.feature.CampfireFragment
import com.pandulapeter.campfire.feature.shared.widget.ToolbarTextInputView
import com.pandulapeter.campfire.util.BundleArgumentDelegate
import com.pandulapeter.campfire.util.drawable

class LibraryFragment : CampfireFragment<FragmentLibraryBinding>(R.layout.fragment_library) {

    private var Bundle.isTextInputVisible by BundleArgumentDelegate.Boolean("isTextInputVisible")
    private var Bundle.searchQuery by BundleArgumentDelegate.String("searchQuery")
    private val toolbarTextInputView by lazy { ToolbarTextInputView(mainActivity.toolbarContext).apply { title.updateToolbarTitle(R.string.home_library) } }
    private val searchToggle by lazy { mainActivity.toolbarContext.createToolbarButton(R.drawable.ic_search_24dp) { toggleTextInputVisibility() } }
    private val drawableCloseToSearch by lazy { AnimatedVectorDrawableCompat.create(context, R.drawable.avd_close_to_search_24dp) }
    private val drawableSearchToClose by lazy { AnimatedVectorDrawableCompat.create(context, R.drawable.avd_search_to_close_24dp) }
    override val navigationMenu = R.menu.library

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        savedInstanceState?.let {
            if (it.isTextInputVisible) {
                searchToggle.setImageDrawable(context.drawable(R.drawable.ic_close_24dp))
                toolbarTextInputView.textInput.run {
                    setText(savedInstanceState.searchQuery)
                    setSelection(text.length)
                }
                toolbarTextInputView.showTextInput()
            }
        }
        binding.root.setOnClickListener { mainActivity.openDetailScreen() }
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)
        outState?.isTextInputVisible = toolbarTextInputView.isTextInputVisible
        outState?.searchQuery = toolbarTextInputView.textInput.text.toString()
    }

    override fun inflateToolbarTitle(context: Context) = toolbarTextInputView

    override fun inflateToolbarButtons(context: Context) = listOf<View>(
        searchToggle,
        context.createToolbarButton(R.drawable.ic_view_options_24dp) { mainActivity.openSecondaryNavigationDrawer() }
    )

    override fun onBackPressed() = if (toolbarTextInputView.isTextInputVisible) {
        toggleTextInputVisibility()
        true
    } else false

    private fun toggleTextInputVisibility() {
        toolbarTextInputView.run {
            if (title.tag == null) {
                if (!isTextInputVisible) {
                    textInput.setText("")
                }
                searchToggle.setImageDrawable((if (toolbarTextInputView.isTextInputVisible) drawableCloseToSearch else drawableSearchToClose).apply { this?.start() })
                isTextInputVisible = !isTextInputVisible
            }
        }
    }
}