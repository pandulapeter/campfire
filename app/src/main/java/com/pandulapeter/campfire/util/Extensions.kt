package com.pandulapeter.campfire.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.databinding.Observable
import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import android.databinding.ObservableInt
import android.os.Bundle
import android.support.annotation.ColorRes
import android.support.annotation.DimenRes
import android.support.annotation.DrawableRes
import android.support.design.internal.NavigationMenuView
import android.support.design.widget.AppBarLayout
import android.support.design.widget.CoordinatorLayout
import android.support.design.widget.NavigationView
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.support.v7.content.res.AppCompatResources
import android.view.View
import android.widget.CompoundButton
import com.pandulapeter.campfire.data.model.Language
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import kotlin.reflect.KClass


fun Context.color(@ColorRes colorId: Int) = ContextCompat.getColor(this, colorId)

fun Context.dimension(@DimenRes dimensionId: Int) = resources.getDimensionPixelSize(dimensionId)

fun Context.drawable(@DrawableRes drawableId: Int) = AppCompatResources.getDrawable(this, drawableId)

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
                callback(get())
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

inline fun <T> ObservableField<T>.onPropertyChanged(fragment: Fragment? = null, crossinline callback: (T) -> Unit) {
    addOnPropertyChangedCallback(object : Observable.OnPropertyChangedCallback() {
        override fun onPropertyChanged(sender: Observable?, propertyId: Int) {
            if (fragment?.isAdded != false) {
                callback(get())
            }
        }
    })
}

fun CompoundButton.setupWithBackingField(backingField: ObservableBoolean, shouldNegate: Boolean = false) {
    isChecked = backingField.get().let { if (shouldNegate) !it else it }
    setOnCheckedChangeListener { _, isChecked -> backingField.set(if (shouldNegate) !isChecked else isChecked) }
    backingField.onPropertyChanged { isChecked = if (shouldNegate) !it else it }
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

fun Fragment.setArguments(bundleOperations: (Bundle) -> Unit): Fragment {
    arguments = Bundle().apply { bundleOperations(this) }
    return this
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

fun AppBarLayout.performAfterExpand(connectedView: View, onExpanded: () -> Unit) {
    if (tag != null || height == bottom) {
        onExpanded()
    } else {
        tag = "expanding"
        var previousVerticalOffset = -Int.MAX_VALUE
        connectedView.let {
            it.layoutParams = (it.layoutParams as CoordinatorLayout.LayoutParams).apply {
                behavior = null
                setMargins(leftMargin, topMargin + height, rightMargin, bottomMargin)
            }
            it.requestLayout()
        }
        addOnOffsetChangedListener(object : AppBarLayout.OnOffsetChangedListener {
            override fun onOffsetChanged(appBarLayout: AppBarLayout?, verticalOffset: Int) {
                if (verticalOffset == 0 || verticalOffset <= previousVerticalOffset) {
                    onExpanded()
                    removeOnOffsetChangedListener(this)
                }
                previousVerticalOffset = verticalOffset
            }
        })
        setExpanded(true, true)
    }
}