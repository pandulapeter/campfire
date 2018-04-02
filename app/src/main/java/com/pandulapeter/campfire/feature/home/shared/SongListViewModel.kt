package com.pandulapeter.campfire.feature.home.shared

import android.databinding.ObservableBoolean
import android.support.annotation.CallSuper
import com.pandulapeter.campfire.data.model.remote.Song
import com.pandulapeter.campfire.data.repository.SongRepository
import com.pandulapeter.campfire.feature.shared.CampfireViewModel
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.cancel
import org.koin.android.ext.android.inject
import kotlin.coroutines.experimental.CoroutineContext

abstract class SongListViewModel : CampfireViewModel(), SongRepository.Subscriber {

    private val songRepository by inject<SongRepository>()
    private var librarySongs = sequenceOf<Song>()
    private var coroutine: CoroutineContext? = null
    val adapter = SongAdapter()
    val shouldShowErrorSnackbar = ObservableBoolean()
    val isLoading = ObservableBoolean()

    @CallSuper
    override fun subscribe() = songRepository.subscribe(this)

    @CallSuper
    override fun unsubscribe() = songRepository.unsubscribe(this)

    override fun onSongRepositoryDataUpdated(data: List<Song>) {
        librarySongs = data.asSequence()
        updateAdapterItems()
    }

    override fun onSongRepositoryLoadingStateChanged(isLoading: Boolean) = this.isLoading.set(isLoading)

    override fun onSongRepositoryUpdateError() = shouldShowErrorSnackbar.set(true)

    fun updateData() = songRepository.updateData()

    protected abstract fun Sequence<Song>.createViewModels(): List<SongViewModel>

    protected fun updateAdapterItems(shouldScrollToTop: Boolean = false) {
        coroutine?.cancel()
        coroutine = async(UI) {
            adapter.shouldScrollToTop = shouldScrollToTop
            adapter.items = async(CommonPool) { librarySongs.createViewModels() }.await()
            coroutine = null
        }
    }
}