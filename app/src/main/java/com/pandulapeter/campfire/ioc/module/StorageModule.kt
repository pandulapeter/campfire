package com.pandulapeter.campfire.ioc.module

import android.content.Context
import com.pandulapeter.campfire.data.SharedPreferencesManager
import com.pandulapeter.campfire.ioc.app.AppContext
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
object StorageModule {

    @Provides
    @Singleton
    @JvmStatic
    fun provideSharedPreferences(@AppContext context: Context) = SharedPreferencesManager(context)
}