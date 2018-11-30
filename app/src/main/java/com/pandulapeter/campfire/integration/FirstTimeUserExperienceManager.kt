package com.pandulapeter.campfire.integration

import androidx.annotation.Keep
import com.pandulapeter.campfire.data.persistence.PreferenceDatabase
import kotlin.properties.Delegates
import kotlin.reflect.KProperty

@Keep
class FirstTimeUserExperienceManager(private val preferenceDatabase: PreferenceDatabase) {

    var historyCompleted by Delegates.observable(preferenceDatabase.ftuxHistoryCompleted) { _: KProperty<*>, old: Boolean, new: Boolean ->
        if (old != new) {
            preferenceDatabase.ftuxHistoryCompleted = new
        }
    }
    var playlistSwipeCompleted by Delegates.observable(preferenceDatabase.ftuxPlaylistSwipeCompleted) { _: KProperty<*>, old: Boolean, new: Boolean ->
        if (old != new) {
            preferenceDatabase.ftuxPlaylistSwipeCompleted = new
        }
    }
    var playlistDragCompleted by Delegates.observable(preferenceDatabase.ftuxPlaylistDragCompleted) { _: KProperty<*>, old: Boolean, new: Boolean ->
        if (old != new) {
            preferenceDatabase.ftuxPlaylistDragCompleted = new
        }
    }
    var managePlaylistsSwipeCompleted by Delegates.observable(preferenceDatabase.ftuxManagePlaylistsSwipeCompleted) { _: KProperty<*>, old: Boolean, new: Boolean ->
        if (old != new) {
            preferenceDatabase.ftuxManagePlaylistsSwipeCompleted = new
        }
    }
    var managePlaylistsDragCompleted by Delegates.observable(preferenceDatabase.ftuxManagePlaylistsDragCompleted) { _: KProperty<*>, old: Boolean, new: Boolean ->
        if (old != new) {
            preferenceDatabase.ftuxManagePlaylistsDragCompleted = new
        }
    }
    var manageDownloadsCompleted by Delegates.observable(preferenceDatabase.ftuxManageDownloadsCompleted) { _: KProperty<*>, old: Boolean, new: Boolean ->
        if (old != new) {
            preferenceDatabase.ftuxManageDownloadsCompleted = new
        }
    }
    var playlistPagerSwipeCompleted by Delegates.observable(preferenceDatabase.ftuxPlaylistPagerSwipeCompleted) { _: KProperty<*>, old: Boolean, new: Boolean ->
        if (old != new) {
            preferenceDatabase.ftuxPlaylistPagerSwipeCompleted = new
        }
    }
    var fontSizePinchCompleted by Delegates.observable(preferenceDatabase.fontSizePinchCompleted) { _: KProperty<*>, old: Boolean, new: Boolean ->
        if (old != new) {
            preferenceDatabase.fontSizePinchCompleted = new
        }
    }

    fun resetAll() {
        historyCompleted = false
        playlistSwipeCompleted = false
        playlistDragCompleted = false
        managePlaylistsSwipeCompleted = false
        managePlaylistsDragCompleted = false
        manageDownloadsCompleted = false
        playlistPagerSwipeCompleted = false
        fontSizePinchCompleted = false
    }
}