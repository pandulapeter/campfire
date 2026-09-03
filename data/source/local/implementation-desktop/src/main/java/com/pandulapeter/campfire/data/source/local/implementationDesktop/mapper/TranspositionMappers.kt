package com.pandulapeter.campfire.data.source.local.implementationDesktop.mapper

import com.pandulapeter.campfire.data.model.domain.TranspositionKey
import com.pandulapeter.campfire.data.source.local.implementationDesktop.model.TranspositionEntity

internal fun List<TranspositionEntity>.toModel() = associate { entity ->
    TranspositionKey(
        songId = entity.songId,
        setlistId = entity.setlistId.takeIf { it != TranspositionEntity.NO_SETLIST_ID }
    ) to entity.transposition
}

internal fun Map<TranspositionKey, Int>.toEntities() = map { (key, transposition) ->
    TranspositionEntity(
        songId = key.songId,
        setlistId = key.setlistId ?: TranspositionEntity.NO_SETLIST_ID,
        transposition = transposition
    )
}
