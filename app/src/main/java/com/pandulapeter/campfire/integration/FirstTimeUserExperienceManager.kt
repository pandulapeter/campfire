package com.pandulapeter.campfire.integration

import com.pandulapeter.campfire.data.persistence.PreferenceDatabase
import kotlin.properties.Delegates
import kotlin.reflect.KProperty

class FirstTimeUserExperienceManager(private val preferenceDatabase: PreferenceDatabase) {

    var historyCompleted by Delegates.observable(preferenceDatabase.ftuxHistoryCompleted) { _: KProperty<*>, old: Boolean, new: Boolean ->
        if (old != new) {
            preferenceDatabase.ftuxHistoryCompleted = new
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
    var playlistSwipeCompleted by Delegates.observable(preferenceDatabase.ftuxPlaylistSwipeCompleted) { _: KProperty<*>, old: Boolean, new: Boolean ->
        if (old != new) {
            preferenceDatabase.ftuxPlaylistSwipeCompleted = new
        }
    }

    fun resetAll() {
        historyCompleted = false
        manageDownloadsCompleted = false
        playlistSwipeCompleted = false
    }
}