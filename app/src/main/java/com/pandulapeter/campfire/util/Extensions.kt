package com.pandulapeter.campfire.util

import android.content.Context
import android.support.annotation.AttrRes
import android.support.annotation.ColorInt
import android.support.annotation.ColorRes
import android.support.constraint.ConstraintLayout
import android.support.constraint.ConstraintSet
import android.support.v4.content.ContextCompat
import android.util.TypedValue
import android.view.View

fun Context.color(@ColorRes colorId: Int) = ContextCompat.getColor(this, colorId)

@ColorInt
fun Context.obtainColor(@AttrRes colorAttribute: Int): Int {
    val attributes = obtainStyledAttributes(TypedValue().data, intArrayOf(colorAttribute))
    val color = attributes.getColor(0, 0)
    attributes.recycle()
    return color
}

inline fun ConstraintLayout.makeConnections(block: ConstraintSet.() -> Unit) = ConstraintSet().let {
    it.clone(this)
    it.block()
    it.applyTo(this)
}

fun ConstraintLayout.connectToParent(view: View) = makeConnections {
    connect(view.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
    connect(view.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
    connect(view.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
    connect(view.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
}