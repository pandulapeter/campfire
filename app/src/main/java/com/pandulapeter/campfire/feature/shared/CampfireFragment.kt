package com.pandulapeter.campfire.feature.shared

import android.databinding.DataBindingUtil
import android.databinding.ViewDataBinding
import android.os.Bundle
import android.support.annotation.CallSuper
import android.support.annotation.LayoutRes
import android.support.annotation.StringRes
import android.support.design.widget.Snackbar
import android.support.v4.app.Fragment
import android.transition.Transition
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.pandulapeter.campfire.BR
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.feature.CampfireActivity
import com.pandulapeter.campfire.integration.AnalyticsManager
import com.pandulapeter.campfire.util.color
import org.koin.android.ext.android.inject

abstract class CampfireFragment<B : ViewDataBinding, out VM : CampfireViewModel>(@LayoutRes private var layoutResourceId: Int) : Fragment(), Transition.TransitionListener {

    protected lateinit var binding: B
    protected abstract val viewModel: VM
    protected open val shouldDelaySubscribing = false
    protected val mainActivity get() = (activity as? CampfireActivity) ?: throw IllegalStateException("The Fragment is not attached to CampfireActivity.")
    protected val analyticsManager by inject<AnalyticsManager>()
    private var snackbar: Snackbar? = null
    private var isResumingDelayed = false

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
            isResumingDelayed = true
        } else {
            updateUI()
        }
    }

    override fun onPause() {
        super.onPause()
        isResumingDelayed = false
        snackbar?.dismiss()
        viewModel.unsubscribe()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewModel.componentCallbacks = null
    }

    override fun setReenterTransition(transition: Any?) {
        super.setReenterTransition(transition)
        (transition as? Transition)?.let {
            it.removeListener(this)
            it.addListener(this)
        }
    }

    @CallSuper
    open fun updateUI() = viewModel.subscribe()

    open fun onBackPressed() = false

    protected fun showHint(@StringRes message: Int, action: () -> Unit) {
        snackbar?.dismiss()
        snackbar = mainActivity.snackbarRoot
            .makeSnackbar(getString(message), Snackbar.LENGTH_INDEFINITE)
            .apply { setAction(R.string.got_it, { action() }) }
        snackbar?.show()
    }


    protected fun isSnackbarVisible() = snackbar?.isShownOrQueued ?: false

    fun hideSnackbar() = snackbar?.dismiss()

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

    @CallSuper
    override fun onTransitionEnd(transition: Transition?) {
        transition?.removeListener(this)
        if (isResumingDelayed) {
            updateUI()
            isResumingDelayed = false
        }
        enterTransition = null
        exitTransition = null
    }

    override fun onTransitionResume(transition: Transition?) = Unit

    override fun onTransitionPause(transition: Transition?) = Unit

    @CallSuper
    override fun onTransitionCancel(transition: Transition?) {
        transition?.removeListener(this)
        if (isResumingDelayed) {
            updateUI()
            isResumingDelayed = false
        }
        enterTransition = null
        exitTransition = null
    }

    override fun onTransitionStart(transition: Transition?) = Unit
}