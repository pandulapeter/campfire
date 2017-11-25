package com.pandulapeter.campfire

import android.databinding.DataBindingUtil
import android.os.Bundle
import android.support.v7.app.AppCompatActivity

/**
 * Displays the main screen of the app which contains the app bar, the three possible Fragments that
 * can be selected and the bottom navigation.
 *
 * Controlled by [HomeViewModel].
 */
class HomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DataBindingUtil.setContentView<HomeBinding>(this, R.layout.activity_home).viewModel = HomeViewModel()
    }
}