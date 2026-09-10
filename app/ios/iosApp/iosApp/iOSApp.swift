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
import ComposeApp

@main
struct iOSApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
                // Everything iOS hands the app arrives here: a ChordPro file opened with Campfire, whether it was
                // already running or was launched by the open itself, and the redirect back from the sync
                // service's consent page. Which is which is decided on the Kotlin side.
                .onOpenURL { url in IosFileImportKt.openUrl(url: url) }
        }
    }
}
