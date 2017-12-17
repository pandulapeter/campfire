package com.pandulapeter.campfire.feature.shared

import android.databinding.BindingAdapter
import android.support.annotation.DrawableRes
import android.support.design.widget.FloatingActionButton
import android.view.View
import android.widget.TextView
import com.pandulapeter.campfire.util.drawable

@BindingAdapter(value = ["android:drawableStart", "android:drawableTop", "android:drawableEnd", "android:drawableBottom"], requireAll = false)
fun setCompoundDrawables(view: TextView,
                         @DrawableRes drawableStart: Int,
                         @DrawableRes drawableTop: Int,
                         @DrawableRes drawableEnd: Int,
                         @DrawableRes drawableBottom: Int) {
    val drawables = view.compoundDrawables
    view.setCompoundDrawablesWithIntrinsicBounds(
        if (drawableStart == 0) drawables[0] else view.context.drawable(drawableStart),
        if (drawableTop == 0) drawables[1] else view.context.drawable(drawableTop),
        if (drawableEnd == 0) drawables[2] else view.context.drawable(drawableEnd),
        if (drawableBottom == 0) drawables[3] else view.context.drawable(drawableBottom))
}

@BindingAdapter("visibility")
fun setVisibility(view: FloatingActionButton, isVisible: Boolean) {
    if (view.visibility == View.GONE && isVisible) {
        view.visibility = View.VISIBLE
    } else {
        if (isVisible) {
            view.show()
        } else {
            view.hide()
        }
    }
}