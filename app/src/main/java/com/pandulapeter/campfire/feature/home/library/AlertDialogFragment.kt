package com.pandulapeter.campfire.feature.home.library

import android.app.Dialog
import android.os.Bundle
import android.support.annotation.StringRes
import android.support.v4.app.FragmentManager
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatDialogFragment

/**
 * Wrapper for [AlertDialog] with that handles state saving.
 */
class AlertDialogFragment : AppCompatDialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        context?.let { context ->
            arguments?.let { arguments ->
                return AlertDialog.Builder(context)
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
        //TODO: Remove duplicated code using delegation.
        private const val TITLE = "title"
        private const val MESSAGE = "message"
        private const val POSITIVE_BUTTON = "positiveButton"
        private const val NEGATIVE_BUTTON = "negativeButton"
        private var Bundle?.title
            get() = this?.getInt(TITLE) ?: 0
            set(value) {
                this?.putInt(TITLE, value)
            }
        private var Bundle?.message
            get() = this?.getInt(MESSAGE) ?: 0
            set(value) {
                this?.putInt(MESSAGE, value)
            }
        private var Bundle?.positiveButton
            get() = this?.getInt(POSITIVE_BUTTON) ?: 0
            set(value) {
                this?.putInt(POSITIVE_BUTTON, value)
            }
        private var Bundle?.negativeButton
            get() = this?.getInt(NEGATIVE_BUTTON) ?: 0
            set(value) {
                this?.putInt(NEGATIVE_BUTTON, value)
            }

        fun show(fragmentManager: FragmentManager,
                 @StringRes title: Int,
                 @StringRes message: Int,
                 @StringRes positiveButton: Int,
                 @StringRes negativeButton: Int) {
            AlertDialogFragment().apply {
                arguments = Bundle().apply {
                    this.title = title
                    this.message = message
                    this.positiveButton = positiveButton
                    this.negativeButton = negativeButton
                }
            }.let { it.show(fragmentManager, it.tag) }
        }
    }
}