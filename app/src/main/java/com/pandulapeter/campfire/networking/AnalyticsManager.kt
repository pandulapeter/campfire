package com.pandulapeter.campfire.networking

import com.pandulapeter.campfire.data.persistence.PreferenceDatabase

class AnalyticsManager(private val preferenceDatabase: PreferenceDatabase) {

    private fun track() {
        if (preferenceDatabase.shouldShareUsageData) {

        }
    }
}