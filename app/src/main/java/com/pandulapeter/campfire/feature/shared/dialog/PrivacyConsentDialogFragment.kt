package com.pandulapeter.campfire.feature.shared.dialog

import android.databinding.DataBindingUtil
import android.net.Uri
import android.os.Bundle
import android.support.customtabs.CustomTabsIntent
import android.support.v4.app.FragmentManager
import android.support.v7.app.AlertDialog
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.LayoutInflater
import android.view.View
import com.pandulapeter.campfire.PrivacyConsentBinding
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.feature.home.options.about.AboutViewModel
import com.pandulapeter.campfire.util.BundleArgumentDelegate
import com.pandulapeter.campfire.util.color
import com.pandulapeter.campfire.util.withArguments

class PrivacyConsentDialogFragment : BaseDialogFragment() {

    companion object {
        private var Bundle?.id by BundleArgumentDelegate.Int("id")

        fun show(id: Int, fragmentManager: FragmentManager) = PrivacyConsentDialogFragment().withArguments { it.id = id }.run {
            isCancelable = false
            show(fragmentManager, tag)
        }
    }

    private val binding by lazy {
        DataBindingUtil.inflate<PrivacyConsentBinding>(LayoutInflater.from(context), R.layout.dialog_privacy_consent, null, false).apply {
            val first = getString(R.string.home_privacy_policy_message_end_part_1)
            val second = getString(R.string.home_privacy_policy_message_end_part_2)
            val third = getString(R.string.home_privacy_policy_message_end_part_3)
            textEnd.movementMethod = LinkMovementMethod.getInstance()
            textEnd.text = SpannableString("$first$second$third").apply {
                setSpan(object : ClickableSpan() {
                    override fun onClick(widget: View?) {
                        CustomTabsIntent.Builder()
                            .setToolbarColor(requireContext().color(R.color.accent))
                            .build()
                            .launchUrl(requireContext(), Uri.parse(AboutViewModel.PRIVACY_POLICY_URL))
                    }
                }, first.length, length - third.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }

    override fun AlertDialog.Builder.createDialog(arguments: Bundle?): AlertDialog = setView(binding.root)
        .setPositiveButton(R.string.home_privacy_policy_positive, { _, _ ->
            if (binding.checkboxAnalytics.isChecked) {
                onDialogItemSelectedListener?.onPositiveButtonSelected(arguments.id)
            } else {
                onDialogItemSelectedListener?.onNegativeButtonSelected(arguments.id)
            }
        })
        .create()
}