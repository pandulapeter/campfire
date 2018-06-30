package com.pandulapeter.campfire.feature.main.home.home

import com.pandulapeter.campfire.data.model.remote.Collection

sealed class HomeItemViewModel {

    abstract fun getItemId(): Long

    data class CollectionViewModel(val collection: Collection, private val newText: String) : HomeItemViewModel() {

        override fun getItemId() = collection.id.hashCode().toLong()

        val alertText = if (collection.isNew) newText else null
    }

    data class HeaderViewModel(val title: String) : HomeItemViewModel() {

        override fun getItemId() = title.hashCode().toLong()
    }
}