package com.pandulapeter.campfire.feature.home

import android.databinding.DataBindingUtil
import android.os.Bundle
import com.pandulapeter.campfire.HomeBinding
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.networking.NetworkingManager
import dagger.android.support.DaggerAppCompatActivity
import javax.inject.Inject

/**
 * Displays the main screen of the app which contains the app bar, the three possible Fragments that
 * can be selected and the bottom navigation.
 *
 * Controlled by [HomeViewModel].
 */
class HomeActivity : DaggerAppCompatActivity() {

    @Inject lateinit var networkingManager: NetworkingManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DataBindingUtil.setContentView<HomeBinding>(this, R.layout.activity_home).viewModel = HomeViewModel(networkingManager)
    }
}