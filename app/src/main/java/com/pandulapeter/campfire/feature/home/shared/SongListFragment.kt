package com.pandulapeter.campfire.feature.home.shared

import android.databinding.ViewDataBinding
import android.os.Bundle
import android.support.annotation.LayoutRes
import android.support.v4.widget.SwipeRefreshLayout
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.View
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.model.Song
import com.pandulapeter.campfire.data.repository.SongRepository
import com.pandulapeter.campfire.feature.CampfireFragment
import com.pandulapeter.campfire.old.feature.home.shared.SpacesItemDecoration
import com.pandulapeter.campfire.util.color
import com.pandulapeter.campfire.util.dimension
import com.pandulapeter.campfire.util.hideKeyboard
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import org.koin.android.ext.android.inject

abstract class SongListFragment<T : ViewDataBinding>(@LayoutRes layoutResourceId: Int) : CampfireFragment<T>(layoutResourceId), SongRepository.Subscriber {

    abstract val recyclerView: RecyclerView
    abstract val swipeRefreshLayout: SwipeRefreshLayout
    private val songRepository by inject<SongRepository>()
    private val adapter = SongAdapter().apply {
        itemClickListener = { mainActivity.openDetailScreen(items[it].song.id) }
    }
    private var librarySongs = listOf<Song>()

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        swipeRefreshLayout.run {
            setColorSchemeColors(context.color(R.color.accent))
            setOnRefreshListener { songRepository.updateData() }
        }
        recyclerView.run {
            layoutManager = LinearLayoutManager(context)
            adapter = this@SongListFragment.adapter
            setHasFixedSize(true)
            addItemDecoration(SpacesItemDecoration(context.dimension(R.dimen.content_padding)))
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView?, dx: Int, dy: Int) {
                    if (dy > 0) {
                        hideKeyboard(activity?.currentFocus)
                    }
                }
            })
        }
    }

    override fun onStart() {
        super.onStart()
        songRepository.subscribe(this)
    }

    override fun onPause() {
        super.onPause()
        swipeRefreshLayout.run {
            isRefreshing = false
            destroyDrawingCache()
            clearAnimation()
        }
    }

    override fun onStop() {
        super.onStop()
        songRepository.unsubscribe(this)
    }

    protected abstract fun List<Song>.createViewModels(): List<SongViewModel>

    protected fun updateAdapterItems() {
        async(UI) {
            adapter.items = async(CommonPool) { librarySongs.createViewModels() }.await()
        }
    }

    override fun onSongRepositoryDataUpdated(data: List<Song>) {
        librarySongs = data
        updateAdapterItems()
    }

    override fun onSongRepositoryLoadingStateChanged(isLoading: Boolean) {
        swipeRefreshLayout.isRefreshing = isLoading
    }

    override fun onSongRepositoryUpdateError() = showSnackbar(R.string.library_update_error, View.OnClickListener { songRepository.updateData() })
}