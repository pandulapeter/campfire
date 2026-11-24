/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
import SwiftUI
import WidgetKit

/// The home screen widgets. They read only the snapshot the app writes into the App Group's container, and the app asks
/// WidgetKit to reload them whenever it writes a new one.
@main
struct CampfireWidgets: WidgetBundle {
    var body: some Widget {
        RandomPicksWidget()
        SetlistWidget()
    }
}
