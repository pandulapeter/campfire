package com.pandulapeter.campfire.feature.shared

import android.animation.Animator
import android.content.Context
import android.databinding.ViewDataBinding
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.Bundle
import android.support.annotation.*
import android.support.v4.app.FragmentPagerAdapter
import android.support.v7.widget.AppCompatTextView
import android.text.Spannable
import android.text.SpannableString
import android.text.style.LineBackgroundSpan
import android.text.style.ReplacementSpan
import android.text.style.TextAppearanceSpan
import android.view.Gravity
import android.view.View
import android.widget.TextView
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.feature.shared.widget.ToolbarButton
import com.pandulapeter.campfire.util.*
import kotlin.math.ceil

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

    open fun onNavigationItemSelected(menuItemId: Int) = false

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

    class EllipsizeLineSpan(@ColorInt private val color: Int? = null) : ReplacementSpan(), LineBackgroundSpan {
        private var layoutLeft = 0
        private var layoutRight = 0

        override fun drawBackground(
            canvas: Canvas,
            paint: Paint,
            left: Int,
            right: Int,
            top: Int,
            baseline: Int,
            bottom: Int,
            text: CharSequence,
            start: Int,
            end: Int,
            lnum: Int
        ) {
            val clipRect = Rect()
            canvas.getClipBounds(clipRect)
            layoutLeft = clipRect.left
            layoutRight = clipRect.right
        }

        override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fontMetricsInt: Paint.FontMetricsInt?): Int {
            fontMetricsInt?.let {
                it.ascent = paint.getFontMetricsInt(it)
                it.leading = paint.getFontMetricsInt(it)
            }
            return Math.round(paint.measureText(text, start, start))
        }

        override fun draw(canvas: Canvas, text: CharSequence, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
            color?.let { paint.color = it }
            val textWidth = paint.measureText(text, start, end)
            if (x + ceil(textWidth) < layoutRight) {
                canvas.drawText(text, start, end, x, y.toFloat(), paint)
            } else {
                val ellipsizeWidth = paint.measureText(ELLIPSIZE_CHARACTER)
                val newEnd = start + paint.breakText(text, start, end, true, layoutRight - x - ellipsizeWidth, null)
                canvas.drawText(text, start, newEnd, x, y.toFloat(), paint)
                canvas.drawText(ELLIPSIZE_CHARACTER, x + paint.measureText(text, start, newEnd), y.toFloat(), paint)
            }
        }

        companion object {
            private const val ELLIPSIZE_CHARACTER = "\u2026"
        }
    }
}