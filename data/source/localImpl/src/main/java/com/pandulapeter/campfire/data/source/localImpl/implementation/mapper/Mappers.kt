package com.pandulapeter.campfire.data.source.localImpl.implementation.mapper

private const val SEPARATOR = ","

internal fun List<String>.mapToString() = joinToString(SEPARATOR)

internal fun String.mapToList() = split(SEPARATOR)