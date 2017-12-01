package com.pandulapeter.campfire.feature.shared

import android.databinding.BindingAdapter
import android.support.annotation.ColorInt
import android.support.annotation.DrawableRes
import android.support.annotation.StringRes
import android.widget.ImageView
import com.pandulapeter.campfire.util.drawable
import com.pandulapeter.campfire.util.tint

@BindingAdapter("android:tint")
fun setTint(imageView: ImageView, @ColorInt colorResourceId: Int) =
    imageView.apply { setImageDrawable(drawable.tint(colorResourceId)) }

@BindingAdapter("android:src")
fun setImage(imageView: ImageView, @DrawableRes drawableResourceId: Int) =
    imageView.setImageDrawable(imageView.context.drawable(drawableResourceId))

@BindingAdapter("android:contentDescription")
fun setContentDescription(imageView: ImageView, @StringRes stringResourceId: Int) {
    imageView.contentDescription = imageView.context.getString(stringResourceId)
}