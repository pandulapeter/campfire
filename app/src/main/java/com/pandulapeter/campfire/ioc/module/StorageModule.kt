package com.pandulapeter.campfire.ioc.module

import android.content.Context
import com.google.gson.Gson
import com.pandulapeter.campfire.data.storage.StorageManager
import com.pandulapeter.campfire.ioc.app.AppContext
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
object StorageModule {

    @Provides
    @Singleton
    @JvmStatic
    fun provideStorageManager(@AppContext context: Context, gson: Gson) = StorageManager(context, gson)
}