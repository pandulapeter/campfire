package com.pandulapeter.campfire.old.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.databinding.Observable
import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import android.databinding.ObservableInt
import android.os.Bundle
import android.support.annotation.*
import android.support.design.internal.NavigationMenuView
import android.support.design.widget.NavigationView
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.support.v4.widget.DrawerLayout
import android.support.v7.content.res.AppCompatResources
import android.util.TypedValue
import android.view.View
import android.widget.CompoundButton
import com.pandulapeter.campfire.old.data.model.Language
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import kotlin.reflect.KClass

fun Context.color(@ColorRes colorId: Int) = ContextCompat.getColor(this, colorId)

fun Context.dimension(@DimenRes dimensionId: Int) = resources.getDimensionPixelSize(dimensionId)

fun Context.drawable(@DrawableRes drawableId: Int) = AppCompatResources.getDrawable(this, drawableId)

@ColorInt
fun Context.obtainColor(@AttrRes colorAttribute: Int): Int {
    val attributes = obtainStyledAttributes(TypedValue().data, intArrayOf(colorAttribute))
    val color = attributes.getColor(0, 0)
    attributes.recycle()
    return color
}

fun ObservableBoolean.toggle() = set(!get())

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

inline fun ObservableInt.onEventTriggered(fragment: Fragment? = null, crossinline callback: (Int) -> Unit) {
    addOnPropertyChangedCallback(object : Observable.OnPropertyChangedCallback() {
        override fun onPropertyChanged(sender: Observable?, propertyId: Int) {
            if (get() != 0 && fragment?.isAdded != false) {
                callback(get())
                set(0)
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

fun CompoundButton.setupWithBackingField(backingField: ObservableBoolean) {
    isChecked = backingField.get()
    setOnCheckedChangeListener { _, isChecked -> backingField.set(isChecked) }
    backingField.onPropertyChanged { isChecked = it }
}

fun <T> CompoundButton.setupWithBackingField(backingField: ObservableField<T>, value: T) {
    isChecked = backingField.get() == value
    setOnCheckedChangeListener { _, isChecked -> if (isChecked) { backingField.set(value) } }
    backingField.onPropertyChanged { isChecked = it == value }
}

fun <T> Call<T>.enqueueCall(onSuccess: (T) -> Unit, onFailure: () -> Unit) {
    enqueue(object : Callback<T> {
        override fun onResponse(call: Call<T>?, response: Response<T>?) {
            if (response?.isSuccessful == true) response.body()?.let { onSuccess(it) } else onFailure()
        }

        override fun onFailure(call: Call<T>?, t: Throwable?) = onFailure()
    })
}

fun String?.mapToLanguage() = when (this) {
    Language.SupportedLanguages.ENGLISH.id -> Language.Known.English
    Language.SupportedLanguages.HUNGARIAN.id -> Language.Known.Hungarian
    else -> Language.Unknown
}

fun NavigationView.disableScrollbars() {
    (getChildAt(0) as? NavigationMenuView)?.isVerticalScrollBarEnabled = false
}

inline fun <T : Fragment> T.withArguments(bundleOperations: (Bundle) -> Unit): T = apply {
    arguments = Bundle().apply { bundleOperations(this) }
}

fun Context.getIntentFor(activityClass: KClass<out Activity>, extraOperations: (Intent) -> Unit = {}) =
    Intent(this, activityClass.java).apply { extraOperations(this) }

fun String.replaceSpecialCharacters() = this
    .replace("á", "a")
    .replace("Á", "A")
    .replace("ă", "a")
    .replace("Ă", "A")
    .replace("â", "a")
    .replace("Â", "A")
    .replace("é", "e")
    .replace("É", "E")
    .replace("í", "i")
    .replace("Í", "I")
    .replace("î", "i")
    .replace("Î", "I")
    .replace("ó", "o")
    .replace("Ó", "O")
    .replace("ö", "o")
    .replace("Ö", "O")
    .replace("ő", "o")
    .replace("Ő", "O")
    .replace("ș", "s")
    .replace("Ș", "S")
    .replace("ț", "t")
    .replace("Ț", "T")
    .replace("ú", "u")
    .replace("Ú", "U")
    .replace("ű", "u")
    .replace("Ű", "U")

fun DrawerLayout.addDrawerListener(
    onDrawerStateChanged: () -> Unit = {},
    onDrawerSlide: () -> Unit = {},
    onDrawerClosed: () -> Unit = {},
    onDrawerOpened: () -> Unit = {}
) = addDrawerListener(object : DrawerLayout.DrawerListener {
    override fun onDrawerStateChanged(newState: Int) = onDrawerStateChanged()

    override fun onDrawerSlide(drawerView: View, slideOffset: Float) = onDrawerSlide()

    override fun onDrawerClosed(drawerView: View) = onDrawerClosed()

    override fun onDrawerOpened(drawerView: View) = onDrawerOpened()
})