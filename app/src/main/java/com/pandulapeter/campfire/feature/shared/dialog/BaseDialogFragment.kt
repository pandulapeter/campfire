package com.pandulapeter.campfire.feature.shared.dialog

import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatDialogFragment
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.persistence.PreferenceDatabase
import org.koin.android.ext.android.inject

abstract class BaseDialogFragment : AppCompatDialogFragment() {

    protected val onDialogItemSelectedListener get() = parentFragment as? OnDialogItemSelectedListener ?: activity as? OnDialogItemSelectedListener
    private val preferenceDatabase by inject<PreferenceDatabase>()

    override fun onCreateDialog(savedInstanceState: Bundle?) = context?.let {
        AlertDialog.Builder(it, R.style.AlertDialogTheme).createDialog(arguments)
    } ?: super.onCreateDialog(savedInstanceState)

    abstract fun AlertDialog.Builder.createDialog(arguments: Bundle?): AlertDialog

    interface OnDialogItemSelectedListener {

        fun onPositiveButtonSelected(id: Int)

        fun onNegativeButtonSelected(id: Int) = Unit
    }
}