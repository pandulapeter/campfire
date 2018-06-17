package com.pandulapeter.campfire.feature.detail

import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentStatePagerAdapter
import com.pandulapeter.campfire.data.model.remote.Song
import com.pandulapeter.campfire.feature.detail.page.DetailPageFragment

class DetailPagerAdapter(fragmentManager: FragmentManager, private val songs: List<Song>) : FragmentStatePagerAdapter(fragmentManager) {

    override fun getItem(position: Int) = DetailPageFragment.newInstance(songs[position])

    override fun getCount() = songs.size
}