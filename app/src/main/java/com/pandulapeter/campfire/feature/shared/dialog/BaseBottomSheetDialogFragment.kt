package com.pandulapeter.campfire.feature.shared.dialog

import android.content.Context
import android.content.DialogInterface
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.annotation.StyleRes
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.feature.shared.CampfireFragment
import com.pandulapeter.campfire.feature.shared.InteractionBlocker
import com.pandulapeter.campfire.util.color
import com.pandulapeter.campfire.util.dimension
import org.koin.android.ext.android.inject

abstract class BaseBottomSheetDialogFragment<B : ViewDataBinding>(@LayoutRes private val layoutResourceId: Int) : AppCompatDialogFragment() {

    protected lateinit var binding: B
    protected val behavior: BottomSheetBehavior<*> by lazy { ((binding.root.parent as View).layoutParams as CoordinatorLayout.LayoutParams).behavior as BottomSheetBehavior<*> }
    protected val isFullWidth get() = (dialog as CustomWidthBottomSheetDialog).isFullWidth
    private val interactionBlocker by inject<InteractionBlocker>()

    open fun initializeDialog(context: Context, savedInstanceState: Bundle?) = Unit

    abstract fun onDialogCreated()

    open fun updateSystemWindows() {
        if (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_NO) {
            dialog?.window?.decorView?.systemUiVisibility = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context?.color(R.color.panel_primary)?.let { color -> dialog?.window?.navigationBarColor = color }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?) = context?.let { context ->
        interactionBlocker.isUiBlocked = true
        (parentFragment as? CampfireFragment<*, *>)?.onDialogOpened()
        CustomWidthBottomSheetDialog(context, R.style.BottomSheetDialog).apply {
            binding = DataBindingUtil.inflate(LayoutInflater.from(context), layoutResourceId, null, false)
            binding.setLifecycleOwner(this@BaseBottomSheetDialogFragment)
            initializeDialog(context, savedInstanceState)
            setContentView(binding.root)
            onDialogCreated()
        }
    } ?: super.onCreateDialog(savedInstanceState)

    override fun onStart() {
        super.onStart()
        updateSystemWindows()
    }

    override fun onCancel(dialog: DialogInterface) {
        (parentFragment as? CampfireFragment<*, *>)?.onDialogDismissed()
        interactionBlocker.isUiBlocked = false
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        (parentFragment as? CampfireFragment<*, *>)?.onDialogDismissed()
        interactionBlocker.isUiBlocked = false
    }

    protected inline fun <T> MutableLiveData<T?>.observeAndReset(crossinline callback: (T) -> Unit) = observe(this@BaseBottomSheetDialogFragment, Observer {
        if (it != null) {
            callback(it)
            value = null
        }
    })

    private class CustomWidthBottomSheetDialog(context: Context, @StyleRes theme: Int) : BottomSheetDialog(context, theme) {

        private val width = context.dimension(R.dimen.bottom_sheet_width)
        val isFullWidth = width == 0

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            if (!isFullWidth) {
                window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
                window?.setGravity(Gravity.BOTTOM)
            }
        }
    }
}