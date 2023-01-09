package com.pandulapeter.campfire

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.pandulapeter.campfire.presentation.collections.CollectionsFragment
import com.pandulapeter.campfire.presentation.shared.navigation.Navigator
import com.pandulapeter.campfire.presentation.utilities.extensions.handleReplace

class CampfireActivity : AppCompatActivity(R.layout.activity_campfire), Navigator {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            navigateToCollections()
        }
    }

    override fun navigateToCollections() = supportFragmentManager.handleReplace(
        containerId = R.id.fragment_container,
        newInstance = CollectionsFragment.Companion::newInstance
    )
}