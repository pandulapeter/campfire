package com.pandulapeter.campfire.util

import android.animation.Animator
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

@set:BindingAdapter("visibility")
var View.visibleOrGone
    get() = visibility == View.VISIBLE
    set(value) {
        visibility = if (value) View.VISIBLE else View.GONE
    }

@set:BindingAdapter("invisibility")
var View.visibleOrInvisible
    get() = visibility == View.VISIBLE
    set(value) {
        visibility = if (value) View.VISIBLE else View.INVISIBLE
    }

@set:BindingAdapter("animatedVisibilityStart")
var View.animatedVisibilityStart: Boolean
    get() = visibility == View.VISIBLE
    set(value) {
        animateCircularReveal(value, true)
    }

@set:BindingAdapter("animatedVisibilityEnd")
var View.animatedVisibilityEnd: Boolean
    get() = visibleOrGone
    set(value) {
        animateCircularReveal(value, false)
    }

private fun View.animateCircularReveal(isVisible: Boolean, start: Boolean) {
    if (isAttachedToWindow) {
        val cx = if (start) 0 else width
        val cy = height / 2
        val maxRadius = Math.hypot(width.toDouble(), height.toDouble()).toFloat()
        visibleOrGone = true
        val animator = ViewAnimationUtils.createCircularReveal(this, cx, cy, if (isVisible) 0f else maxRadius, if (isVisible) maxRadius else 0f).apply {
            addListener(onAnimationEnd = {
                visibleOrGone = isVisible
                tag = null
            })
        }
        tag = animator
        animator.start()
    }
}

fun DrawerLayout.addDrawerListener(
    onDrawerStateChanged: (newState: Int) -> Unit = {},
    onDrawerSlide: () -> Unit = {},
    onDrawerClosed: () -> Unit = {},
    onDrawerOpened: () -> Unit = {}
) = addDrawerListener(object : DrawerLayout.DrawerListener {
    override fun onDrawerStateChanged(newState: Int) = onDrawerStateChanged(newState)

    override fun onDrawerSlide(drawerView: View, slideOffset: Float) = onDrawerSlide()

    override fun onDrawerClosed(drawerView: View) = onDrawerClosed()

    override fun onDrawerOpened(drawerView: View) = onDrawerOpened()
})

fun Animator.addListener(
    onAnimationRepeat: () -> Unit = {},
    onAnimationEnd: () -> Unit = {},
    onAnimationCancel: () -> Unit = {},
    onAnimationStart: () -> Unit = {}
) = addListener(object : Animator.AnimatorListener {
    override fun onAnimationRepeat(animation: Animator?) = onAnimationRepeat()

    override fun onAnimationEnd(animation: Animator?) = onAnimationEnd()

    override fun onAnimationCancel(animation: Animator?) = onAnimationCancel()

    override fun onAnimationStart(animation: Animator?) = onAnimationStart()
})