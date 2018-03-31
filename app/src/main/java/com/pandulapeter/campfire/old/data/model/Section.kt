package com.pandulapeter.campfire.old.data.model

import android.content.Context

/**
 * Represents a single section.
 */
data class Section(private val sectionType: SectionType, private val index: Int, val startPosition: Int) {

    fun getName(context: Context) = "${context.getString(sectionType.nameResourceId)}${if (index == 0) "" else " $index"}\n"
}