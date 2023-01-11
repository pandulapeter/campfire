package com.pandulapeter.campfire.data.source.local.implementationDesktop.mapper

private const val SEPARATOR = ","

internal fun List<String>.mapToString() = joinToString(SEPARATOR)

internal fun String.mapToList() = split(SEPARATOR)