package com.pandulapeter.campfire.feature

import android.animation.Animator
import android.content.Context
import android.databinding.ViewDataBinding
import android.os.Bundle
import android.support.annotation.*
import android.support.v4.app.FragmentPagerAdapter
import android.support.v7.widget.AppCompatTextView
import android.text.Spannable
import android.text.SpannableString
import android.text.style.TextAppearanceSpan
import android.view.Gravity
import android.view.View
import android.widget.TextView
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.feature.shared.EllipsizeLineSpan
import com.pandulapeter.campfire.feature.shared.widget.ToolbarButton
import com.pandulapeter.campfire.util.*

abstract class TopLevelFragment<B : ViewDataBinding, out VM : CampfireViewModel>(@LayoutRes layoutResourceId: Int) : CampfireFragment<B, VM>(layoutResourceId) {

    protected open val onFloatingActionButtonClicked: (() -> Unit)? = null
    protected open val fragmentPagerAdapter: FragmentPagerAdapter? = null
    protected val defaultToolbar by lazy { AppCompatTextView(context).apply { gravity = Gravity.CENTER_VERTICAL } }
    @MenuRes
    protected open val navigationMenu: Int? = null

    @CallSuper
    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        mainActivity.changeToolbarTitle(inflateToolbarTitle(mainActivity.toolbarContext))
        mainActivity.changeToolbarButtons(inflateToolbarButtons(mainActivity.toolbarContext))
        mainActivity.tabLayout.visibleOrGone = fragmentPagerAdapter != null
        if (fragmentPagerAdapter == null) {
            mainActivity.tabLayout.setupWithViewPager(null)
        }
        mainActivity.floatingActionButton.setOnClickListener { onFloatingActionButtonClicked?.invoke() }
        mainActivity.setSecondaryNavigationDrawerEnabled(navigationMenu)
        if (onFloatingActionButtonClicked == null) {
            mainActivity.autoScrollControl.run {
                if (animatedVisibilityEnd) {
                    animatedVisibilityEnd = false
                    (tag as? Animator)?.let {
                        it.addListener(onAnimationEnd = {
                            mainActivity.floatingActionButton.hide()
                            tag = null
                            visibleOrGone = false

                        })
                    }
                } else {
                    mainActivity.floatingActionButton.hide()
                }
            }
        }
    }

    open fun onDrawerStateChanged(state: Int) = Unit

    protected open fun inflateToolbarTitle(context: Context): View = defaultToolbar

    protected open fun inflateToolbarButtons(context: Context): List<View> = listOf()

    protected inline fun Context.createToolbarButton(@DrawableRes drawableRes: Int, crossinline onClickListener: (View) -> Unit) = ToolbarButton(this).apply {
        setImageDrawable(drawable(drawableRes))
        setOnClickListener { onClickListener(it) }
    }

    protected fun TextView.updateToolbarTitle(@StringRes titleRes: Int, subtitle: String? = null) = updateToolbarTitle(context.getString(titleRes), subtitle)

    protected fun TextView.updateToolbarTitle(title: String, subtitle: String? = null) {
        mainActivity.toolbarContext.let { context ->
            text = SpannableString("$title${subtitle?.let { "\n$it" } ?: ""}").apply {
                setSpan(TextAppearanceSpan(context, R.style.TextAppearance_AppCompat_Title), 0, title.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(EllipsizeLineSpan(context.obtainColor(android.R.attr.textColorPrimary)), 0, title.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                subtitle?.let {
                    setSpan(EllipsizeLineSpan(context.obtainColor(android.R.attr.textColorSecondary)), title.length + 1, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        }
    }
}