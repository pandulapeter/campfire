package com.pandulapeter.campfire.feature.shared

import android.databinding.DataBindingUtil
import android.databinding.ViewDataBinding
import android.os.Bundle
import android.support.annotation.LayoutRes
import com.pandulapeter.campfire.BR
import dagger.android.support.DaggerAppCompatActivity

/**
 * Base class for all Activities in the app. Handles layout inflation and setting up the view model.
 *
 * Controlled by subclasses of [CampfireViewModel].
 */
abstract class CampfireActivity<B : ViewDataBinding, out VM : CampfireViewModel>(@LayoutRes private val layoutResourceId: Int) : DaggerAppCompatActivity() {
    abstract protected val viewModel: VM
    protected lateinit var binding: B

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, layoutResourceId)
        binding.setVariable(BR.viewModel, viewModel)
    }
}