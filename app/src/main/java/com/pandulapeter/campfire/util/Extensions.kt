package com.pandulapeter.campfire.util

import android.animation.Animator
import android.content.Context
import android.databinding.BindingAdapter
import android.os.Bundle
import android.support.annotation.*
import android.support.graphics.drawable.AnimatedVectorDrawableCompat
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.support.v4.widget.DrawerLayout
import android.support.v7.content.res.AppCompatResources
import android.util.TypedValue
import android.view.View
import android.view.ViewAnimationUtils
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

fun Context.color(@ColorRes colorId: Int) = ContextCompat.getColor(this, colorId)

fun Context.dimension(@DimenRes dimensionId: Int) = resources.getDimensionPixelSize(dimensionId)

fun Context.drawable(@DrawableRes drawableId: Int) = AppCompatResources.getDrawable(this, drawableId)

fun Context.animatedDrawable(@DrawableRes drawableId: Int) = AnimatedVectorDrawableCompat.create(this, drawableId)

fun <T> MutableCollection<T>.swap(newItems: Collection<T>) {
    clear()
    addAll(newItems)
}

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
    get() = visibleOrGone
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

fun <T> Call<T>.enqueueCall(onSuccess: (T) -> Unit, onFailure: () -> Unit) {
    enqueue(object : Callback<T> {
        override fun onResponse(call: Call<T>?, response: Response<T>?) {
            if (response?.isSuccessful == true) response.body()?.let { onSuccess(it) } else onFailure()
        }

        override fun onFailure(call: Call<T>?, t: Throwable?) = onFailure()
    })
}

fun String.replaceSpecialCharacters() = this
    .replace("á", "a")
    .replace("Á", "A")
    .replace("ă", "a")
    .replace("Ă", "A")
    .replace("â", "a")
    .replace("Â", "A")
    .replace("é", "e")
    .replace("É", "E")
    .replace("í", "i")
    .replace("Í", "I")
    .replace("î", "i")
    .replace("Î", "I")
    .replace("ó", "o")
    .replace("Ó", "O")
    .replace("ö", "o")
    .replace("Ö", "O")
    .replace("ő", "o")
    .replace("Ő", "O")
    .replace("ș", "s")
    .replace("Ș", "S")
    .replace("ț", "t")
    .replace("Ț", "T")
    .replace("ú", "u")
    .replace("Ú", "U")
    .replace("ű", "u")
    .replace("Ű", "U")


inline fun <T : Fragment> T.withArguments(bundleOperations: (Bundle) -> Unit): T = apply {
    arguments = Bundle().apply { bundleOperations(this) }
}