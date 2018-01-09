package com.pandulapeter.campfire.feature.shared.dialog

import android.app.Dialog
import android.os.Bundle
import android.support.annotation.StringRes
import android.support.v4.app.DialogFragment
import android.support.v4.app.FragmentManager
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatDialogFragment
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.util.BundleArgumentDelegate
import com.pandulapeter.campfire.util.setArguments

/**
 * Wrapper for [AlertDialog] with that handles state saving.
 */
class AlertDialogFragment : AppCompatDialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        context?.let { context ->
            arguments?.let { arguments ->
                return AlertDialog.Builder(context, R.style.AlertDialog)
                    .setTitle(arguments.title)
                    .setMessage(arguments.message)
                    .setPositiveButton(arguments.positiveButton, { _, _ -> getOnDialogItemsSelectedListener()?.onPositiveButtonSelected() })
                    .setNegativeButton(arguments.negativeButton, null)
                    .create()
            }
        }
        return super.onCreateDialog(savedInstanceState)
    }

    private fun getOnDialogItemsSelectedListener(): OnDialogItemsSelectedListener? {
        parentFragment?.let {
            if (it is OnDialogItemsSelectedListener) {
                return it
            }
        }
        return null
    }

    interface OnDialogItemsSelectedListener {

        fun onPositiveButtonSelected()
    }

    companion object {
        private var Bundle?.title by BundleArgumentDelegate.Int("title")
        private var Bundle?.message by BundleArgumentDelegate.Int("message")
        private var Bundle?.positiveButton by BundleArgumentDelegate.Int("positiveButton")
        private var Bundle?.negativeButton by BundleArgumentDelegate.Int("negativeButton")

        fun show(fragmentManager: FragmentManager,
                 @StringRes title: Int,
                 @StringRes message: Int,
                 @StringRes positiveButton: Int,
                 @StringRes negativeButton: Int) {
            AlertDialogFragment().setArguments {
                it.title = title
                it.message = message
                it.positiveButton = positiveButton
                it.negativeButton = negativeButton
            }.run { (this as DialogFragment).show(fragmentManager, tag) }
        }
    }
}