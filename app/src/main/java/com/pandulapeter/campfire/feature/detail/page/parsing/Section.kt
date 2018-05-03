package com.pandulapeter.campfire.feature.detail.page.parsing

import android.content.Context

data class Section(private val sectionType: SectionType, private val index: Int, val startPosition: Int) {

    fun getName(context: Context) = "${context.getString(sectionType.nameResourceId)}${if (index == 0) "" else " $index"}\n"
}