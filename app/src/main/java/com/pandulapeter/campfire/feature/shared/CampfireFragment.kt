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
    protected open val shouldDelaySubscribing = false
    protected val mainActivity get() = (activity as? CampfireActivity) ?: throw IllegalStateException("The Fragment is not attached to CampfireActivity.")
    private var snackbar: Snackbar? = null

    final override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        viewModel.componentCallbacks = this
        binding = DataBindingUtil.inflate(inflater, layoutResourceId, container, false)
        binding.setVariable(BR.viewModel, viewModel)
        binding.executePendingBindings()
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        if (shouldDelaySubscribing) {
            binding.root.postDelayed({
                if (isAdded) {
                    viewModel.subscribe()
                }
            }, 400)
        } else {
            viewModel.subscribe()
        }
    }

    override fun onPause() {
        super.onPause()
        snackbar?.dismiss()
        viewModel.unsubscribe()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewModel.componentCallbacks = null
    }

    open fun onBackPressed() = false

    protected fun showHint(@StringRes message: Int, action: () -> Unit) {
        snackbar?.dismiss()
        snackbar = mainActivity.snackbarRoot
            .makeSnackbar(getString(message), Snackbar.LENGTH_INDEFINITE)
            .apply { setAction(R.string.got_it, { action() }) }
        snackbar?.show()
    }

    protected fun showSnackbar(@StringRes message: Int, @StringRes actionText: Int = R.string.try_again, action: (() -> Unit)? = null, dismissAction: (() -> Unit)? = null) =
        showSnackbar(getString(message), actionText, action, dismissAction)

    protected fun showSnackbar(message: String, @StringRes actionText: Int = R.string.try_again, action: (() -> Unit)? = null, dismissAction: (() -> Unit)? = null) {
        snackbar?.dismiss()
        snackbar = mainActivity.snackbarRoot
            .makeSnackbar(message, if (action == null && dismissAction == null) Snackbar.LENGTH_SHORT else Snackbar.LENGTH_LONG, dismissAction)
            .apply { action?.let { setAction(actionText, { action() }) } }
        snackbar?.show()
    }

    private fun View.makeSnackbar(message: String, duration: Int, dismissAction: (() -> Unit)? = null) = Snackbar.make(this, message, duration).apply {
        view.setBackgroundColor(context.color(R.color.primary))
        dismissAction?.let {
            addCallback(object : Snackbar.Callback() {
                override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                    if (event != DISMISS_EVENT_ACTION && event != DISMISS_EVENT_CONSECUTIVE) {
                        it()
                    }
                }
            })
        }
    }
}