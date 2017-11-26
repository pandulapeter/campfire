package com.pandulapeter.campfire.feature.detail

import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentPagerAdapter
import com.pandulapeter.campfire.feature.detail.page.PageFragment

/**
 * Creates the pages for the individual songs.
 */
class SongPagerAdapter(fragmentManager: FragmentManager, private val ids: List<String>) : FragmentPagerAdapter(fragmentManager) {
    override fun getItem(position: Int) = PageFragment.newInstance(ids[position])

    override fun getCount() = ids.size
}