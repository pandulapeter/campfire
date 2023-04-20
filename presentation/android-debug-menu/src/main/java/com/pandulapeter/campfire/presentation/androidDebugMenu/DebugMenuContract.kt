package com.pandulapeter.campfire.presentation.androidDebugMenu

import android.app.Application
import androidx.annotation.StyleRes

interface DebugMenuContract {

    fun initialize(
        application: Application,
        applicationTitle: String,
        @StyleRes themeResourceId: Int
    ) = Unit

    fun log(
        text: String
    ) = Unit
}