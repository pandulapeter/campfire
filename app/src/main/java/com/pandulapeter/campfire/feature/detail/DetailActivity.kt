package com.pandulapeter.campfire.feature.detail

import android.content.Context
import android.content.Intent
import android.databinding.DataBindingUtil
import android.os.Bundle
import com.pandulapeter.campfire.DetailBinding
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.repository.SongInfoRepository
import dagger.android.support.DaggerAppCompatActivity
import javax.inject.Inject

/**
 * Displays the chords and lyrics of a single song per screen. The user can cycle through all the
 * songs in the category if they started this screen from Downloaded of Favorites.
 *
 * //TODO: Add option to add / remove song from Favorites.
 *
 * Controlled by [DetailViewModel].
 */
class DetailActivity : DaggerAppCompatActivity() {

    @Inject lateinit var songInfoRepository: SongInfoRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = DataBindingUtil.setContentView<DetailBinding>(this, R.layout.activity_detail)
        binding.viewModel = DetailViewModel(songInfoRepository, intent.id)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    companion object {
        private const val ID = "id"
        private val Intent.id
            get() = getStringExtra(ID)

        fun getStartIntent(context: Context, id: String): Intent = Intent(context, DetailActivity::class.java).putExtra(ID, id)
    }
}