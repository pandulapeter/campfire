package com.pandulapeter.campfire.inversionOfControl.app

import android.content.Context
import com.pandulapeter.campfire.CampfireApplication
import dagger.Binds
import dagger.Module
import javax.inject.Singleton

@Module
abstract class AppModule {

    @Singleton
    @AppContext
    @Binds
    abstract fun bindsContext(app: CampfireApplication): Context
}