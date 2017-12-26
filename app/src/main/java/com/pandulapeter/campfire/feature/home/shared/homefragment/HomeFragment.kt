package com.pandulapeter.campfire.feature.home.shared.homefragment

import android.databinding.ViewDataBinding
import android.os.Bundle
import android.support.annotation.CallSuper
import android.support.annotation.LayoutRes
import android.view.View
import com.pandulapeter.campfire.feature.shared.CampfireFragment
import com.pandulapeter.campfire.util.onEventTriggered

/**
 * Parent class for Fragments that can be seen on the main screen.
 *
 * Controlled by subclasses of [HomeFragmentViewModel].
 */
abstract class HomeFragment<B : ViewDataBinding, out VM : HomeFragmentViewModel>(@LayoutRes layoutResourceId: Int) : CampfireFragment<B, VM>(layoutResourceId) {
    override val viewModel by lazy { createViewModel() }

    @CallSuper
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewModel.shouldShowMenu.onEventTriggered { (parentFragment as? HomeCallbacks)?.showMenu() }
    }

    abstract fun createViewModel(): VM

    open fun onBackPressed() = false

    interface HomeCallbacks {

        fun showMenu()
    }
}