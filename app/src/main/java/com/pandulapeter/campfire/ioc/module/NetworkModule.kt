package com.pandulapeter.campfire.ioc.module

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.pandulapeter.campfire.data.network.NetworkManager
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
object NetworkModule {

    @Provides
    @Singleton
    @JvmStatic
    fun provideGson() = GsonBuilder().create()

    @Provides
    @Singleton
    @JvmStatic
    fun provideNetworkManager(gson: Gson) = NetworkManager(gson)
}