package com.pandulapeter.campfire.ioc.module

import com.pandulapeter.campfire.data.networking.NetworkingManager
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
object NetworkingModule {

    @Provides
    @Singleton
    @JvmStatic
    fun provideNetworkingManager() = NetworkingManager()
}