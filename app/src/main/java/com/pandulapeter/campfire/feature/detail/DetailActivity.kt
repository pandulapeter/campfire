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
        binding.viewModel = DetailViewModel(supportFragmentManager, songInfoRepository, intent.currentId, intent.ids)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    companion object {
        private const val CURRENT_ID = "current_id"
        private const val IDS = "ids"
        private val Intent.currentId
            get() = getStringExtra(CURRENT_ID)
        private val Intent.ids
            get() = getStringArrayExtra(IDS).toList()

        fun getStartIntent(context: Context, currentId: String, ids: List<String> = listOf()): Intent = Intent(context, DetailActivity::class.java)
            .putExtra(CURRENT_ID, currentId)
            .putExtra(IDS, ids.toTypedArray())
    }
}