package com.pandulapeter.campfire.data.source.remote.implementationJvm.mapper

import com.pandulapeter.campfire.data.model.domain.Collection
import com.pandulapeter.campfire.data.source.remote.implementationJvm.model.CollectionResponse
import com.pandulapeter.campfire.data.source.remote.implementationJvm.model.exception.DataValidationException

internal fun CollectionResponse.toModel() = try {
    Collection(
        id = id.toCollectionId(),
        title = title.toCollectionTitle(),
        description = description.toCollectionDescription(),
        thumbnailUrl = thumbnailUrl.toCollectionThumbnailUrl(),
        songIds = songIds.toCollectionSongIds(),
        isPublic = isPublic.toCollectionIsPublic()
    )
} catch (exception: DataValidationException) {
    println(exception.message)
    null
}

private fun String?.toCollectionId() = toId("Missing collection ID.")

private fun String?.toCollectionTitle() = toText("Missing collection title.")

private fun String?.toCollectionDescription() = toText("Missing collection description.")

private fun String?.toCollectionThumbnailUrl() = toUrl("Missing collection thumbnail URL.")

private fun String?.toCollectionSongIds() = orEmpty().replace(" ", "").split(",").ifEmpty { throw DataValidationException("Empty collection.") }

private fun Boolean?.toCollectionIsPublic() = toBoolean()