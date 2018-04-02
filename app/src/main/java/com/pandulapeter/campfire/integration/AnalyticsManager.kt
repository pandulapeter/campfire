package com.pandulapeter.campfire.integration

import com.pandulapeter.campfire.data.persistence.PreferenceDatabase

class AnalyticsManager(private val preferenceDatabase: PreferenceDatabase) {

    private fun track() {
        if (preferenceDatabase.shouldShareUsageData) {

        }
    }
}