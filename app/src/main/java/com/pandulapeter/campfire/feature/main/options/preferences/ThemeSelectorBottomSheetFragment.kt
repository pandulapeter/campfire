package com.pandulapeter.campfire.feature.main.options.preferences

import android.os.Bundle
import androidx.fragment.app.FragmentManager
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.FragmentThemeSelectorBottomSheetBinding
import com.pandulapeter.campfire.feature.shared.dialog.BaseBottomSheetDialogFragment
import com.pandulapeter.campfire.util.BundleArgumentDelegate
import com.pandulapeter.campfire.util.withArguments

class ThemeSelectorBottomSheetFragment : BaseBottomSheetDialogFragment<FragmentThemeSelectorBottomSheetBinding>(R.layout.fragment_theme_selector_bottom_sheet) {

    private val onThemeSelectedListener get() = parentFragment as? OnThemeSelectedListener

    override fun onDialogCreated() {
        when (PreferencesViewModel.Theme.fromId(arguments?.selectedThemeId ?: PreferencesViewModel.Theme.AUTOMATIC.id)) {
            PreferencesViewModel.Theme.AUTOMATIC -> binding.automatic
            PreferencesViewModel.Theme.DARK -> binding.dark
            PreferencesViewModel.Theme.LIGHT -> binding.light
        }.isChecked = true
        binding.automatic.setOnCheckedChangeListener { _, isChecked -> if (isChecked) onThemeSelected(PreferencesViewModel.Theme.AUTOMATIC) }
        binding.dark.setOnCheckedChangeListener { _, isChecked -> if (isChecked) onThemeSelected(PreferencesViewModel.Theme.DARK) }
        binding.light.setOnCheckedChangeListener { _, isChecked -> if (isChecked) onThemeSelected(PreferencesViewModel.Theme.LIGHT) }
        binding.root.apply { post { behavior.peekHeight = height } }
    }

    private fun onThemeSelected(theme: PreferencesViewModel.Theme) {
        dismiss()
        onThemeSelectedListener?.onThemeSelected(theme)
    }

    interface OnThemeSelectedListener {

        fun onThemeSelected(theme: PreferencesViewModel.Theme)
    }

    companion object {

        private var Bundle?.selectedThemeId by BundleArgumentDelegate.Int("selectedThemeId")

        fun show(fragmentManager: FragmentManager, selectedThemeId: Int) =
            ThemeSelectorBottomSheetFragment().withArguments { it.selectedThemeId = selectedThemeId }.run { show(fragmentManager, tag) }
    }
}