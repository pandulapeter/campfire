package com.pandulapeter.campfire.feature.home.library

import android.databinding.ObservableBoolean
import com.pandulapeter.campfire.data.repository.ChangeListener
import com.pandulapeter.campfire.data.repository.SongInfoRepository
import com.pandulapeter.campfire.feature.home.shared.SongInfoAdapter

/**
 * Handles events and logic for [LibraryFragment].
 */
class LibraryViewModel(private val songInfoRepository: SongInfoRepository) {

    val adapter = SongInfoAdapter()
    val shouldShowErrorSnackbar = ObservableBoolean(false)
    val isLoading = ObservableBoolean(false)

    init {
        update(false)
    }

    fun update(isForceRefresh: Boolean) {
        if (!isLoading.get()) {
            isLoading.set(true)
            songInfoRepository.getDataSet(ChangeListener(
                onNext = {
                    adapter.songInfoList = it
                },
                onComplete = {
                    isLoading.set(false)
                },
                onError = {
                    shouldShowErrorSnackbar.set(true)
                    isLoading.set(false)
                }), isForceRefresh)
        }
    }
}
