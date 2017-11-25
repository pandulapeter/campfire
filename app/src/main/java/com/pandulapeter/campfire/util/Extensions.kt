package com.pandulapeter.campfire.util

import android.content.Context
import android.databinding.Observable
import android.databinding.ObservableBoolean
import android.support.annotation.ColorRes
import android.support.v4.content.ContextCompat
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

fun Context.color(@ColorRes colorId: Int) = ContextCompat.getColor(this, colorId)

internal inline fun ObservableBoolean.onEventTriggered(crossinline callback: () -> Unit) {
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