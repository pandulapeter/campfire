package com.pandulapeter.campfire.feature.home.shared

import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.View
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.model.Song
import com.pandulapeter.campfire.data.repository.SongRepository
import com.pandulapeter.campfire.databinding.FragmentSongListBinding
import com.pandulapeter.campfire.feature.TopLevelFragment
import com.pandulapeter.campfire.old.feature.home.shared.SpacesItemDecoration
import com.pandulapeter.campfire.util.color
import com.pandulapeter.campfire.util.dimension
import com.pandulapeter.campfire.util.hideKeyboard
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.cancel
import org.koin.android.ext.android.inject
import kotlin.coroutines.experimental.CoroutineContext

abstract class SongListFragment<VM : SongListViewModel> : TopLevelFragment<FragmentSongListBinding, SongListViewModel>(R.layout.fragment_song_list), SongRepository.Subscriber {

    private var coroutine: CoroutineContext? = null
    private val songRepository by inject<SongRepository>()
    private val adapter = SongAdapter().apply {
        itemClickListener = { mainActivity.openDetailScreen(items[it].song.id) }
    }
    private var librarySongs = sequenceOf<Song>()

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.swipeRefreshLayout.run {
            setOnRefreshListener { songRepository.updateData() }
            setColorSchemeColors(context.color(R.color.accent))
        }
        binding.recyclerView.run {
            layoutManager = LinearLayoutManager(context)
            adapter = this@SongListFragment.adapter
            setHasFixedSize(true)
            addItemDecoration(SpacesItemDecoration(context.dimension(R.dimen.content_padding)))
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView?, dx: Int, dy: Int) {
                    if (dy > 0 && recyclerView?.isAnimating == false) {
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

    override fun onStop() {
        super.onStop()
        songRepository.unsubscribe(this)
        binding.swipeRefreshLayout.run {
            isRefreshing = false
            clearAnimation()
        }
    }

    protected abstract fun Sequence<Song>.createViewModels(): List<SongViewModel>

    protected fun updateAdapterItems(shouldScrollToTop: Boolean = false) {
        coroutine?.cancel()
        coroutine = async(UI) {
            adapter.shouldScrollToTop = shouldScrollToTop
            adapter.items = async(CommonPool) { librarySongs.createViewModels() }.await()
            coroutine = null
        }
    }

    override fun onSongRepositoryDataUpdated(data: List<Song>) {
        librarySongs = data.asSequence()
        updateAdapterItems()
    }

    override fun onSongRepositoryLoadingStateChanged(isLoading: Boolean) {
        binding.swipeRefreshLayout.isRefreshing = isLoading
    }

    override fun onSongRepositoryUpdateError() = showSnackbar(R.string.library_update_error, View.OnClickListener { songRepository.updateData() })
}