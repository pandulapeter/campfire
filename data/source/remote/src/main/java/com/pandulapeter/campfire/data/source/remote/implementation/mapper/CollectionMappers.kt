package com.pandulapeter.campfire.data.source.remote.implementation.mapper

import com.pandulapeter.campfire.data.model.domain.Collection
import com.pandulapeter.campfire.data.source.remote.implementation.model.CollectionResponse
import com.pandulapeter.campfire.data.source.remote.implementation.model.exception.DataValidationException

internal fun CollectionResponse.toModel() = try {
    Collection(
        id = id.toCollectionId(),
        title = title.toCollectionTitle(),
        description = description.toCollectionDescription(),
        thumbnailUrl = thumbnailUrl.toCollectionThumbnailUrl(),
        songIds = songIds.toSongIds(),
        isPublic = isPublic.toSongIsPublic()
    )
} catch (exception: DataValidationException) {
    println(exception.message)
    null
}

internal fun String?.toCollectionId() = if (isNullOrBlank()) throw DataValidationException("Missing collection ID.") else replace(" ", "")

private fun String?.toCollectionTitle() = if (isNullOrBlank()) throw DataValidationException("Missing collection title.") else this

private fun String?.toCollectionDescription() = if (isNullOrBlank()) throw DataValidationException("Missing collection description.") else this

private fun String?.toCollectionThumbnailUrl() = this ?: throw DataValidationException("Missing collection thumbnail URL.")

private fun String?.toSongIds() = orEmpty().replace(" ", "").split(",").ifEmpty { throw DataValidationException("Empty collection.") }

private fun Boolean?.toSongIsPublic() = this == true