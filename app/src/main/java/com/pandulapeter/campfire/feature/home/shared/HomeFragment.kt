package com.pandulapeter.campfire.feature.home.shared

import android.content.Context
import android.databinding.DataBindingUtil
import android.databinding.ViewDataBinding
import android.os.Bundle
import android.support.annotation.LayoutRes
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.pandulapeter.campfire.BR
import com.pandulapeter.campfire.data.repository.SongInfoRepository
import dagger.android.support.DaggerFragment
import javax.inject.Inject

/**
 * Parent class for Fragments that can be seen on the main screen.
 *
 * Controlled by subclasses of [HomeFragmentViewModel].
 */
abstract class HomeFragment<B : ViewDataBinding, out VM : HomeFragmentViewModel>(@LayoutRes private val layoutResourceId: Int) : DaggerFragment() {

    @Inject lateinit var songInfoRepository: SongInfoRepository
    abstract protected val viewModel: VM
    protected lateinit var binding: B
    protected var callbacks: HomeCallbacks? = null

    override fun onAttach(context: Context?) {
        super.onAttach(context)
        if (context is HomeCallbacks) {
            callbacks = context
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DataBindingUtil.inflate(inflater, layoutResourceId, container, false)
        binding.setVariable(BR.viewModel, viewModel)
        return binding.root
    }

    abstract fun isSearchInputVisible(): Boolean

    abstract fun closeSearchInput()

    interface HomeCallbacks {
        fun showViewOptions()
    }
}