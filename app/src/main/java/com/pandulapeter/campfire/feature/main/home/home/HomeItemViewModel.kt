package com.pandulapeter.campfire.feature.main.home.home

import com.pandulapeter.campfire.data.model.remote.Collection
import com.pandulapeter.campfire.data.model.remote.Song

sealed class HomeItemViewModel {

    abstract fun getItemId(): Long

    data class HeaderViewModel(val title: String) : HomeItemViewModel() {

        override fun getItemId() = title.hashCode().toLong()
    }

    data class CollectionViewModel(val collection: Collection, private val newText: String) : HomeItemViewModel() {

        override fun getItemId() = collection.id.hashCode().toLong()

        val alertText = if (collection.isNew) newText else null
    }

    data class SongViewModel(val song: Song, private val newText: String) : HomeItemViewModel() {

        override fun getItemId() = song.id.hashCode().toLong()

        val alertText = if (song.isNew) newText else null
    }
}