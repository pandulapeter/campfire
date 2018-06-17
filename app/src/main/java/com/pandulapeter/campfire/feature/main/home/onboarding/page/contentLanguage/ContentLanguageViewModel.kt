package com.pandulapeter.campfire.feature.main.home.onboarding.page.contentLanguage

import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import com.pandulapeter.campfire.data.model.local.Language
import com.pandulapeter.campfire.data.model.remote.Collection
import com.pandulapeter.campfire.data.model.remote.Song
import com.pandulapeter.campfire.data.persistence.PreferenceDatabase
import com.pandulapeter.campfire.data.repository.CollectionRepository
import com.pandulapeter.campfire.data.repository.SongRepository
import com.pandulapeter.campfire.feature.shared.CampfireViewModel
import com.pandulapeter.campfire.feature.shared.widget.StateLayout
import com.pandulapeter.campfire.util.onPropertyChanged
import org.koin.android.ext.android.inject

class ContentLanguageViewModel(private val onLanguagesLoaded: (List<Language>) -> Unit) : CampfireViewModel(), CollectionRepository.Subscriber, SongRepository.Subscriber {

    private val preferenceDatabase by inject<PreferenceDatabase>()
    private val collectionRepository by inject<CollectionRepository>()
    private val songRepository by inject<SongRepository>()
    private var areCollectionsLoading = true
    private var areSongsLoading = true
    private var collectionLanguages = listOf<Language>()
    private var songLanguages = listOf<Language>()
    val state = ObservableField<StateLayout.State>(StateLayout.State.LOADING)
    val shouldShowExplicit = ObservableBoolean(preferenceDatabase.shouldShowExplicit)

    init {
        shouldShowExplicit.onPropertyChanged { preferenceDatabase.shouldShowExplicit = it }
    }

    override fun subscribe() {
        collectionRepository.subscribe(this)
        songRepository.subscribe(this)
    }

    override fun unsubscribe() {
        collectionRepository.unsubscribe(this)
        songRepository.unsubscribe(this)
    }

    override fun onCollectionsUpdated(data: List<Collection>) {
        collectionLanguages = collectionRepository.languages
        updateLanguages()
    }

    override fun onCollectionsLoadingStateChanged(isLoading: Boolean) {
        areCollectionsLoading = isLoading
        refreshLoadingState()
        updateLanguages()
    }

    override fun onCollectionRepositoryUpdateError() = state.set(StateLayout.State.ERROR)

    override fun onSongRepositoryDataUpdated(data: List<Song>) {
        songLanguages = songRepository.languages
        updateLanguages()
    }

    override fun onSongRepositoryLoadingStateChanged(isLoading: Boolean) {
        areSongsLoading = isLoading
        refreshLoadingState()
        updateLanguages()
    }

    override fun onSongRepositoryUpdateError() = state.set(StateLayout.State.ERROR)

    fun startLoading() {
        state.set(StateLayout.State.LOADING)
        collectionRepository.updateData()
        songRepository.updateData()
    }

    private fun refreshLoadingState() {
        if ((areCollectionsLoading || areSongsLoading) && state.get() != StateLayout.State.ERROR) {
            state.set(StateLayout.State.LOADING)
        }
    }

    private fun updateLanguages() {
        if (!areCollectionsLoading && !areSongsLoading) {
            onLanguagesLoaded(collectionLanguages.union(songLanguages).toList())
            state.set(StateLayout.State.NORMAL)
        }
    }
}