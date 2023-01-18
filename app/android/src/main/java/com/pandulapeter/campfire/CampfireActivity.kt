package com.pandulapeter.campfire

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import com.pandulapeter.campfire.presentation.android.CampfireApp

class CampfireActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CampfireApp() }
    }
}