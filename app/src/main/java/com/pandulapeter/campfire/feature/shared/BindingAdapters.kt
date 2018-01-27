package com.pandulapeter.campfire.feature.shared

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
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
import android.text.style.ForegroundColorSpan
import android.text.style.TextAppearanceSpan
import android.text.style.TypefaceSpan
import android.view.View
import android.view.ViewAnimationUtils
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.util.color
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

@BindingAdapter("animatedVisibility")
fun setAnimatedVisibility(view: View, isVisible: Boolean) {
    if (view.isAttachedToWindow) {
        val cx = view.width
        val cy = view.height / 2
        val maxRadius = Math.hypot(cx.toDouble(), cy.toDouble()).toFloat()
        if (isVisible) {
            view.visibility = View.VISIBLE
            ViewAnimationUtils.createCircularReveal(view, cx, cy, 0f, maxRadius).start()
        } else {
            ViewAnimationUtils.createCircularReveal(view, cx, cy, maxRadius, 0f).apply {
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        super.onAnimationEnd(animation)
                        view.visibility = View.INVISIBLE
                    }
                })
            }.start()
        }
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
    val text = SpannableString("${title ?: ""}\n${subtitle ?: ""}")
    title?.let {
        text.setSpan(TextAppearanceSpan(view.context, R.style.TextAppearance_AppCompat_Title), 0, it.length, Spannable.SPAN_INCLUSIVE_INCLUSIVE)
        text.setSpan(EllipsizeLineSpan(), 0, it.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    subtitle?.let {
        text.setSpan(
            TextAppearanceSpan(view.context, R.style.SubtitleTextAppearance),
            (title?.length ?: 0) + 1,
            text.length,
            Spannable.SPAN_INCLUSIVE_INCLUSIVE
        )
        text.setSpan(EllipsizeLineSpan(), (title?.length ?: 0) + 1, text.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    view.text = text
}

@BindingAdapter(value = ["title", "description"], requireAll = false)
fun setTitleDescription(view: TextView, title: String?, description: String?) {
    val text = SpannableString("${title ?: ""}\n${description ?: ""}")
    title?.let {
        text.setSpan(
            ForegroundColorSpan(view.context.color(R.color.text)),
            0,
            it.length,
            Spannable.SPAN_INCLUSIVE_INCLUSIVE
        )
    }
    description?.let {
        text.setSpan(
            TextAppearanceSpan(view.context, R.style.TextAppearance_AppCompat_Caption),
            (title?.length ?: 0) + 1,
            text.length,
            Spannable.SPAN_INCLUSIVE_INCLUSIVE
        )
    }
    view.text = text
}

@BindingAdapter(value = ["songTitle", "songArtist", "songExtra"], requireAll = false)
fun setSongInfoItemText(view: TextView, title: String?, artist: String?, songExtra: Int?) {
    val extra = songExtra?.let { view.context.getString(it) }
    val text = SpannableString("${title ?: ""}\n${artist ?: ""}${extra?.let { "\n$it" } ?: ""}")
    title?.let {
        text.setSpan(
            TypefaceSpan("sans-serif-medium"),
            0,
            it.length,
            Spannable.SPAN_INCLUSIVE_INCLUSIVE
        )
    }
    artist?.let {
        text.setSpan(
            TypefaceSpan("sans-serif-thin"),
            (title?.length ?: 0) + 1,
            text.length,
            Spannable.SPAN_INCLUSIVE_INCLUSIVE
        )
    }
    extra?.let {
        text.setSpan(
            ForegroundColorSpan(view.context.color(R.color.accent)),
            text.length - it.length,
            text.length,
            Spannable.SPAN_INCLUSIVE_INCLUSIVE
        )
        text.setSpan(
            TextAppearanceSpan(view.context, R.style.TextAppearance_AppCompat_Caption),
            text.length - it.length,
            text.length,
            Spannable.SPAN_INCLUSIVE_INCLUSIVE
        )
    }
    view.text = text
}