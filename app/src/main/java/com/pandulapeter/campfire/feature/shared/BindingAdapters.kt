package com.pandulapeter.campfire.feature.shared

import android.databinding.BindingAdapter
import android.graphics.drawable.Drawable
import android.os.Build
import android.support.annotation.ColorInt
import android.support.annotation.DrawableRes
import android.support.annotation.StringRes
import android.support.design.widget.FloatingActionButton
import android.support.graphics.drawable.AnimatedVectorDrawableCompat
import android.text.Html
import android.text.Spannable
import android.text.SpannableString
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.text.style.TextAppearanceSpan
import android.text.style.TypefaceSpan
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.util.color
import com.pandulapeter.campfire.util.drawable
import com.pandulapeter.campfire.util.obtainColor


@BindingAdapter(value = ["android:drawableStart", "android:drawableTop", "android:drawableEnd", "android:drawableBottom"], requireAll = false)
fun setCompoundDrawables(
    view: TextView,
    @DrawableRes drawableStart: Int,
    @DrawableRes drawableTop: Int,
    @DrawableRes drawableEnd: Int,
    @DrawableRes drawableBottom: Int
) {
    val drawables = view.compoundDrawables
    val secondary = view.context.obtainColor(android.R.attr.textColorSecondary)
    view.setTextColor(view.context.obtainColor(android.R.attr.textColorPrimary))
    view.setCompoundDrawablesWithIntrinsicBounds(
        if (drawableStart == 0) drawables[0] else view.context.drawable(drawableStart)?.apply { setTint(secondary) },
        if (drawableTop == 0) drawables[1] else view.context.drawable(drawableTop)?.apply { setTint(secondary) },
        if (drawableEnd == 0) drawables[2] else view.context.drawable(drawableEnd)?.apply { setTint(secondary) },
        if (drawableBottom == 0) drawables[3] else view.context.drawable(drawableBottom)?.apply { setTint(secondary) }
    )
}

@BindingAdapter(value = ["android:paddingStart", "android:paddingTop", "android:paddingEnd", "android:paddingBottom"], requireAll = false)
fun setPadding(view: View, paddingStart: Int?, paddingTop: Int?, paddingEnd: Int?, paddingBottom: Int?) = view.setPadding(
    paddingStart ?: view.paddingStart,
    paddingTop ?: view.paddingTop,
    paddingEnd ?: view.paddingEnd,
    paddingBottom ?: view.paddingBottom
)

@BindingAdapter("android:src")
fun setImage(view: ImageView, url: String?) = Glide
    .with(view)
    .load(url)
    .apply(RequestOptions.placeholderOf(R.drawable.bg_splash))
    .into(view)

@BindingAdapter("android:text")
fun setText(view: EditText, text: String?) {
    if (!TextUtils.equals(view.text, text)) {
        view.setText(text)
        if (text != null) {
            view.setSelection(text.length)
        }
    }
}

@BindingAdapter("formattedText")
fun setFormattedText(view: TextView, @StringRes resourceId: Int) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        view.text = Html.fromHtml(view.context.getString(resourceId), Html.FROM_HTML_MODE_COMPACT)
    } else {
        @Suppress("DEPRECATION")
        view.text = Html.fromHtml(view.context.getString(resourceId))
    }
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

@BindingAdapter(value = ["title", "subtitle", "titleColor", "subtitleColor"], requireAll = false)
fun setTitleSubtitle(view: TextView, title: String?, subtitle: String?, @ColorInt titleColor: Int?, @ColorInt subtitleColor: Int?) {
    view.text = SpannableString("${title ?: ""}\n${subtitle ?: ""}").apply {
        title?.let {
            setSpan(TextAppearanceSpan(view.context, R.style.TextAppearance_AppCompat_Title), 0, it.length, Spannable.SPAN_INCLUSIVE_INCLUSIVE)
            if (titleColor != null) {
                setSpan(ForegroundColorSpan(titleColor), 0, it.length, Spannable.SPAN_INCLUSIVE_INCLUSIVE)
            }
        }
        subtitle?.let {
            if (subtitleColor != null) {
                setSpan(ForegroundColorSpan(subtitleColor), length - it.length, length, Spannable.SPAN_INCLUSIVE_INCLUSIVE)
            }
        }
    }
}

@BindingAdapter(value = ["title", "description"], requireAll = false)
fun setTitleDescription(view: TextView, title: String?, description: String?) {
    view.text = SpannableString("${title ?: ""}\n${description ?: ""}").apply {
        description?.let {
            setSpan(
                TextAppearanceSpan(view.context, R.style.TextAppearance_AppCompat_Caption),
                (title?.length ?: 0) + 1,
                length,
                Spannable.SPAN_INCLUSIVE_INCLUSIVE
            )
        }
    }
}

@BindingAdapter(value = ["primaryText", "secondaryText", "extraText"], requireAll = false)
fun setListItemText(view: TextView, primaryText: String, secondaryText: String?, extraText: String?) {
    view.text = SpannableString("$primaryText${secondaryText?.let { "\n$it" } ?: ""}${extraText?.let { "\n$it" } ?: ""}").apply {
        setSpan(
            TypefaceSpan("sans-serif-medium"),
            0,
            primaryText.length,
            Spannable.SPAN_INCLUSIVE_INCLUSIVE
        )
        secondaryText?.let {
            setSpan(
                TypefaceSpan("sans-serif-thin"),
                primaryText.length + 1,
                length,
                Spannable.SPAN_INCLUSIVE_INCLUSIVE
            )
        }
        extraText?.let {
            setSpan(
                TextAppearanceSpan(view.context, R.style.TextAppearance_AppCompat_Caption),
                length - it.length,
                length,
                Spannable.SPAN_INCLUSIVE_INCLUSIVE
            )
            setSpan(
                ForegroundColorSpan(view.context.color(R.color.accent)),
                length - it.length,
                length,
                Spannable.SPAN_INCLUSIVE_INCLUSIVE
            )
        }
    }
}