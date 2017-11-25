package com.pandulapeter.campfire.feature.home.favorites

import android.databinding.DataBindingUtil
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.app.AppCompatActivity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.pandulapeter.campfire.FavoritesBinding
import com.pandulapeter.campfire.R

/**
 * Displays the list of all songs the user marked as favorite. All of these items are also downloaded.
 * They can be deleted from the list using the swipe-to-dismiss gesture. The list can also be re-
 * organized.
 *
 * Controlled by [FavoritesViewModel].
 */
class FavoritesFragment : Fragment() {

    private lateinit var binding: FavoritesBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_favorites, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.viewModel = FavoritesViewModel()
        (activity as? AppCompatActivity)?.setSupportActionBar(binding.toolbar)
    }
}