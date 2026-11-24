/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
import UIKit
import ComposeApp

/// Hands the scene the delegate Home Screen quick actions arrive at, which a SwiftUI app has no other way to hear:
/// SwiftUI keeps creating the window and keeps answering `onOpenURL` behind a scene delegate of the app's own.
final class QuickActionsAppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        configurationForConnecting connectingSceneSession: UISceneSession,
        options: UIScene.ConnectionOptions
    ) -> UISceneConfiguration {
        let configuration = UISceneConfiguration(name: nil, sessionRole: connectingSceneSession.role)
        configuration.delegateClass = QuickActionsSceneDelegate.self
        return configuration
    }
}

/// A quick action that launches the app arrives with the scene's connection, one pressed while it runs on its own; both
/// are the app link the Kotlin side put in the item (`IosQuickActions.kt`), opened as any other URL the app is handed.
final class QuickActionsSceneDelegate: NSObject, UIWindowSceneDelegate {
    func scene(_ scene: UIScene, willConnectTo session: UISceneSession, options connectionOptions: UIScene.ConnectionOptions) {
        if let item = connectionOptions.shortcutItem { open(item) }
    }

    func windowScene(
        _ windowScene: UIWindowScene,
        performActionFor shortcutItem: UIApplicationShortcutItem,
        completionHandler: @escaping (Bool) -> Void
    ) {
        completionHandler(open(shortcutItem))
    }

    @discardableResult
    private func open(_ item: UIApplicationShortcutItem) -> Bool {
        guard let link = item.userInfo?[IosQuickActionsKt.QUICK_ACTION_LINK_KEY] as? String, let url = URL(string: link) else { return false }
        IosFileImportKt.openUrl(url: url)
        return true
    }
}
