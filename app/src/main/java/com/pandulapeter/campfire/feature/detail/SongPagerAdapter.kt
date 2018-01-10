package com.pandulapeter.campfire.feature.detail

import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentStatePagerAdapter
import android.util.SparseArray
import android.view.ViewGroup
import com.pandulapeter.campfire.feature.detail.songPage.SongPageFragment

/**
 * Creates the pages for the individual songs controlled by [SongPageFragment].
 */
class SongPagerAdapter(fragmentManager: FragmentManager, private val ids: List<String>) : FragmentStatePagerAdapter(fragmentManager) {
    private val referenceMap = SparseArray<SongPageFragment>()

    fun getItemAt(position: Int): SongPageFragment = referenceMap[position]

    override fun getItem(position: Int) = SongPageFragment.newInstance(ids[position])

    override fun getCount() = ids.size

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        referenceMap.put(position, super.instantiateItem(container, position) as SongPageFragment)
        return referenceMap[position]
    }

    override fun destroyItem(container: ViewGroup, position: Int, obj: Any) {
        super.destroyItem(container, position, obj)
        referenceMap.remove(position)
    }
}