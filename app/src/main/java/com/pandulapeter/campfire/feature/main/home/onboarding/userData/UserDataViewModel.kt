package com.pandulapeter.campfire.feature.main.home.onboarding.userData

import com.pandulapeter.campfire.data.persistence.PreferenceDatabase
import com.pandulapeter.campfire.feature.shared.CampfireViewModel
import com.pandulapeter.campfire.integration.AnalyticsManager
import com.pandulapeter.campfire.util.mutableLiveDataOf

class UserDataViewModel(
    private val preferenceDatabase: PreferenceDatabase,
    private val analyticsManager: AnalyticsManager
) : CampfireViewModel() {

    val isAnalyticsEnabled = mutableLiveDataOf(preferenceDatabase.shouldShareUsageData) { onAnalyticsEnabledChanged(it) }
    val isCrashReportingEnabled = mutableLiveDataOf(preferenceDatabase.shouldShareCrashReports) { onCrashReportingEnabledChanged(it) }

    private fun onAnalyticsEnabledChanged(isAnalyticsEnabled: Boolean) {
        preferenceDatabase.shouldShareUsageData = isAnalyticsEnabled
        analyticsManager.updateCollectionEnabledState()
        if (isAnalyticsEnabled) {
            analyticsManager.onConsentGiven(System.currentTimeMillis())
        }
    }

    private fun onCrashReportingEnabledChanged(isCrashReportingEnabled: Boolean) {
        preferenceDatabase.shouldShareCrashReports = isCrashReportingEnabled
    }
}