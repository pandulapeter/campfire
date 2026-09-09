import SwiftUI
import ComposeApp

@main
struct iOSApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
                // A ChordPro file opened with Campfire arrives here, whether the app was already running or was
                // launched by the open itself.
                .onOpenURL { url in IosFileImportKt.importFile(url: url) }
        }
    }
}
