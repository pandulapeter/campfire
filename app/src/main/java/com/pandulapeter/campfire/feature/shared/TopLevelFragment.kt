package com.pandulapeter.campfire.feature.shared

import android.content.Context
import android.databinding.ViewDataBinding
import android.os.Bundle
import android.support.annotation.CallSuper
import android.support.annotation.DrawableRes
import android.support.annotation.LayoutRes
import android.support.annotation.StringRes
import android.support.v7.widget.AppCompatTextView
import android.support.v7.widget.RecyclerView
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.widget.CompoundButton
import android.widget.TextView
import com.pandulapeter.campfire.feature.detail.DetailFragment
import com.pandulapeter.campfire.feature.main.songs.SongsFragment
import com.pandulapeter.campfire.feature.shared.widget.ToolbarButton
import com.pandulapeter.campfire.util.consume
import com.pandulapeter.campfire.util.drawable


abstract class TopLevelFragment<B : ViewDataBinding, out VM : CampfireViewModel>(@LayoutRes layoutResourceId: Int) : CampfireFragment<B, VM>(layoutResourceId) {

    companion object {
        const val COMPOUND_BUTTON_TRANSITION_DELAY = 10L
    }

    protected val defaultToolbar by lazy { AppCompatTextView(context).apply { gravity = Gravity.CENTER_VERTICAL } }
    protected open val appBarView: View? = null
    protected var toolbarWidth = 0
    open val shouldShowAppBar = true

    @CallSuper
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        getCampfireActivity().onScreenChanged()
        if (this !is DetailFragment) {
            getCampfireActivity().updateToolbarTitleView(inflateToolbarTitle(getCampfireActivity().toolbarContext), toolbarWidth)
        }
        if (savedInstanceState == null || this !is SongsFragment) {
            getCampfireActivity().updateAppBarView(appBarView, savedInstanceState != null)
        }
    }

    open fun onDrawerStateChanged(state: Int) = Unit

    open fun onNavigationItemSelected(menuItem: MenuItem) = false

    open fun onFloatingActionButtonPressed() = Unit

    protected open fun inflateToolbarTitle(context: Context): View = defaultToolbar

    protected inline fun Context.createToolbarButton(@DrawableRes drawableRes: Int, crossinline onClickListener: (View) -> Unit) = ToolbarButton(this).apply {
        setImageDrawable(drawable(drawableRes))
        setOnClickListener { if (!getCampfireActivity().isUiBlocked) onClickListener(it) }
    }

    protected fun TextView.updateToolbarTitle(@StringRes titleRes: Int, subtitle: String? = null) = updateToolbarTitle(context.getString(titleRes), subtitle)

    protected fun TextView.updateToolbarTitle(title: String, subtitle: String? = null) = setTitleSubtitle(this, title, subtitle)

    protected fun initializeCompoundButton(itemId: Int, getValue: () -> Boolean) = consume {
        getCampfireActivity().secondaryNavigationMenu.findItem(itemId)?.let {
            (it.actionView as? CompoundButton)?.run {
                isChecked = getValue()
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked != getValue()) {
                        onNavigationItemSelected(it)
                    }
                }
            }
        }
    }

    protected fun RecyclerView.canScroll() = computeVerticalScrollRange() > height

    protected fun consumeAndUpdateBoolean(menuItem: MenuItem, setValue: (Boolean) -> Unit, getValue: () -> Boolean) = consume {
        setValue(!getValue())
        (menuItem.actionView as? CompoundButton).updateCheckedStateWithDelay(getValue())
    }

    protected fun CompoundButton?.updateCheckedStateWithDelay(checked: Boolean) {
        this?.postDelayed({ if (isAdded) isChecked = checked }, COMPOUND_BUTTON_TRANSITION_DELAY)
    }


}