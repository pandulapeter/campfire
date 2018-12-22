package com.pandulapeter.campfire.feature.main.home.onboarding.contentLanguage

import androidx.lifecycle.MutableLiveData
import com.pandulapeter.campfire.data.model.local.Language
import com.pandulapeter.campfire.data.model.remote.Collection
import com.pandulapeter.campfire.data.model.remote.Song
import com.pandulapeter.campfire.data.persistence.PreferenceDatabase
import com.pandulapeter.campfire.data.repository.CollectionRepository
import com.pandulapeter.campfire.data.repository.SongRepository
import com.pandulapeter.campfire.feature.shared.CampfireViewModel
import com.pandulapeter.campfire.feature.shared.InteractionBlocker
import com.pandulapeter.campfire.feature.shared.widget.StateLayout
import com.pandulapeter.campfire.util.mutableLiveDataOf

class ContentLanguageViewModel(
    private val preferenceDatabase: PreferenceDatabase,
    private val collectionRepository: CollectionRepository,
    private val songRepository: SongRepository,
    interactionBlocker: InteractionBlocker
) : CampfireViewModel(interactionBlocker), CollectionRepository.Subscriber, SongRepository.Subscriber {

    private var areCollectionsLoading = true
    private var areSongsLoading = true
    val state = mutableLiveDataOf(StateLayout.State.LOADING)
    val shouldShowError = mutableLiveDataOf(false)
    val shouldShowExplicit = mutableLiveDataOf(preferenceDatabase.shouldShowExplicit) { onShouldShowExplicitChanged(it) }
    var languages = MutableLiveData<List<Language>?>()
    var selectedLanguageCount = 0

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

    override fun onCollectionRepositoryUpdateError() {
        state.value = StateLayout.State.ERROR
    }

    override fun onSongRepositoryDataUpdated(data: List<Song>) = Unit

    override fun onSongRepositoryLoadingStateChanged(isLoading: Boolean) {
        areSongsLoading = isLoading && songRepository.languages.isEmpty()
        refreshLoadingState()
        if (!areSongsLoading) {
            updateLanguages()
        }
    }

    override fun onSongRepositoryUpdateError() {
        state.value = StateLayout.State.ERROR
    }

    fun startLoading() {
        state.value = StateLayout.State.LOADING
        collectionRepository.updateData()
        songRepository.updateData()
    }

    private fun onShouldShowExplicitChanged(shouldShowExplicit: Boolean) {
        preferenceDatabase.shouldShowExplicit = shouldShowExplicit
    }

    private fun refreshLoadingState() {
        if ((areCollectionsLoading || areSongsLoading) && state.value != StateLayout.State.ERROR) {
            state.postValue(StateLayout.State.LOADING)
        }
    }

    private fun updateLanguages() {
        if (!areCollectionsLoading && !areSongsLoading) {
            languages.postValue(collectionRepository.languages.union(songRepository.languages).toList())
            state.postValue(StateLayout.State.NORMAL)
        }
    }
}