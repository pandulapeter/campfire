package com.pandulapeter.campfire.feature.shared.recycler.viewModel

import com.pandulapeter.campfire.data.model.remote.Collection

data class CollectionItemViewModel(val collection: Collection, private val newText: String) : ItemViewModel {

    val alertText = if (collection.isNew) newText else null

    override fun getItemId() = collection.id.hashCode().toLong()
}