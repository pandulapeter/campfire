package com.pandulapeter.campfire.feature.detail.songPage

import android.content.Context
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.util.color

/**
 * Parses raw song texts.
 */
class SongParser(context: Context) {
    private val accentColor = context.color(R.color.accent)
    private val verse = context.getString(R.string.detail_verse)
    private val bridge = context.getString(R.string.detail_bridge)
    private val solo = context.getString(R.string.detail_solo)
    private val chorus = context.getString(R.string.detail_chorus)

    //TODO: Implement chord parsing.
    //TODO: Remove line breaks if they occur multiple times in a row.
    fun parseSong(text: String, shouldShowChords: Boolean): SpannableString {
        val sectionNames = mutableListOf<Pair<Int, Int>>()
        var offset = 0
        val parsedText = (if (shouldShowChords) text else text.replace(Regex("\\[(.*?)\\]"), ""))
            .replace(Regex("\\{(.*?)\\}"), {
                val name = when (it.value[1].toLowerCase()) {
                    'v' -> verse
                    'b' -> bridge
                    's' -> solo
                    'c' -> chorus
                    else -> ""
                }
                val result = it.value.indexOf("_").let { index ->
                    if (index >= 0) {
                        "$name ${it.value.substring(index + 1).removeSuffix("}")}"
                    } else {
                        name
                    }
                }
                sectionNames.add(offset + it.range.first to offset + it.range.first + result.length)
                offset += result.length - it.value.length
                result
            })
        return SpannableString(parsedText).apply {
            sectionNames.forEach { setSpan(ForegroundColorSpan(accentColor), it.first, it.second, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
        }
    }
}
