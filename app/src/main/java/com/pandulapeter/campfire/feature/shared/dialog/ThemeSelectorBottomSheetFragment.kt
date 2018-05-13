package com.pandulapeter.campfire.feature.shared.dialog

import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.support.v4.app.FragmentManager
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.FragmentThemeSelectorBottomSheetBinding
import com.pandulapeter.campfire.feature.home.options.preferences.PreferencesViewModel
import com.pandulapeter.campfire.util.BundleArgumentDelegate
import com.pandulapeter.campfire.util.withArguments

class ThemeSelectorBottomSheetFragment : BaseBottomSheetDialogFragment<FragmentThemeSelectorBottomSheetBinding>(R.layout.fragment_theme_selector_bottom_sheet) {

    companion object {
        private var Bundle?.selectedThemeId by BundleArgumentDelegate.Int("selectedThemeId")

        fun show(fragmentManager: FragmentManager, selectedThemeId: Int) {
            ThemeSelectorBottomSheetFragment().withArguments { it.selectedThemeId = selectedThemeId }.run { (this as DialogFragment).show(fragmentManager, tag) }
        }
    }

    private val onThemeSelectedListener get() = parentFragment as? OnThemeSelectedListener

    override fun onDialogCreated() {
        binding.system.setOnClickListener { onThemeSelected(PreferencesViewModel.Theme.SYSTEM) }
        binding.automatic.setOnClickListener { onThemeSelected(PreferencesViewModel.Theme.AUTOMATIC) }
        binding.dark.setOnClickListener { onThemeSelected(PreferencesViewModel.Theme.DARK) }
        binding.light.setOnClickListener { onThemeSelected(PreferencesViewModel.Theme.LIGHT) }
    }

    private fun onThemeSelected(theme: PreferencesViewModel.Theme) {
        onThemeSelectedListener?.onThemeSelected(theme)
        dismiss()
    }

    interface OnThemeSelectedListener {

        fun onThemeSelected(theme: PreferencesViewModel.Theme)
    }
}