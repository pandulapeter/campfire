package com.pandulapeter.campfire.feature.main.home.onboarding.contentLanguage

import androidx.databinding.ObservableBoolean
import androidx.databinding.ObservableField
import com.pandulapeter.campfire.data.model.local.Language
import com.pandulapeter.campfire.data.model.remote.Collection
import com.pandulapeter.campfire.data.model.remote.Song
import com.pandulapeter.campfire.data.persistence.PreferenceDatabase
import com.pandulapeter.campfire.data.repository.CollectionRepository
import com.pandulapeter.campfire.data.repository.SongRepository
import com.pandulapeter.campfire.feature.shared.deprecated.OldCampfireViewModel
import com.pandulapeter.campfire.feature.shared.widget.StateLayout
import com.pandulapeter.campfire.util.onPropertyChanged
import org.koin.android.ext.android.inject

class ContentLanguageViewModel(private val onLanguagesLoaded: (List<Language>) -> Unit) : OldCampfireViewModel(), CollectionRepository.Subscriber, SongRepository.Subscriber {

    private val preferenceDatabase by inject<PreferenceDatabase>()
    private val collectionRepository by inject<CollectionRepository>()
    private val songRepository by inject<SongRepository>()
    private var areCollectionsLoading = true
    private var areSongsLoading = true
    val state = ObservableField<StateLayout.State>(StateLayout.State.LOADING)
    val shouldShowError = ObservableBoolean()
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

    override fun onCollectionsUpdated(data: List<Collection>) = Unit

    override fun onCollectionsLoadingStateChanged(isLoading: Boolean) {
        areCollectionsLoading = isLoading && collectionRepository.languages.isEmpty()
        refreshLoadingState()
        if (!areCollectionsLoading) {
            updateLanguages()
        }
    }

    override fun onCollectionRepositoryUpdateError() = state.set(StateLayout.State.ERROR)

    override fun onSongRepositoryDataUpdated(data: List<Song>) = Unit

    override fun onSongRepositoryLoadingStateChanged(isLoading: Boolean) {
        areSongsLoading = isLoading && songRepository.languages.isEmpty()
        refreshLoadingState()
        if (!areSongsLoading) {
            updateLanguages()
        }
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
            onLanguagesLoaded(collectionRepository.languages.union(songRepository.languages).toList())
            state.set(StateLayout.State.NORMAL)
        }
    }
}