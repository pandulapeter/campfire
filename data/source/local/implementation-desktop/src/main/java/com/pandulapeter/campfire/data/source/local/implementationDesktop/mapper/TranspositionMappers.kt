package com.pandulapeter.campfire.data.source.local.implementationDesktop.mapper

import com.pandulapeter.campfire.data.source.local.implementationDesktop.model.TranspositionEntity

internal fun List<TranspositionEntity>.toModel() = associate { it.songId to it.transposition }

internal fun Map<String, Int>.toEntities() = map { (songId, transposition) ->
    TranspositionEntity(
        songId = songId,
        transposition = transposition
    )
}
