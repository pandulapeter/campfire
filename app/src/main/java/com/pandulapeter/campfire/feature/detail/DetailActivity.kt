package com.pandulapeter.campfire.feature.detail

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v4.view.ViewPager
import com.pandulapeter.campfire.DetailBinding
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.repository.HistoryRepository
import com.pandulapeter.campfire.data.repository.SongInfoRepository
import com.pandulapeter.campfire.feature.shared.CampfireActivity
import javax.inject.Inject

/**
 * Displays a horizontal pager with the songs that are in the currently selected playlist (or a
 * single song if no playlist is available).
 *
 * Controlled by [DetailViewModel].
 */
class DetailActivity : CampfireActivity<DetailBinding, DetailViewModel>(R.layout.activity_detail) {
    @Inject lateinit var songInfoRepository: SongInfoRepository
    @Inject lateinit var historyRepository: HistoryRepository
    override val viewModel by lazy { DetailViewModel(supportFragmentManager, intent.ids, songInfoRepository, historyRepository) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Setup the toolbar.
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        // Setup the view pager.
        //TODO: Pay attention to instance state saving.
        binding.viewPager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrollStateChanged(state: Int) = Unit

            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) = Unit

            override fun onPageSelected(position: Int) = viewModel.onPageSelected(position)
        })
        if (intent.ids.indexOf(intent.currentId) == 0) {
            binding.viewModel?.onPageSelected(0)
        } else {
            binding.viewPager.run { post { setCurrentItem(intent.ids.indexOf(intent.currentId), false) } }
        }
    }

    companion object {
        private const val CURRENT_ID = "current_id"
        private const val IDS = "ids"
        private val Intent.currentId
            get() = getStringExtra(CURRENT_ID)
        private val Intent.ids
            get() = getStringArrayExtra(IDS).toList()

        fun getStartIntent(context: Context, currentId: String, ids: List<String> = listOf(currentId)): Intent = Intent(context, DetailActivity::class.java)
            .putExtra(CURRENT_ID, currentId)
            .putExtra(IDS, ids.toTypedArray())
    }
}