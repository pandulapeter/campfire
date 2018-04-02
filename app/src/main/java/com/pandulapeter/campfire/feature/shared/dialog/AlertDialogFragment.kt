package com.pandulapeter.campfire.feature.shared.dialog

import android.app.Dialog
import android.os.Bundle
import android.support.annotation.StringRes
import android.support.v4.app.FragmentManager
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatDialogFragment
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.persistence.PreferenceDatabase
import com.pandulapeter.campfire.util.BundleArgumentDelegate
import com.pandulapeter.campfire.util.withArguments
import org.koin.android.ext.android.inject

class AlertDialogFragment : AppCompatDialogFragment() {

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
        }.run { show(fragmentManager, tag) }
    }

    private val preferenceDatabase by inject<PreferenceDatabase>()
    private val onDialogItemsSelectedListener get() = parentFragment as? OnDialogItemsSelectedListener ?: activity as? OnDialogItemsSelectedListener

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        context?.let { context ->
            arguments?.let { arguments ->
                return AlertDialog.Builder(context, if (preferenceDatabase.shouldUseDarkTheme) R.style.DarkAlertDialog else R.style.LightAlertDialog)
                    .setTitle(arguments.title)
                    .setMessage(arguments.message)
                    .setPositiveButton(arguments.positiveButton, { _, _ -> onDialogItemsSelectedListener?.onPositiveButtonSelected(arguments.id) })
                    .setNegativeButton(arguments.negativeButton, null)
                    .create()
            }
        }
        return super.onCreateDialog(savedInstanceState)
    }


    interface OnDialogItemsSelectedListener {

        fun onPositiveButtonSelected(id: Int)
    }
}