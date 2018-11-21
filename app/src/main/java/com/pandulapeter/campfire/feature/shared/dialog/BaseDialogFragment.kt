package com.pandulapeter.campfire.feature.shared.dialog

import android.content.DialogInterface
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDialogFragment
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.feature.CampfireActivity

abstract class BaseDialogFragment : AppCompatDialogFragment() {

    protected val onDialogItemSelectedListener get() = parentFragment as? OnDialogItemSelectedListener ?: activity as? OnDialogItemSelectedListener

    override fun onCreateDialog(savedInstanceState: Bundle?) =
        context?.let {
            (activity as? CampfireActivity)?.isUiBlocked = true
            AlertDialog.Builder(it, R.style.DialogTheme).createDialog(arguments)
        } ?: super.onCreateDialog(savedInstanceState)

    abstract fun AlertDialog.Builder.createDialog(arguments: Bundle?): AlertDialog

    override fun onCancel(dialog: DialogInterface?) {
        (activity as? CampfireActivity)?.isUiBlocked = false
    }

    override fun onDismiss(dialog: DialogInterface?) {
        super.onDismiss(dialog)
        (activity as? CampfireActivity)?.isUiBlocked = false
    }

    interface OnDialogItemSelectedListener {

        fun onPositiveButtonSelected(id: Int)

        fun onNegativeButtonSelected(id: Int) = Unit
    }
}