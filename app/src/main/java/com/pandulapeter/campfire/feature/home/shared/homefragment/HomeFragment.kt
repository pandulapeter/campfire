package com.pandulapeter.campfire.feature.home.shared.homefragment

import android.content.Context
import android.databinding.ViewDataBinding
import android.support.annotation.LayoutRes
import com.pandulapeter.campfire.feature.shared.CampfireFragment

/**
 * Parent class for Fragments that can be seen on the main screen.
 *
 * Controlled by subclasses of [HomeFragmentViewModel].
 */
abstract class HomeFragment<B : ViewDataBinding, out VM : HomeFragmentViewModel>(@LayoutRes layoutResourceId: Int) : CampfireFragment<B, VM>(layoutResourceId) {
    override val viewModel by lazy { createViewModel() }
    protected var callbacks: HomeCallbacks? = null

    abstract fun createViewModel(): VM

    override fun onAttach(context: Context?) {
        super.onAttach(context)
        if (context is HomeCallbacks) {
            callbacks = context
        }
    }

    open fun onBackPressed() = false

    interface HomeCallbacks {

        fun showMenu()
    }
}