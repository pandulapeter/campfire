package com.pandulapeter.campfire.old.feature.shared

import com.pandulapeter.campfire.integration.AnalyticsManager

/**
 * Base class for all view models in the app. Handles events and logic for subclasses of [CampfireFragment].
 */
abstract class CampfireViewModel(protected val analyticsManager: AnalyticsManager)