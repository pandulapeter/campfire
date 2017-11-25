package com.pandulapeter.campfire.feature.detail

import android.content.Context
import android.content.Intent
import android.databinding.DataBindingUtil
import android.os.Bundle
import com.pandulapeter.campfire.DetailBinding
import com.pandulapeter.campfire.R
import dagger.android.support.DaggerAppCompatActivity

/**
 * Displays the chords and lyrics of a single song.
 *
 * Controlled by [DetailViewModel].
 */
class DetailActivity : DaggerAppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = DataBindingUtil.setContentView<DetailBinding>(this, R.layout.activity_detail)
        binding.viewModel = DetailViewModel(intent.title, intent.artist)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    companion object {
        private const val TITLE = "title"
        private const val ARTIST = "artist"
        private val Intent.title
            get() = getStringExtra(TITLE)
        private val Intent.artist
            get() = getStringExtra(ARTIST)

        fun getStartIntent(context: Context, title: String, artist: String): Intent =
            Intent(context, DetailActivity::class.java)
                .putExtra(TITLE, title)
                .putExtra(ARTIST, artist)
    }
}