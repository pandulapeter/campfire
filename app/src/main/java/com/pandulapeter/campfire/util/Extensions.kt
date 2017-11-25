package com.pandulapeter.campfire.util

import android.content.Context
import android.databinding.Observable
import android.databinding.ObservableBoolean
import android.graphics.drawable.Drawable
import android.support.annotation.ColorInt
import android.support.annotation.ColorRes
import android.support.annotation.DimenRes
import android.support.v4.content.ContextCompat
import android.support.v4.graphics.drawable.DrawableCompat
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

fun Context.color(@ColorRes colorId: Int) = ContextCompat.getColor(this, colorId)

fun Context.dimension(@DimenRes dimensionId: Int) = resources.getDimensionPixelSize(dimensionId)

fun Drawable?.tint(@ColorInt tintColor: Int) = this?.let {
    val drawable = DrawableCompat.wrap(this)
    DrawableCompat.setTint(drawable.mutate(), tintColor)
    DrawableCompat.unwrap<Drawable>(drawable)
}

inline fun ObservableBoolean.onEventTriggered(crossinline callback: () -> Unit) {
    addOnPropertyChangedCallback(object : Observable.OnPropertyChangedCallback() {
        override fun onPropertyChanged(sender: Observable?, propertyId: Int) {
            if (get()) {
                callback()
                set(false)
            }
        }
    })
}

inline fun ObservableBoolean.onPropertyChanged(crossinline callback: (Boolean) -> Unit) {
    addOnPropertyChangedCallback(object : Observable.OnPropertyChangedCallback() {
        override fun onPropertyChanged(sender: Observable?, propertyId: Int) {
            callback(get())
        }
    })
}


fun <T> Call<T>.enqueueCall(onSuccess: (T) -> Unit, onFailure: () -> Unit) {
    enqueue(object : Callback<T> {
        override fun onResponse(call: Call<T>?, response: Response<T>?) {
            if (response?.isSuccessful == true) response.body()?.let { onSuccess(it) } else onFailure()
        }

        override fun onFailure(call: Call<T>?, t: Throwable?) = onFailure()
    })
}