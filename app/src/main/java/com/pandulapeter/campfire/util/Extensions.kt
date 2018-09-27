package com.pandulapeter.campfire.util

import android.animation.Animator
import android.content.Context
import android.content.Intent
import android.content.res.TypedArray
import android.databinding.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.support.annotation.*
import android.support.graphics.drawable.AnimatedVectorDrawableCompat
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.support.v4.content.res.ResourcesCompat
import android.support.v4.view.ViewPager
import android.support.v4.widget.DrawerLayout
import android.support.v7.content.res.AppCompatResources
import android.text.Editable
import android.text.Html
import android.text.TextWatcher
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.ViewAnimationUtils
import android.view.ViewTreeObserver
import android.widget.EditText
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

fun Context.color(@ColorRes colorId: Int) = ContextCompat.getColor(this, colorId)

fun Context.dimension(@DimenRes dimensionId: Int) = resources.getDimensionPixelSize(dimensionId)

fun Context.drawable(@DrawableRes drawableId: Int) = AppCompatResources.getDrawable(this, drawableId)

fun Context.font(@FontRes fontId: Int) = ResourcesCompat.getFont(this, fontId) ?: throw(Throwable("Font doesn't exist"))

fun Context.animatedDrawable(@DrawableRes drawableId: Int) = AnimatedVectorDrawableCompat.create(this, drawableId)

fun Context.parseHtml(@StringRes resourceId: Int) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
    Html.fromHtml(getString(resourceId), Html.FROM_HTML_MODE_COMPACT)
} else {
    @Suppress("DEPRECATION")
    Html.fromHtml(getString(resourceId))
} ?: ""

fun <T> MutableCollection<T>.swap(newItems: Collection<T>) {
    clear()
    addAll(newItems)
}

@ColorInt
fun Context.obtainColor(@AttrRes colorAttribute: Int): Int {
    val attributes = obtainStyledAttributes(TypedValue().data, intArrayOf(colorAttribute))
    val color = attributes.getColor(0, 0)
    attributes.recycle()
    return color
}

@set:BindingAdapter("visibility")
var View.visibleOrGone
    get() = visibility == View.VISIBLE
    set(value) {
        visibility = if (value) View.VISIBLE else View.GONE
    }

@set:BindingAdapter("invisibility")
var View.visibleOrInvisible
    get() = visibility == View.VISIBLE
    set(value) {
        visibility = if (value) View.VISIBLE else View.INVISIBLE
    }

@set:BindingAdapter("animatedVisibilityStart")
var View.animatedVisibilityStart: Boolean
    get() = visibleOrGone
    set(value) {
        animateCircularReveal(value, true)
    }

@set:BindingAdapter("animatedVisibilityEnd")
var View.animatedVisibilityEnd: Boolean
    get() = visibleOrGone
    set(value) {
        animateCircularReveal(value, false)
    }

var View.animatedVisibilityCenter: Boolean
    get() = visibleOrGone
    set(value) {
        animateCircularReveal(value, null)
    }

private fun View.animateCircularReveal(isVisible: Boolean, start: Boolean?) {
    if (isAttachedToWindow) {
        val cx = when (start) {
            null -> width / 2
            true -> 0
            false -> width
        }
        val cy = height / 2
        val maxRadius = Math.hypot(width.toDouble(), height.toDouble()).toFloat()
        visibleOrGone = true
        val animator = ViewAnimationUtils.createCircularReveal(this, cx, cy, if (isVisible) 0f else maxRadius, if (isVisible) maxRadius else 0f).apply {
            addListener(onAnimationEnd = {
                visibleOrGone = isVisible
                tag = null
            })
        }
        tag = animator
        animator.start()
    }
}

@JvmOverloads
inline fun View.useStyledAttributes(set: AttributeSet?, @StyleableRes attrs: IntArray, defStyleAttr: Int = 0, defStyleRes: Int = 0, crossinline block: TypedArray.() -> Unit) =
    set?.let {
        val typedArray = context.theme.obtainStyledAttributes(set, attrs, defStyleAttr, defStyleRes)
        try {
            block(typedArray)
        } finally {
            typedArray.recycle()
        }
    }

inline fun ObservableBoolean.onPropertyChanged(fragment: Fragment? = null, crossinline callback: (Boolean) -> Unit) {
    addOnPropertyChangedCallback(object : Observable.OnPropertyChangedCallback() {
        override fun onPropertyChanged(sender: Observable?, propertyId: Int) {
            if (fragment?.isAdded != false) {
                callback(get())
            }
        }
    })
}

inline fun ObservableInt.onPropertyChanged(fragment: Fragment? = null, crossinline callback: (Int) -> Unit) {
    addOnPropertyChangedCallback(object : Observable.OnPropertyChangedCallback() {
        override fun onPropertyChanged(sender: Observable?, propertyId: Int) {
            if (fragment?.isAdded != false) {
                callback(get())
            }
        }
    })
}

