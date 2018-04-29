package com.pandulapeter.campfire.feature.shared.dialog

import android.databinding.DataBindingUtil
import android.os.Bundle
import android.support.v4.app.FragmentManager
import android.support.v7.app.AlertDialog
import android.view.LayoutInflater
import com.pandulapeter.campfire.PrivacyConsentBinding
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.util.BundleArgumentDelegate
import com.pandulapeter.campfire.util.withArguments

class PrivacyConsentDialogFragment : BaseDialogFragment() {

    companion object {
        private var Bundle?.id by BundleArgumentDelegate.Int("id")

        fun show(id: Int, fragmentManager: FragmentManager) = PrivacyConsentDialogFragment().withArguments { it.id = id }.run {
            isCancelable = false
            show(fragmentManager, tag)
        }
    }

    private val binding by lazy { DataBindingUtil.inflate<PrivacyConsentBinding>(LayoutInflater.from(context), R.layout.dialog_privacy_consent, null, false) }

    override fun AlertDialog.Builder.createDialog(arguments: Bundle?): AlertDialog = setTitle(R.string.home_privacy_policy_title)
        .setView(binding.root)
        .setMessage(R.string.home_privacy_policy_message)
        .setPositiveButton(R.string.home_privacy_policy_positive, { _, _ ->
            if (binding.checkbox.isChecked) {
                onDialogItemSelectedListener?.onPositiveButtonSelected(arguments.id)
            } else {
                onDialogItemSelectedListener?.onNegativeButtonSelected(arguments.id)
            }
        })
        .create()
}