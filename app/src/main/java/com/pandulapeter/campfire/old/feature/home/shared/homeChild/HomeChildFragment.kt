package com.pandulapeter.campfire.old.feature.home.shared.homeChild

import android.databinding.ViewDataBinding
import android.os.Bundle
import android.support.annotation.CallSuper
import android.support.annotation.LayoutRes
import android.support.design.widget.AppBarLayout
import android.transition.Fade
import android.view.View
import com.pandulapeter.campfire.old.feature.home.HomeViewModel
import com.pandulapeter.campfire.old.feature.shared.CampfireFragment
import com.pandulapeter.campfire.old.util.onEventTriggered

/**
 * Parent class for Fragments that are part of the home screen.
 *
 * Controlled by subclasses of [HomeChildViewModel].
 */
abstract class HomeChildFragment<B : ViewDataBinding, out VM : HomeChildViewModel>(@LayoutRes layoutResourceId: Int) :
    CampfireFragment<B, VM>(layoutResourceId) {
    var shouldPlayReturnAnimation = false
    override val viewModel by lazy { createViewModel() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        exitTransition = Fade()
    }

    @CallSuper
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewModel.shouldShowMenu.onEventTriggered(this) { (parentFragment as? HomeCallbacks)?.showMenu() }
        if (shouldPlayReturnAnimation) {
            viewModel.shouldPlayReturnAnimation.set(true)
        }
    }

    protected abstract fun createViewModel(): VM

    protected abstract fun getAppBarLayout(): AppBarLayout

    interface HomeCallbacks {

        fun showMenu()

        fun setCheckedItem(homeNavigationItem: HomeViewModel.HomeNavigationItem)
    }
}