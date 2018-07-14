package com.pandulapeter.campfire.feature.shared

import android.databinding.BindingAdapter
import android.graphics.drawable.Drawable
import android.support.annotation.DrawableRes
import android.support.annotation.StringRes
import android.support.graphics.drawable.AnimatedVectorDrawableCompat
import android.text.Spannable
import android.text.SpannableString
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.text.style.TextAppearanceSpan
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.feature.shared.span.EllipsizeLineSpan
import com.pandulapeter.campfire.feature.shared.span.FontFamilySpan
import com.pandulapeter.campfire.util.*


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

@BindingAdapter("android:visibility")
fun setVisibility(view: View, isVisible: Boolean) {
    view.visibleOrGone = isVisible
}

@BindingAdapter("translationMultiplierX")
fun setTranslationMultiplierX(view: View, translationMultiplierX: Float) {
    view.apply { translationX = width * translationMultiplierX }
}

@BindingAdapter("translationMultiplierY")
fun setTranslationMultiplierY(view: View, translationMultiplierY: Float) {
    view.apply { translationY = width * translationMultiplierY }
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
    .apply(RequestOptions.circleCropTransform())
    .apply(RequestOptions.placeholderOf(R.drawable.bg_placeholder))
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
    view.text = view.context.parseHtml(resourceId)
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
fun setTitleSubtitle(view: TextView, title: String, subtitle: String?) {
    view.setLineSpacing(0f, 0.9f)
    view.text = SpannableString("$title${subtitle?.let { "\n$it" } ?: ""}").apply {
        setSpan(TextAppearanceSpan(view.context, R.style.Title), 0, title.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        setSpan(EllipsizeLineSpan(view.context.obtainColor(android.R.attr.textColorPrimary)), 0, title.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        subtitle?.let {
            setSpan(EllipsizeLineSpan(view.context.obtainColor(android.R.attr.textColorSecondary)), title.length + 1, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        setSpan(FontFamilySpan(view.context.font(R.font.regular)), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
}

@BindingAdapter(value = ["title", "description"], requireAll = false)
fun setTitleDescription(view: TextView, title: String?, description: String?) {
    view.text = SpannableString("${title ?: ""}\n${description ?: ""}").apply {
        description?.let {
            setSpan(
                TextAppearanceSpan(view.context, R.style.Caption),
                (title?.length ?: 0) + 1,
                length,
                Spannable.SPAN_INCLUSIVE_INCLUSIVE
            )
            if (view.isEnabled) {
                setSpan(
                    ForegroundColorSpan(view.context.obtainColor(android.R.attr.textColorSecondary)),
                    (title?.length ?: 0) + 1,
                    length,
                    Spannable.SPAN_INCLUSIVE_INCLUSIVE
                )
            }
        }
        setSpan(FontFamilySpan(view.context.font(R.font.regular)), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
}

@BindingAdapter(value = ["primaryText", "secondaryText", "extraText", "shouldEllipsize"], requireAll = false)
fun setListItemText(view: TextView, primaryText: String, secondaryText: String?, extraText: String?, shouldEllipsize: Boolean?) {
    view.setLineSpacing(0f, 0.9f)
    view.text = SpannableString("$primaryText${secondaryText?.let { "\n$it" } ?: ""}${extraText?.let { " $it" } ?: ""}").apply {
        setSpan(
            ForegroundColorSpan(view.context.obtainColor(android.R.attr.textColorPrimary)),
            0,
            primaryText.length,
            Spannable.SPAN_INCLUSIVE_INCLUSIVE
        )
        secondaryText?.let {
            setSpan(
                ForegroundColorSpan(view.context.obtainColor(android.R.attr.textColorSecondary)),
                primaryText.length + 1,
                length,
                Spannable.SPAN_INCLUSIVE_INCLUSIVE
            )
            if (shouldEllipsize == true) {
                setSpan(
                    EllipsizeLineSpan(),
                    primaryText.length + 1,
                    primaryText.length + 1 + it.length,
                    Spannable.SPAN_INCLUSIVE_INCLUSIVE
                )
            }
        }
        extraText?.let {
            setSpan(
                TextAppearanceSpan(view.context, R.style.Caption),
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