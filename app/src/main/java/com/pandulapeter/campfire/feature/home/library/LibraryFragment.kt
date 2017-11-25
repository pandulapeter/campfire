package com.pandulapeter.campfire.feature.home.library

import android.support.v4.app.Fragment

/**
 * Displays the list of all available songs from the backend. The list is searchable and filterable
 * and contains headers. The list is also cached locally and automatically updated after a period or
 * manually using the pull-to-refresh gesture.
 *
 * Controlled by [LibraryViewModel].
 */
class LibraryFragment : Fragment()