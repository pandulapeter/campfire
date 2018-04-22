package com.pandulapeter.campfire.feature.shared

import android.content.Context
import android.databinding.ViewDataBinding
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.Bundle
import android.support.annotation.*
import android.support.v7.widget.AppCompatTextView
import android.text.Spannable
import android.text.SpannableString
import android.text.style.LineBackgroundSpan
import android.text.style.ReplacementSpan
import android.text.style.TextAppearanceSpan
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.widget.CompoundButton
import android.widget.TextView
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.feature.detail.DetailFragment
import com.pandulapeter.campfire.feature.home.library.LibraryFragment
import com.pandulapeter.campfire.feature.shared.widget.ToolbarButton
import com.pandulapeter.campfire.util.consume
import com.pandulapeter.campfire.util.drawable
import com.pandulapeter.campfire.util.obtainColor
import kotlin.math.ceil

abstract class TopLevelFragment<B : ViewDataBinding, out VM : CampfireViewModel>(@LayoutRes layoutResourceId: Int) : CampfireFragment<B, VM>(layoutResourceId) {

    companion object {
        const val COMPOUND_BUTTON_TRANSITION_DELAY = 10L
    }

    protected val defaultToolbar by lazy { AppCompatTextView(context).apply { gravity = Gravity.CENTER_VERTICAL } }
    protected open val appBarView: View? = null
    protected var toolbarWidth = 0

    @CallSuper
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        mainActivity.beforeScreenChanged()
        if (this !is DetailFragment) {
            mainActivity.updateToolbarTitleView(inflateToolbarTitle(mainActivity.toolbarContext), toolbarWidth)
        }
        if (savedInstanceState == null || this !is LibraryFragment) {
            mainActivity.updateAppBarView(appBarView, savedInstanceState != null)
        }
    }

    open fun onDrawerStateChanged(state: Int) = Unit

    open fun onNavigationItemSelected(menuItem: MenuItem) = false

    open fun onFloatingActionButtonPressed() = Unit

    protected open fun inflateToolbarTitle(context: Context): View = defaultToolbar

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

    protected inline fun initializeCompoundButton(itemId: Int, crossinline getValue: () -> Boolean) = consume {
        mainActivity.secondaryNavigationMenu.findItem(itemId)?.let {
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


    protected fun CompoundButton?.updateCheckedStateWithDelay(checked: Boolean) {
        this?.postDelayed({ if (isAdded) isChecked = checked }, COMPOUND_BUTTON_TRANSITION_DELAY)
    }


    class EllipsizeLineSpan(@ColorInt private val color: Int? = null) : ReplacementSpan(), LineBackgroundSpan {

        companion object {
            private const val ELLIPSIZE_CHARACTER = "\u2026"
        }

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
    }
}