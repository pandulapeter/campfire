package com.pandulapeter.campfire.feature.main.collections

import com.pandulapeter.campfire.data.model.remote.Collection
import com.pandulapeter.campfire.feature.main.home.home.HomeItemViewModel

sealed class CollectionListItemViewModel : HomeItemViewModel {

    data class CollectionViewModel(val collection: Collection, private val newText: String) : CollectionListItemViewModel() {

        override fun getItemId() = collection.id.hashCode().toLong()

        val alertText = if (collection.isNew) newText else null
    }

    data class HeaderViewModel(val title: String) : CollectionListItemViewModel() {

        override fun getItemId() = title.hashCode().toLong()
    }
}