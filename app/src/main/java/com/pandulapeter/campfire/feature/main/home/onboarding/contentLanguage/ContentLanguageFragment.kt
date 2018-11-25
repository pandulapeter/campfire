package com.pandulapeter.campfire.feature.main.home.onboarding.contentLanguage

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatCheckBox
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.model.local.Language
import com.pandulapeter.campfire.data.persistence.PreferenceDatabase
import com.pandulapeter.campfire.databinding.FragmentOnboardingContentLanguageBinding
import com.pandulapeter.campfire.feature.main.home.onboarding.OnboardingFragment
import com.pandulapeter.campfire.feature.main.home.onboarding.OnboardingPageFragment
import com.pandulapeter.campfire.util.dimension
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class ContentLanguageFragment : OnboardingPageFragment<FragmentOnboardingContentLanguageBinding, ContentLanguageViewModel>(R.layout.fragment_onboarding_content_language) {

    override val viewModel by viewModel<ContentLanguageViewModel>()
    private val preferenceDatabase by inject<PreferenceDatabase>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.languages.observe { onLanguagesLoaded(it.orEmpty()) }
    }

    private fun onLanguagesLoaded(languages: List<Language>) {
        viewModel.selectedLanguageCount = 0
        binding.languageContainer.apply {
            removeAllViews()
            val contentPadding = context.dimension(R.dimen.content_padding)
            languages.forEach {
                addView(AppCompatCheckBox(getCampfireActivity()).apply {
                    setText(it.nameResource)
                    setPadding(contentPadding, 0, 0, 0)
                    isChecked = !preferenceDatabase.disabledLanguageFilters.contains(it.id)
                    if (isChecked) {
                        viewModel.selectedLanguageCount++
                    }
                    setOnCheckedChangeListener { _, isChecked ->
                        preferenceDatabase.disabledLanguageFilters =
                                preferenceDatabase.disabledLanguageFilters.toMutableSet().apply { if (contains(it.id)) remove(it.id) else add(it.id) }
                        if (isChecked) {
                            viewModel.selectedLanguageCount++
                        } else {
                            viewModel.selectedLanguageCount--
                        }
                        onLanguageFiltersUpdated()
                    }
                }, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
        }
        onLanguageFiltersUpdated()
    }

    private fun onLanguageFiltersUpdated() {
        (parentFragment as OnboardingFragment).languageFiltersUpdated(viewModel.selectedLanguageCount)
        viewModel.shouldShowError.value = viewModel.selectedLanguageCount == 0
    }

    companion object {

        fun newInstance() = ContentLanguageFragment()
    }
}