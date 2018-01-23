package com.pandulapeter.campfire.feature.shared

import android.databinding.BindingAdapter
import android.graphics.drawable.Drawable
import android.support.annotation.DrawableRes
import android.support.annotation.StringRes
import android.support.design.widget.AppBarLayout
import android.support.design.widget.FloatingActionButton
import android.support.graphics.drawable.AnimatedVectorDrawableCompat
import android.text.Spannable
import android.text.SpannableString
import android.text.TextUtils
import android.text.style.TextAppearanceSpan
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.util.drawable

@BindingAdapter(value = ["android:drawableStart", "android:drawableTop", "android:drawableEnd", "android:drawableBottom"], requireAll = false)
fun setCompoundDrawables(
    view: TextView,
    @DrawableRes drawableStart: Int,
    @DrawableRes drawableTop: Int,
    @DrawableRes drawableEnd: Int,
    @DrawableRes drawableBottom: Int
) {
    val drawables = view.compoundDrawables
    view.setCompoundDrawablesWithIntrinsicBounds(
        if (drawableStart == 0) drawables[0] else view.context.drawable(drawableStart),
        if (drawableTop == 0) drawables[1] else view.context.drawable(drawableTop),
        if (drawableEnd == 0) drawables[2] else view.context.drawable(drawableEnd),
        if (drawableBottom == 0) drawables[3] else view.context.drawable(drawableBottom)
    )
}

@BindingAdapter("android:text")
fun setText(view: EditText, text: String?) {
    if (!TextUtils.equals(view.text, text)) {
        view.setText(text)
        if (text != null) {
            view.setSelection(text.length)
        }
    }
}

@BindingAdapter("android:text")
fun setText(view: TextView, @StringRes text: Int?) {
    view.visibility = if (text == null) View.GONE else View.VISIBLE
    text?.let {
        view.setText(text)
    }
}

@BindingAdapter("visibility")
fun setVisibility(view: View, isVisible: Boolean) {
    view.visibility = if (isVisible) View.VISIBLE else View.GONE
}

@BindingAdapter("visibility")
fun setVisibility(view: FloatingActionButton, isVisible: Boolean) {
    if (isVisible) {
        view.show()
        view.visibility = View.VISIBLE
    } else {
        view.hide()
    }
}

@BindingAdapter("isScrollEnabled")
fun setScrollEnabled(view: View, isScrollEnabled: Boolean) {
    (view.layoutParams as AppBarLayout.LayoutParams).scrollFlags = if (isScrollEnabled) {
        AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL or AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS or AppBarLayout.LayoutParams.SCROLL_FLAG_SNAP
    } else {
        0
    }
    (view.parent as? AppBarLayout)?.setExpanded(true, true)
}

@BindingAdapter(value = ["animation", "lastFrame", "forcePlay"], requireAll = false)
fun setAnimation(view: ImageView, @DrawableRes drawableRes: Int, lastFrame: Drawable?, forcePlay: Boolean?) {
    if (forcePlay == true) {
        view.post {
            val drawable = AnimatedVectorDrawableCompat.create(view.context, drawableRes)
            view.setImageDrawable(drawable)
            drawable?.start()
        }
    } else {
        if (view.drawable == null && lastFrame != null) {
            view.setImageDrawable(lastFrame)
        } else {
            if (drawableRes != view.tag) {
                val drawable = AnimatedVectorDrawableCompat.create(view.context, drawableRes)
                view.setImageDrawable(drawable)
                drawable?.start()
            }
        }
    }
    view.tag = drawableRes
}

@BindingAdapter(value = ["title", "subtitle"], requireAll = false)
fun setTitleSubtitle(view: TextView, title: String?, subtitle: String?) {
    //TODO: In portrait split screen mode the smallest possible window size adds an extra line break between the title and the subtitle.
    val completeText = SpannableString("${title ?: ""}\n${subtitle ?: ""}")
    title?.let {
        completeText.setSpan(TextAppearanceSpan(view.context, R.style.TextAppearance_AppCompat_Title), 0, it.length, Spannable.SPAN_INCLUSIVE_INCLUSIVE)
        completeText.setSpan(EllipsizeLineSpan(), 0, it.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    subtitle?.let {
        completeText.setSpan(
            TextAppearanceSpan(view.context, R.style.SubtitleTextAppearance),
            title?.length ?: 0,
            completeText.length,
            Spannable.SPAN_INCLUSIVE_INCLUSIVE
        )
        completeText.setSpan(EllipsizeLineSpan(), title?.length ?: 0, completeText.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    view.text = completeText
}