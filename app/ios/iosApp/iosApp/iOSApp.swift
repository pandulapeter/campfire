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
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            ContentView()
                // Everything iOS hands the app arrives here: a ChordPro file opened with Campfire, whether it was
                // already running or was launched by the open itself, and the redirect back from the sync
                // service's consent page. Which is which is decided on the Kotlin side.
                .onOpenURL { url in IosFileImportKt.openUrl(url: url) }
        }
        // A file that arrives from AirDrop or Mail is a copy iOS leaves in Documents/Inbox, which the Files app
        // shows next to the library. The ones that were read are deleted as they are read; this is for the rest.
        .onChange(of: scenePhase) { phase in
            if phase == .background {
                IosFileImportKt.cleanImportInbox()
            }
        }
    }
}
