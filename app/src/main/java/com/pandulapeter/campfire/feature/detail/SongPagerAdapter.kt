package com.pandulapeter.campfire.feature.detail

import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentStatePagerAdapter
import com.pandulapeter.campfire.feature.detail.songPage.SongPageFragment

/**
 * Creates the pages for the individual songs controlled by [SongPageFragment].
 */
class SongPagerAdapter(fragmentManager: FragmentManager, private val ids: List<String>) : FragmentStatePagerAdapter(fragmentManager) {

    override fun getItem(position: Int) = SongPageFragment.newInstance(ids[position])

    override fun getCount() = ids.size
}