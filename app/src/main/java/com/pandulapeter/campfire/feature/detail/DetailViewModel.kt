package com.pandulapeter.campfire.feature.detail

import android.databinding.ObservableField
import android.support.v4.app.FragmentManager
import com.pandulapeter.campfire.data.repository.SongInfoRepository
import com.pandulapeter.campfire.feature.shared.CampfireViewModel

/**
 * Handles events and logic for [DetailActivity].
 */
class DetailViewModel(
    fragmentManager: FragmentManager,
    private val ids: List<String>,
    private val songInfoRepository: SongInfoRepository) : CampfireViewModel() {
    val title = ObservableField("")
    val artist = ObservableField("")
    val adapter = SongPagerAdapter(fragmentManager, ids)

    init {
        updateToolbar(ids[0])
    }

    fun onPageSelected(position: Int) = updateToolbar(ids[position])

    private fun updateToolbar(songId: String) {
        songInfoRepository.getSongInfo(songId)?.let {
            title.set(it.title)
            artist.set(it.artist)
        }
    }
}