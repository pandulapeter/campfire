package com.pandulapeter.campfire.util

import android.content.Context
import android.support.annotation.ColorRes
import android.support.v4.content.ContextCompat

fun Context.color(@ColorRes colorId: Int) = ContextCompat.getColor(this, colorId)