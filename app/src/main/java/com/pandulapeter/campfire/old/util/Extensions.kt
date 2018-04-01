package com.pandulapeter.campfire.old.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.databinding.Observable
import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import android.databinding.ObservableInt
import android.os.Bundle
import android.support.design.internal.NavigationMenuView
import android.support.design.widget.NavigationView
import android.support.v4.app.Fragment
import android.widget.CompoundButton
import com.pandulapeter.campfire.data.model.Language
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import kotlin.reflect.KClass

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
    setOnCheckedChangeListener { _, isChecked ->
        if (isChecked) {
            backingField.set(value)
        }
    }
    backingField.onPropertyChanged { isChecked = it == value }
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