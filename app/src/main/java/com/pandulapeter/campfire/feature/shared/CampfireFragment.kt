package com.pandulapeter.campfire.feature.shared

import android.content.res.Resources
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
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.TextView
import com.pandulapeter.campfire.BR
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.feature.CampfireActivity
import com.pandulapeter.campfire.integration.AnalyticsManager
import com.pandulapeter.campfire.util.color
import com.pandulapeter.campfire.util.hideKeyboard
import com.pandulapeter.campfire.util.obtainColor
import org.koin.android.ext.android.inject


abstract class CampfireFragment<B : ViewDataBinding, out VM : CampfireViewModel>(@LayoutRes private var layoutResourceId: Int) : Fragment(), Transition.TransitionListener {

    companion object {
        private const val SNACKBAR_SHORT_DURATION = 4000
        private const val SNACKBAR_LONG_DURATION = 7000
    }

    protected lateinit var binding: B
    protected abstract val viewModel: VM
    protected open val shouldDelaySubscribing = false
    protected val analyticsManager by inject<AnalyticsManager>()
    private var snackbar: Snackbar? = null
    private var isResumingDelayed = false
    private val snackbarBackgroundColor by lazy { getCampfireActivity().obtainColor(android.R.attr.textColorPrimary) }
    private val snackbarTextColor by lazy { getCampfireActivity().obtainColor(android.R.attr.colorPrimary) }
    private val snackbarActionTextColor by lazy { getCampfireActivity().color(R.color.accent) }

    final override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        viewModel.componentCallbacks = this
        binding = DataBindingUtil.inflate(inflater, layoutResourceId, container, false)
        binding.setVariable(BR.viewModel, viewModel)
        binding.executePendingBindings()
        return binding.root
    }

    override fun onCreateAnimation(transit: Int, enter: Boolean, nextAnim: Int): Animation? {
        val parent = parentFragment
        return if (!enter && parent != null && parent.isRemoving) {
            // This is a workaround for the bug where child fragments disappear when
            // the parent is removed (as all children are first removed from the parent)
            // See https://code.google.com/p/android/issues/detail?id=55228
            AlphaAnimation(1f, 1f).apply { duration = getNextAnimationDuration(parent, 300L) }
        } else {
            super.onCreateAnimation(transit, enter, nextAnim)
        }
    }

    private fun getNextAnimationDuration(fragment: Fragment, defValue: Long): Long {
        try {
            val animInfoField = Fragment::class.java.getDeclaredField("mAnimationInfo")
            animInfoField.isAccessible = true
            val animationInfo = animInfoField.get(fragment)
            val nextAnimField = animationInfo.javaClass.getDeclaredField("mNextAnim")
            nextAnimField.isAccessible = true
            val nextAnimResource = nextAnimField.getInt(animationInfo)
            val nextAnim = AnimationUtils.loadAnimation(fragment.activity, nextAnimResource)
            return nextAnim?.duration ?: defValue
        } catch (ex: NoSuchFieldException) {
            return defValue
        } catch (ex: IllegalAccessException) {
            return defValue
        } catch (ex: Resources.NotFoundException) {
            return defValue
        }
    }

    override fun onStart() {
        super.onStart()
        if (shouldDelaySubscribing) {
            isResumingDelayed = true
        } else {
            updateUI()
        }
    }

    override fun onStop() {
        super.onStop()
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

    @Suppress("NOTHING_TO_INLINE") // Needed for ProGuard
    protected inline fun getCampfireActivity() = (activity as? CampfireActivity) ?: throw IllegalStateException("The Fragment is not attached to CampfireActivity.")

    protected fun showHint(@StringRes message: Int, action: () -> Unit) {
        snackbar?.dismiss()
        snackbar = getCampfireActivity().snackbarRoot
            .makeSnackbar(getString(message), Snackbar.LENGTH_INDEFINITE)
            .apply { setAction(R.string.got_it) { action() } }
        snackbar?.show()
    }

    protected fun isSnackbarVisible() = snackbar?.isShownOrQueued ?: false

    fun hideSnackbar() = snackbar?.dismiss()

    fun showSnackbar(@StringRes message: Int, @StringRes actionText: Int = R.string.try_again, action: (() -> Unit)? = null, dismissAction: (() -> Unit)? = null) =
        showSnackbar(getString(message), actionText, action, dismissAction)

    protected fun showSnackbar(message: String, @StringRes actionText: Int = R.string.try_again, action: (() -> Unit)? = null, dismissAction: (() -> Unit)? = null) {
        snackbar = getCampfireActivity().snackbarRoot
            .makeSnackbar(message, if (action == null && dismissAction == null) SNACKBAR_SHORT_DURATION else SNACKBAR_LONG_DURATION, dismissAction)
            .apply { action?.let { setAction(actionText) { action() } } }
        snackbar?.show()
    }

    private fun View.makeSnackbar(message: String, duration: Int, dismissAction: (() -> Unit)? = null) = Snackbar.make(this, message, duration).apply {
        view.setBackgroundColor(snackbarBackgroundColor)
        view.findViewById<TextView>(android.support.design.R.id.snackbar_text).setTextColor(snackbarTextColor)
        setActionTextColor(snackbarActionTextColor)
        getCampfireActivity().currentFocus?.let { hideKeyboard(it) }
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