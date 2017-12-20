package com.pandulapeter.campfire.feature.shared

import android.databinding.DataBindingUtil
import android.databinding.ViewDataBinding
import android.os.Bundle
import android.support.annotation.LayoutRes
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import com.pandulapeter.campfire.BR
import dagger.android.support.DaggerFragment

/**
 * Base class for all Fragments in the app. Handles layout inflation and setting up the view model.
 *
 * Controlled by subclasses of [CampfireViewModel].
 */
abstract class CampfireFragment<B : ViewDataBinding, out VM : CampfireViewModel>(@LayoutRes private val layoutResourceId: Int) : DaggerFragment() {
    abstract protected val viewModel: VM
    protected lateinit var binding: B
    var inAnimation: Animation? = null
    var outAnimation: Animation? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DataBindingUtil.inflate(inflater, layoutResourceId, container, false)
        binding.setVariable(BR.viewModel, viewModel)
        return binding.root
    }

    override fun onCreateAnimation(transit: Int, enter: Boolean, nextAnim: Int): Animation? = if (enter) inAnimation else outAnimation
}