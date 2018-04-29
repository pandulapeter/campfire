package com.pandulapeter.campfire.feature.shared.dialog

import android.os.Bundle
import android.support.annotation.StringRes
import android.support.v4.app.FragmentManager
import android.support.v7.app.AlertDialog
import com.pandulapeter.campfire.util.BundleArgumentDelegate
import com.pandulapeter.campfire.util.withArguments

class AlertDialogFragment : BaseDialogFragment() {

    companion object {
        private var Bundle?.id by BundleArgumentDelegate.Int("id")
        private var Bundle?.title by BundleArgumentDelegate.Int("title")
        private var Bundle?.message by BundleArgumentDelegate.Int("message")
        private var Bundle?.positiveButton by BundleArgumentDelegate.Int("positiveButton")
        private var Bundle?.negativeButton by BundleArgumentDelegate.Int("negativeButton")

        fun show(
            id: Int,
            fragmentManager: FragmentManager,
            @StringRes title: Int,
            @StringRes message: Int,
            @StringRes positiveButton: Int,
            @StringRes negativeButton: Int
        ) = AlertDialogFragment().withArguments {
            it.id = id
            it.title = title
            it.message = message
            it.positiveButton = positiveButton
            it.negativeButton = negativeButton
        }.run {
            show(fragmentManager, tag)
        }
    }

    override fun AlertDialog.Builder.createDialog(arguments: Bundle?): AlertDialog = setTitle(arguments.title)
        .setMessage(arguments.message)
        .setPositiveButton(arguments.positiveButton, { _, _ -> onDialogItemSelectedListener?.onPositiveButtonSelected(arguments.id) })
        .setNegativeButton(arguments.negativeButton, { _, _ -> onDialogItemSelectedListener?.onNegativeButtonSelected(arguments.id) })
        .create()
}