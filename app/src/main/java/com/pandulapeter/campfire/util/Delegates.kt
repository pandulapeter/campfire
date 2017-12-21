package com.pandulapeter.campfire.util

import android.os.Bundle
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty


sealed class BundleArgumentDelegate(protected val key: kotlin.String) {

    class Boolean(key: kotlin.String) : BundleArgumentDelegate(key), ReadWriteProperty<Bundle?, kotlin.Boolean> {

        override fun getValue(thisRef: Bundle?, property: KProperty<*>) = thisRef?.getBoolean(key) ?: false

        override fun setValue(thisRef: Bundle?, property: KProperty<*>, value: kotlin.Boolean) = thisRef?.putBoolean(key, value) ?: Unit
    }

    class Int(key: kotlin.String) : BundleArgumentDelegate(key), ReadWriteProperty<Bundle?, kotlin.Int> {

        override fun getValue(thisRef: Bundle?, property: KProperty<*>) = thisRef?.getInt(key) ?: 0

        override fun setValue(thisRef: Bundle?, property: KProperty<*>, value: kotlin.Int) = thisRef?.putInt(key, value) ?: Unit
    }

    class String(key: kotlin.String) : BundleArgumentDelegate(key), ReadWriteProperty<Bundle?, kotlin.String> {

        override fun getValue(thisRef: Bundle?, property: KProperty<*>) = thisRef?.getString(key) ?: ""

        override fun setValue(thisRef: Bundle?, property: KProperty<*>, value: kotlin.String) = thisRef?.putString(key, value) ?: Unit
    }
}

sealed class IntentExtraDelegate(protected val key: kotlin.String) {

}