package com.pandulapeter.campfire.feature.home.shared.homefragment

import android.databinding.ViewDataBinding
import android.graphics.drawable.AnimatedVectorDrawable
import android.os.Bundle
import android.support.annotation.CallSuper
import android.support.annotation.LayoutRes
import android.view.View
import android.widget.ImageView
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.feature.home.HomeViewModel
import com.pandulapeter.campfire.feature.shared.CampfireFragment
import com.pandulapeter.campfire.util.onEventTriggered

/**
 * Parent class for Fragments that are part of the home screen.
 *
 * Controlled by subclasses of [HomeChildViewModel].
 */
abstract class HomeChildFragment<B : ViewDataBinding, out VM : HomeChildViewModel>(@LayoutRes layoutResourceId: Int) : CampfireFragment<B, VM>(layoutResourceId) {
    var shouldPlayReturnAnimation = false
    override val viewModel by lazy { createViewModel() }

    @CallSuper
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewModel.shouldShowMenu.onEventTriggered { (parentFragment as? HomeCallbacks)?.showMenu() }
        if (shouldPlayReturnAnimation) {
            viewModel.shouldPlayReturnAnimation.set(true)
        }
    }

    abstract fun createViewModel(): VM

    interface HomeCallbacks {

        fun showMenu()

        fun setCheckedItem(homeNavigationItem: HomeViewModel.HomeNavigationItem)
    }
}