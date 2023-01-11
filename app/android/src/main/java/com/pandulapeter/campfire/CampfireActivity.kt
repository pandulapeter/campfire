package com.pandulapeter.campfire

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.pandulapeter.campfire.presentation.android.MainFragment
import com.pandulapeter.campfire.presentation.android.utilities.handleReplace

class CampfireActivity : AppCompatActivity(R.layout.activity_campfire) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            navigateToCollections()
        }
    }

    private fun navigateToCollections() = supportFragmentManager.handleReplace(
        containerId = R.id.fragment_container,
        newInstance = MainFragment.Companion::newInstance
    )
}