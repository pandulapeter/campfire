package com.pandulapeter.campfire.presentation.utilities.extensions

import android.view.View
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.fragment.app.Fragment
import com.pandulapeter.campfire.presentation.utilities.AutoClearedValue

inline fun <reified B : ViewDataBinding> Fragment.bind(view: View? = null): B =
    DataBindingUtil.bind<B>(view ?: this.view ?: throw IllegalStateException("Fragment doesn't have a View."))?.apply {
        lifecycleOwner = viewLifecycleOwner
    } ?: throw IllegalStateException("No ViewDataBinding of instance: ${B::class} bound to the Fragment's View.")

fun <T : Any> Fragment.autoClearedValue() = AutoClearedValue<T>(this)