package com.pandulapeter.campfire.util

import android.content.Context
import android.support.annotation.ColorRes
import android.support.v4.content.ContextCompat
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

fun Context.color(@ColorRes colorId: Int) = ContextCompat.getColor(this, colorId)

fun <T> Call<T>.enqueue(onSuccess: (T) -> Unit, onFailure: () -> Unit) {
    enqueue(object : Callback<T> {
        override fun onResponse(call: Call<T>?, response: Response<T>?) {
            if (response?.isSuccessful == true) response.body()?.let { onSuccess(it) } else onFailure()
        }

        override fun onFailure(call: Call<T>?, t: Throwable?) = onFailure()
    })
}