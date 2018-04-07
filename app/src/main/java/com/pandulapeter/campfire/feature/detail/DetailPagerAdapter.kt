package com.pandulapeter.campfire.feature.detail

import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentPagerAdapter
import com.pandulapeter.campfire.feature.detail.page.DetailPageFragment

class DetailPagerAdapter(fragmentManager: FragmentManager, private val songIds: List<String>) : FragmentPagerAdapter(fragmentManager) {

    override fun getItem(position: Int) = DetailPageFragment.newInstance(songIds[position])

    override fun getCount() = songIds.size
}