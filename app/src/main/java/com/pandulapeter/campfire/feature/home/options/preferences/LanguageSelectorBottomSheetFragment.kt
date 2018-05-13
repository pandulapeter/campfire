package com.pandulapeter.campfire.feature.home.options.preferences

import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.support.v4.app.FragmentManager
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.FragmentLanguageSelectorBottomSheetBinding
import com.pandulapeter.campfire.feature.shared.dialog.BaseBottomSheetDialogFragment
import com.pandulapeter.campfire.util.BundleArgumentDelegate
import com.pandulapeter.campfire.util.withArguments

class LanguageSelectorBottomSheetFragment : BaseBottomSheetDialogFragment<FragmentLanguageSelectorBottomSheetBinding>(R.layout.fragment_language_selector_bottom_sheet) {

    companion object {
        private var Bundle?.selectedLanguageId by BundleArgumentDelegate.String("selectedLanguageId")

        fun show(fragmentManager: FragmentManager, selectedLanguageId: String) {
            LanguageSelectorBottomSheetFragment().withArguments { it.selectedLanguageId = selectedLanguageId }.run { (this as DialogFragment).show(fragmentManager, tag) }
        }
    }

    private val onLanguageSelectedListener get() = parentFragment as? OnLanguageSelectedListener

    override fun onDialogCreated() {
        when (PreferencesViewModel.Language.fromId(arguments?.selectedLanguageId ?: PreferencesViewModel.Language.AUTOMATIC.id)) {
            PreferencesViewModel.Language.AUTOMATIC -> binding.automatic
            PreferencesViewModel.Language.ENGLISH -> binding.english
        }.isChecked = true
        binding.automatic.setOnCheckedChangeListener { _, isChecked -> if (isChecked) onLanguageSelected(PreferencesViewModel.Language.AUTOMATIC) }
        binding.english.setOnCheckedChangeListener { _, isChecked -> if (isChecked) onLanguageSelected(PreferencesViewModel.Language.ENGLISH) }
        binding.root.apply { post { behavior.peekHeight = height } }
    }

    private fun onLanguageSelected(language: PreferencesViewModel.Language) {
        onLanguageSelectedListener?.onLanguageSelected(language)
        dismiss()
    }

    interface OnLanguageSelectedListener {

        fun onLanguageSelected(language: PreferencesViewModel.Language)
    }
}