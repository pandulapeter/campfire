package com.pandulapeter.campfire.feature.main.home.onboarding.page.contentLanguage

import android.support.v7.widget.AppCompatCheckBox
import android.view.ViewGroup
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.persistence.PreferenceDatabase
import com.pandulapeter.campfire.databinding.FragmentOnboardingContentLanguageBinding
import com.pandulapeter.campfire.feature.main.home.onboarding.OnboardingFragment
import com.pandulapeter.campfire.feature.main.home.onboarding.page.OnboardingPageFragment
import org.koin.android.ext.android.inject

class ContentLanguageFragment : OnboardingPageFragment<FragmentOnboardingContentLanguageBinding, ContentLanguageViewModel>(R.layout.fragment_onboarding_content_language) {

    override val viewModel = ContentLanguageViewModel {
        if (isAdded) {
            val totalLanguageCount = it.size
            binding.languageContainer.apply {
                removeAllViews()
                it.forEach {
                    addView(AppCompatCheckBox(getCampfireActivity()).apply {
                        setText(it.nameResource)
                        isChecked = !preferenceDatabase.disabledLanguageFilters.contains(it.id)
                        setOnCheckedChangeListener { _, _ ->
                            preferenceDatabase.disabledLanguageFilters =
                                    preferenceDatabase.disabledLanguageFilters.toMutableSet().apply { if (contains(it.id)) remove(it.id) else add(it.id) }
                            (parentFragment as OnboardingFragment).languageFiltersUpdated(totalLanguageCount - preferenceDatabase.disabledLanguageFilters.size)
                        }
                    }, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                }
            }
            (parentFragment as OnboardingFragment).languageFiltersUpdated(totalLanguageCount - preferenceDatabase.disabledLanguageFilters.size)
        }
    }
    private val preferenceDatabase by inject<PreferenceDatabase>()
}