package com.pandulapeter.campfire.feature.home.library

import android.app.Dialog
import android.os.Bundle
import android.support.annotation.StringRes
import android.support.v4.app.DialogFragment
import android.support.v4.app.FragmentManager
import android.support.v7.app.AlertDialog

/**
 * Wrapper for [AlertDialog] with that handles state saving.
 */
class AlertDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        context?.let { context ->
            arguments?.let { arguments ->
                return AlertDialog.Builder(context)
                    .setTitle(arguments.getInt(TITLE))
                    .setMessage(arguments.getInt(MESSAGE))
                    .setPositiveButton(arguments.getInt(POSITIVE_BUTTON), { _, _ -> getOnDialogItemsSelectedListener()?.onPositiveButtonSelected() })
                    .setNegativeButton(arguments.getInt(NEGATIVE_BUTTON), null)
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
        private const val TITLE = "title"
        private const val MESSAGE = "message"
        private const val POSITIVE_BUTTON = "positiveButton"
        private const val NEGATIVE_BUTTON = "negativeButton"

        fun show(fragmentManager: FragmentManager,
                 @StringRes title: Int,
                 @StringRes message: Int,
                 @StringRes positiveButton: Int,
                 @StringRes negativeButton: Int) {
            AlertDialogFragment().apply {
                arguments = Bundle().apply {
                    putInt(TITLE, title)
                    putInt(MESSAGE, message)
                    putInt(POSITIVE_BUTTON, positiveButton)
                    putInt(NEGATIVE_BUTTON, negativeButton)
                }
            }.let { it.show(fragmentManager, it.tag) }
        }
    }
}