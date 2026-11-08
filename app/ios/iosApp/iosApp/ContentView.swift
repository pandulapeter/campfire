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
import SwiftUI
import ComposeApp

struct ComposeView: UIViewControllerRepresentable {

    func makeUIViewController(context: Context) -> UIViewController {
        ShortcutListingController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

/// Hosts the Compose UI, filling it, to list the app's shortcuts in the Command-hold overlay of an iPad with a
/// keyboard. `keyCommands` is a category property UIKit reads from the responder chain, which Kotlin cannot override,
/// so this is Swift. The commands are only listed, never performed (`listedKeyCommands` on the Kotlin side): Compose
/// hears every key itself, so nothing can perform their action, and UIKit then delivers the press as it would without
/// them.
final class ShortcutListingController: UIViewController {

    private var listedCommands: [UIKeyCommand] = []

    override var keyCommands: [UIKeyCommand]? { listedCommands }

    override func viewDidLoad() {
        super.viewDidLoad()
        let content = CampfireViewControllerKt.CampfireViewController { [weak self] commands in
            self?.listedCommands = commands
        }
        addChild(content)
        content.view.frame = view.bounds
        content.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.addSubview(content.view)
        content.didMove(toParent: self)
    }

    // What the Compose controller says of the status bar, the home indicator, and the edge gestures is still its own.
    override var childForStatusBarStyle: UIViewController? { children.first }
    override var childForStatusBarHidden: UIViewController? { children.first }
    override var childForHomeIndicatorAutoHidden: UIViewController? { children.first }
    override var childForScreenEdgesDeferringSystemGestures: UIViewController? { children.first }

    override func canPerformAction(_ action: Selector, withSender sender: Any?) -> Bool {
        action == Self.listedAction ? false : super.canPerformAction(action, withSender: sender)
    }

    private static let listedAction = Selector(("campfireListedShortcut:"))
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea() // The shared Compose scaffold applies the system bar insets itself.
    }
}
