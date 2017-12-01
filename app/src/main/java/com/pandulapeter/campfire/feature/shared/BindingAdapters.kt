package com.pandulapeter.campfire.feature.shared

import android.databinding.BindingAdapter
import android.databinding.InverseBindingListener
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

@BindingAdapter(value = *arrayOf("onQueryChanged", "queryAttrChanged"), requireAll = false)
fun setListeners(view: SearchTitleView, listener: SearchTitleView.OnQueryChangedListener?, attrChange: InverseBindingListener?) =
    view.setOnQueryChangeListener(if (attrChange == null) listener else object : SearchTitleView.OnQueryChangedListener {
        override fun onQueryChanged(query: String) {
            listener?.onQueryChanged(query)
            attrChange.onChange()
        }
    })

@BindingAdapter(value = *arrayOf("onSearchInputVisibilityChanged", "searchInputVisibleAttrChanged"), requireAll = false)
fun setListeners(view: SearchTitleView, listener: SearchTitleView.OnSearchInputVisibilityChangedListener?, attrChange: InverseBindingListener?) =
    view.setOnSearchInputVisibilityChangedListener(if (attrChange == null) listener else object : SearchTitleView.OnSearchInputVisibilityChangedListener {
        override fun onSearchInputVisibilityChanged(visibility: Boolean) {
            listener?.onSearchInputVisibilityChanged(visibility)
            attrChange.onChange()
        }
    })