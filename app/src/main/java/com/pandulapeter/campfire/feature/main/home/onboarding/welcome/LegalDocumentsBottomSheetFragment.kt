package com.pandulapeter.campfire.feature.main.home.onboarding.welcome

import android.net.Uri
import android.widget.TextView
import androidx.browser.customtabs.CustomTabsIntent
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.FragmentLegalDocumentsBottomSheetBinding
import com.pandulapeter.campfire.feature.CampfireActivity
import com.pandulapeter.campfire.feature.main.options.about.AboutViewModel
import com.pandulapeter.campfire.feature.shared.dialog.BaseBottomSheetDialogFragment
import com.pandulapeter.campfire.util.color

class LegalDocumentsBottomSheetFragment : BaseBottomSheetDialogFragment<FragmentLegalDocumentsBottomSheetBinding>(R.layout.fragment_legal_documents_bottom_sheet) {

    override fun onDialogCreated() {
        binding.termsAndConditions.openLinkOnClick(AboutViewModel.TERMS_AND_CONDITIONS_URL)
        binding.privacyPolicy.openLinkOnClick(AboutViewModel.PRIVACY_POLICY_URL)
        binding.openSourceLicenses.openLinkOnClick(AboutViewModel.OPEN_SOURCE_LICENSES_URL)
        binding.root.apply { post { behavior.peekHeight = height } }
    }

    private fun TextView.openLinkOnClick(url: String) = setOnClickListener {
        (activity as? CampfireActivity?)?.run {
            if (!isUiBlocked) {
                CustomTabsIntent.Builder()
                    .setToolbarColor(color(R.color.accent))
                    .build()
                    .launchUrl(this, Uri.parse(url))
                isUiBlocked = true
            }
        }
    }

    companion object {
        fun show(fragmentManager: androidx.fragment.app.FragmentManager) {
            LegalDocumentsBottomSheetFragment()
                .run { (this as androidx.fragment.app.DialogFragment).show(fragmentManager, tag) }
        }
    }
}