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