inline fun <T> ObservableField<T>.onPropertyChanged(fragment: Fragment? = null, crossinline callback: (T) -> Unit) {
    addOnPropertyChangedCallback(object : Observable.OnPropertyChangedCallback() {
        override fun onPropertyChanged(sender: Observable?, propertyId: Int) {
            if (fragment?.isAdded != false) {
                get()?.let { callback(it) }
            }
        }
    })
}

inline fun ObservableBoolean.onEventTriggered(fragment: Fragment? = null, crossinline callback: () -> Unit) {
    addOnPropertyChangedCallback(object : Observable.OnPropertyChangedCallback() {
        override fun onPropertyChanged(sender: Observable?, propertyId: Int) {
            if (get() && fragment?.isAdded != false) {
                callback()
                set(false)
            }
        }
    })
}

inline fun <T> ObservableField<T>.onEventTriggered(fragment: Fragment? = null, crossinline callback: (T) -> Unit) {
    addOnPropertyChangedCallback(object : Observable.OnPropertyChangedCallback() {
        override fun onPropertyChanged(sender: Observable?, propertyId: Int) {
            if (get() != null && fragment?.isAdded != false) {
                get()?.let { callback(it) }
                set(null)
            }
        }
    })
}

inline fun DrawerLayout.addDrawerListener(
    crossinline onDrawerStateChanged: (newState: Int) -> Unit = {},
    crossinline onDrawerSlide: () -> Unit = {},
    crossinline onDrawerClosed: () -> Unit = {},
    crossinline onDrawerOpened: () -> Unit = {}
) = addDrawerListener(object : DrawerLayout.DrawerListener {
    override fun onDrawerStateChanged(newState: Int) = onDrawerStateChanged(newState)

    override fun onDrawerSlide(drawerView: View, slideOffset: Float) = onDrawerSlide()

    override fun onDrawerClosed(drawerView: View) = onDrawerClosed()

    override fun onDrawerOpened(drawerView: View) = onDrawerOpened()
})

inline fun View.waitForPreDraw(crossinline block: () -> Boolean) = with(viewTreeObserver) {
    addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
        override fun onPreDraw(): Boolean {
            val shouldContinue = block()
            viewTreeObserver.removeOnPreDrawListener(this)
            return shouldContinue
        }

    })
}

inline fun Animator.addListener(
    crossinline onAnimationRepeat: () -> Unit = {},
    crossinline onAnimationEnd: () -> Unit = {},
    crossinline onAnimationCancel: () -> Unit = {},
    crossinline onAnimationStart: () -> Unit = {}
) = addListener(object : Animator.AnimatorListener {
    override fun onAnimationRepeat(animation: Animator?) = onAnimationRepeat()

    override fun onAnimationEnd(animation: Animator?) = onAnimationEnd()

    override fun onAnimationCancel(animation: Animator?) = onAnimationCancel()

    override fun onAnimationStart(animation: Animator?) = onAnimationStart()
})

inline fun EditText.onTextChanged(crossinline callback: (String) -> Unit) = addTextChangedListener(object : TextWatcher {
    override fun afterTextChanged(s: Editable?) = Unit

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = callback(s?.toString() ?: "")
})

inline fun <T : Fragment> T.withArguments(bundleOperations: (Bundle) -> Unit): T = apply {
    arguments = Bundle().apply { bundleOperations(this) }
}

fun <T> Call<T>.enqueueCall(onSuccess: (T) -> Unit, onFailure: () -> Unit) {
    enqueue(object : Callback<T> {
        override fun onResponse(call: Call<T>?, response: Response<T>?) {
            if (response?.isSuccessful == true) response.body()?.let { onSuccess(it) } else onFailure()
        }

        override fun onFailure(call: Call<T>?, t: Throwable?) = onFailure()
    })
}

fun String.normalize() = toUpperCase()
    .replace("Á", "A")
    .replace("Ă", "A")
    .replace("Â", "A")
    .replace("É", "E")
    .replace("Í", "I")
    .replace("Î", "I")
    .replace("Ó", "O")
    .replace("Ö", "O")
    .replace("Ő", "O")
    .replace("Ș", "S")
    .replace("Ț", "T")
    .replace("Ü", "U")
    .replace("Ú", "U")
    .replace("Ű", "U")
    .replace("'", "")
    .replace("/", "")
    .replace("-", "")

fun String.removePrefixes() = this
    .removePrefix("A ")
    .removePrefix("AZ ")
    .removePrefix("THE ")

fun ViewPager.addPageScrollListener(onPageSelected: (Int) -> Unit = {}, onPageScrollStateChanged: (Int) -> Unit = {}, onPageScrolled: (Int, Float) -> Unit = { _, _ -> }) =
    addOnPageChangeListener(object : ViewPager.OnPageChangeListener {

        override fun onPageScrollStateChanged(state: Int) = onPageScrollStateChanged(state)

        override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) = onPageScrolled(position, positionOffset)

        override fun onPageSelected(position: Int) = onPageSelected(position)
    })

fun String.toUrlIntent() = Intent(Intent.ACTION_VIEW).apply { data = Uri.parse(this@toUrlIntent) }