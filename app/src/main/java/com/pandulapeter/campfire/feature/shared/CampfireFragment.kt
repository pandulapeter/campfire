package com.pandulapeter.campfire.feature.shared

import android.databinding.DataBindingUtil
import android.databinding.ViewDataBinding
import android.os.Bundle
import android.support.annotation.LayoutRes
import android.support.annotation.StringRes
import android.support.design.widget.Snackbar
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.pandulapeter.campfire.BR
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.feature.CampfireActivity
import com.pandulapeter.campfire.util.color

abstract class CampfireFragment<B : ViewDataBinding, out VM : CampfireViewModel>(@LayoutRes private var layoutResourceId: Int) : Fragment() {

    protected lateinit var binding: B
    protected abstract val viewModel: VM
    protected val mainActivity get() = (activity as? CampfireActivity) ?: throw IllegalStateException("The Fragment is not attached to CampfireActivity.")

    final override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        viewModel.componentCallbacks = this
        binding = DataBindingUtil.inflate(inflater, layoutResourceId, container, false)
        binding.setVariable(BR.viewModel, viewModel)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewModel.componentCallbacks = null
    }

    open fun onBackPressed() = false

    protected fun showSnackbar(@StringRes message: Int, retryAction: View.OnClickListener? = null) = showSnackbar(getString(message), retryAction)

    protected fun showSnackbar(message: String, retryAction: View.OnClickListener? = null) = binding.root
        .makeSnackbar(message, if (retryAction == null) Snackbar.LENGTH_SHORT else Snackbar.LENGTH_LONG)
        .apply { retryAction?.let { setAction(R.string.try_again, it) } }
        .show()

    private fun View.makeSnackbar(message: String, duration: Int, dismissListener: (() -> Unit)? = null) = Snackbar.make(this, message, duration).apply {
        view.setBackgroundColor(context.color(R.color.primary))
        dismissListener?.let {
            addCallback(object : Snackbar.Callback() {
                override fun onDismissed(transientBottomBar: Snackbar?, event: Int) = it()
            })
        }
    }
}