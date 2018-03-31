package com.pandulapeter.campfire.util

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.databinding.BindingAdapter
import android.support.annotation.*
import android.support.v4.content.ContextCompat
import android.support.v4.widget.DrawerLayout
import android.support.v7.content.res.AppCompatResources
import android.util.TypedValue
import android.view.View
import android.view.ViewAnimationUtils

fun Context.color(@ColorRes colorId: Int) = ContextCompat.getColor(this, colorId)

fun Context.dimension(@DimenRes dimensionId: Int) = resources.getDimensionPixelSize(dimensionId)

fun Context.drawable(@DrawableRes drawableId: Int) = AppCompatResources.getDrawable(this, drawableId)

@ColorInt
fun Context.obtainColor(@AttrRes colorAttribute: Int): Int {
    val attributes = obtainStyledAttributes(TypedValue().data, intArrayOf(colorAttribute))
    val color = attributes.getColor(0, 0)
    attributes.recycle()
    return color
}

var View.animatedVisibilityStart: Boolean
    get() = visibility == View.VISIBLE
    @BindingAdapter("animatedVisibilityStart")
    set(value) {
        animateCircularReveal(value, true)
    }

var View.animatedVisibilityEnd: Boolean
    get() = visibility == View.VISIBLE
    @BindingAdapter("animatedVisibilityEnd")
    set(value) {
        animateCircularReveal(value, false)
    }

private fun View.animateCircularReveal(isVisible: Boolean, start: Boolean) {
    if (isAttachedToWindow) {
        val cx = if (start) 0 else width
        val cy = height / 2
        val maxRadius = Math.hypot(cx.toDouble(), cy.toDouble()).toFloat()
        if (isVisible) {
            visibility = View.VISIBLE
            ViewAnimationUtils.createCircularReveal(this, cx, cy, 0f, maxRadius).start()
        } else {
            ViewAnimationUtils.createCircularReveal(this, cx, cy, maxRadius, 0f).apply {
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        super.onAnimationEnd(animation)
                        visibility = View.INVISIBLE
                    }
                })
            }.start()
        }
    }
}

fun DrawerLayout.addDrawerListener(
    onDrawerStateChanged: () -> Unit = {},
    onDrawerSlide: () -> Unit = {},
    onDrawerClosed: () -> Unit = {},
    onDrawerOpened: () -> Unit = {}
) = addDrawerListener(object : DrawerLayout.DrawerListener {
    override fun onDrawerStateChanged(newState: Int) = onDrawerStateChanged()

    override fun onDrawerSlide(drawerView: View, slideOffset: Float) = onDrawerSlide()

    override fun onDrawerClosed(drawerView: View) = onDrawerClosed()

    override fun onDrawerOpened(drawerView: View) = onDrawerOpened()
})