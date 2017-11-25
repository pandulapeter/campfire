package com.pandulapeter.campfire.ioc.module

import com.pandulapeter.campfire.data.network.NetworkManager
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
object NetworkModule {

    @Provides
    @Singleton
    @JvmStatic
    fun provideNetworkManager() = NetworkManager()
}