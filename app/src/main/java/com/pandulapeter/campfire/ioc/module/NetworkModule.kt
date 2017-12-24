package com.pandulapeter.campfire.ioc.module

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.pandulapeter.campfire.networking.AnalyticsManager
import com.pandulapeter.campfire.networking.NetworkManager
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
object NetworkModule {

    @Provides
    @Singleton
    @JvmStatic
    fun provideGson(): Gson = GsonBuilder().create()

    @Provides
    @Singleton
    @JvmStatic
    fun provideAnalyticsManager() = AnalyticsManager()

    @Provides
    @Singleton
    @JvmStatic
    fun provideNetworkManager(gson: Gson) = NetworkManager(gson)
}