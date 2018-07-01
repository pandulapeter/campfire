package com.pandulapeter.campfire.util

import android.content.Intent
import android.os.Bundle
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

sealed class BundleArgumentDelegate<T>(protected val key: kotlin.String, protected val defaultValue: T) : ReadWriteProperty<Bundle?, T> {

    class Boolean(key: kotlin.String, defaultValue: kotlin.Boolean = false) : BundleArgumentDelegate<kotlin.Boolean>(key, defaultValue) {

        override fun getValue(thisRef: Bundle?, property: KProperty<*>) = thisRef?.getBoolean(key, defaultValue) ?: defaultValue

        override fun setValue(thisRef: Bundle?, property: KProperty<*>, value: kotlin.Boolean) = thisRef?.putBoolean(key, value) ?: Unit
    }

    class Int(key: kotlin.String, defaultValue: kotlin.Int = 0) : BundleArgumentDelegate<kotlin.Int>(key, defaultValue) {

        override fun getValue(thisRef: Bundle?, property: KProperty<*>) = thisRef?.getInt(key, defaultValue) ?: defaultValue

        override fun setValue(thisRef: Bundle?, property: KProperty<*>, value: kotlin.Int) = thisRef?.putInt(key, value) ?: Unit
    }

    class String(key: kotlin.String, defaultValue: kotlin.String = "") : BundleArgumentDelegate<kotlin.String>(key, defaultValue) {

        override fun getValue(thisRef: Bundle?, property: KProperty<*>) = thisRef?.getString(key, defaultValue) ?: defaultValue

        override fun setValue(thisRef: Bundle?, property: KProperty<*>, value: kotlin.String) = thisRef?.putString(key, value) ?: Unit
    }

    class Parcelable(key: kotlin.String) : BundleArgumentDelegate<android.os.Parcelable?>(key, null) {

        override fun getValue(thisRef: Bundle?, property: KProperty<*>) = thisRef?.getParcelable(key) ?: defaultValue

        override fun setValue(thisRef: Bundle?, property: KProperty<*>, value: android.os.Parcelable?) = thisRef?.putParcelable(key, value) ?: Unit
    }

    class ParcelableArrayList<T : android.os.Parcelable>(key: kotlin.String) : BundleArgumentDelegate<ArrayList<T>>(key, arrayListOf()) {

        override fun getValue(thisRef: Bundle?, property: KProperty<*>) = thisRef?.getParcelableArrayList(key) ?: defaultValue

        override fun setValue(thisRef: Bundle?, property: KProperty<*>, value: ArrayList<T>) = thisRef?.putParcelableArrayList(key, value) ?: Unit
    }
}

sealed class IntentExtraDelegate<T>(protected val key: kotlin.String, protected val defaultValue: T) : ReadWriteProperty<Intent?, T> {

    class Boolean(key: kotlin.String, defaultValue: kotlin.Boolean = false) : IntentExtraDelegate<kotlin.Boolean>(key, defaultValue) {

        override fun getValue(thisRef: Intent?, property: KProperty<*>) = thisRef?.getBooleanExtra(key, defaultValue) ?: defaultValue

        override fun setValue(thisRef: Intent?, property: KProperty<*>, value: kotlin.Boolean) {
            thisRef?.putExtra(key, value)
        }
    }

    class Int(key: kotlin.String, defaultValue: kotlin.Int = 0) : IntentExtraDelegate<kotlin.Int>(key, defaultValue) {

        override fun getValue(thisRef: Intent?, property: KProperty<*>) = thisRef?.getIntExtra(key, defaultValue) ?: defaultValue

        override fun setValue(thisRef: Intent?, property: KProperty<*>, value: kotlin.Int) {
            thisRef?.putExtra(key, value)
        }
    }

    class String(key: kotlin.String, defaultValue: kotlin.String = "") : IntentExtraDelegate<kotlin.String>(key, defaultValue) {

        override fun getValue(thisRef: Intent?, property: KProperty<*>) = thisRef?.getStringExtra(key) ?: defaultValue

        override fun setValue(thisRef: Intent?, property: KProperty<*>, value: kotlin.String) {
            thisRef?.putExtra(key, value)
        }
    }
}