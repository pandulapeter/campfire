package com.pandulapeter.campfire.feature.shared

import android.databinding.BindingAdapter
import android.support.annotation.ColorInt
import android.widget.ImageView
import com.pandulapeter.campfire.util.tint

@BindingAdapter("android:tint")
fun setTint(imageView: ImageView, @ColorInt drawableTint: Int) =
    imageView.apply { setImageDrawable(drawable.tint(drawableTint)) }