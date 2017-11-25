package com.pandulapeter.campfire.feature.home.downloaded

import android.support.v4.app.Fragment

/**
 * Displays the list of all downloaded songs. The list is searchable and filterable and contains
 * headers. The items are automatically updated after a period or manually using the pull-to-refresh
 * gesture. Items can be removed using the swipe-to-dismiss gesture.
 *
 * Controlled by [DownloadedViewModel].
 */
class DownloadedFragment : Fragment()