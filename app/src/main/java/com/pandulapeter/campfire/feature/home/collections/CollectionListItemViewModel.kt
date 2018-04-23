package com.pandulapeter.campfire.feature.home.collections

import com.pandulapeter.campfire.data.model.remote.Collection

sealed class CollectionListItemViewModel {

    abstract fun getItemId(): Long

    data class CollectionViewModel(val collection: Collection) : CollectionListItemViewModel() {

        override fun getItemId() = collection.id.hashCode().toLong()
    }

    data class HeaderViewModel(val title: String) : CollectionListItemViewModel() {

        override fun getItemId() = title.hashCode().toLong()
    }
}