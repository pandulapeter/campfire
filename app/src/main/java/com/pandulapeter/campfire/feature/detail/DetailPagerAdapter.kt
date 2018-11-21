package com.pandulapeter.campfire.feature.detail

import com.pandulapeter.campfire.data.model.remote.Song
import com.pandulapeter.campfire.feature.detail.page.DetailPageFragment

class DetailPagerAdapter(fragmentManager: androidx.fragment.app.FragmentManager, private val songs: List<Song>) : androidx.fragment.app.FragmentStatePagerAdapter(fragmentManager) {

    override fun getItem(position: Int) = DetailPageFragment.newInstance(songs[position])

    override fun getCount() = songs.size
}