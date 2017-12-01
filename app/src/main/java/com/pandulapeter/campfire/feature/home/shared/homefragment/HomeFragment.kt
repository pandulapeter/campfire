package com.pandulapeter.campfire.feature.home.shared.homefragment

import android.content.Context
import android.databinding.ViewDataBinding
import android.support.annotation.LayoutRes
import com.pandulapeter.campfire.data.repository.SongInfoRepository
import com.pandulapeter.campfire.feature.shared.CampfireFragment
import javax.inject.Inject

/**
 * Parent class for Fragments that can be seen on the main screen. Handles common operations related to a song list.
 *
 * Controlled by subclasses of [HomeFragmentViewModel].
 */
abstract class HomeFragment<B : ViewDataBinding, out VM : HomeFragmentViewModel>(@LayoutRes layoutResourceId: Int) : CampfireFragment<B, VM>(layoutResourceId) {

    override val viewModel by lazy { createViewModel() }
    @Inject lateinit var songInfoRepository: SongInfoRepository
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