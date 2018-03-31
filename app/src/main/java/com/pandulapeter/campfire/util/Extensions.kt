package com.pandulapeter.campfire.util

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.databinding.BindingAdapter
import android.support.annotation.*
import android.support.v4.content.ContextCompat
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


var View.animatedVisibility: Boolean
    get() = visibility == View.VISIBLE
    @BindingAdapter("animatedVisibility")
    set(value) {
        if (isAttachedToWindow) {
            val cx = width
            val cy = height / 2
            val maxRadius = Math.hypot(cx.toDouble(), cy.toDouble()).toFloat()
            if (value) {
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