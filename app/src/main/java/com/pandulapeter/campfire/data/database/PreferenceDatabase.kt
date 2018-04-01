package com.pandulapeter.campfire.data.database

import android.content.Context
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class PreferenceDatabase(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("preferences", Context.MODE_PRIVATE)
    var lastUpdateTimestamp by PreferenceFieldDelegate.Long("last_update_timestamp")
    var shouldUseDarkTheme by PreferenceFieldDelegate.Boolean("should_use_dark_theme", false)

    private sealed class PreferenceFieldDelegate<T>(protected val key: String, protected val defaultValue: T) : ReadWriteProperty<PreferenceDatabase, T> {

        class Boolean(key: String, defaultValue: kotlin.Boolean = false) : PreferenceFieldDelegate<kotlin.Boolean>(key, defaultValue) {

            override fun getValue(thisRef: PreferenceDatabase, property: KProperty<*>) = thisRef.preferences.getBoolean(key, defaultValue)

            override fun setValue(thisRef: PreferenceDatabase, property: KProperty<*>, value: kotlin.Boolean) =
                thisRef.preferences.edit().putBoolean(key, value).apply()
        }

        class Int(key: String, defaultValue: kotlin.Int = 0) : PreferenceFieldDelegate<kotlin.Int>(key, defaultValue) {

            override fun getValue(thisRef: PreferenceDatabase, property: KProperty<*>) = thisRef.preferences.getInt(key, defaultValue)

            override fun setValue(thisRef: PreferenceDatabase, property: KProperty<*>, value: kotlin.Int) =
                thisRef.preferences.edit().putInt(key, value).apply()
        }

        class Long(key: String, defaultValue: kotlin.Long = 0) : PreferenceFieldDelegate<kotlin.Long>(key, defaultValue) {

            override fun getValue(thisRef: PreferenceDatabase, property: KProperty<*>) = thisRef.preferences.getLong(key, defaultValue)

            override fun setValue(thisRef: PreferenceDatabase, property: KProperty<*>, value: kotlin.Long) =
                thisRef.preferences.edit().putLong(key, value).apply()
        }
    }
}