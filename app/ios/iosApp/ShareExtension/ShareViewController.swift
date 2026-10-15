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
import UIKit

/// The extension's principal class (`NSExtensionPrincipalClass` in its `Info.plist`, no storyboard): writes what was
/// shared into the inbox and says what came of it. It never opens Campfire - only a Today widget may open its app, and
/// the ways around that are not API - so the app picks the share up the next time it is in front.
///
/// Its view is shown in a sheet of the sharing app's, which an extension cannot size or replace (its own presentation
/// style is ignored: it is hosted in a remote view), so the sheet is kept short-lived instead: a saved share closes it
/// by itself once it has been read.
final class ShareViewController: UIViewController {

    private let state = ShareState()

    override func viewDidLoad() {
        super.viewDidLoad()
        let host = UIHostingController(rootView: ShareView(state: state) { [weak self] in self?.close() })
        addChild(host)
        host.view.frame = view.bounds
        host.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.addSubview(host.view)
        host.didMove(toParent: self)
        let items = extensionContext?.inputItems.compactMap { $0 as? NSExtensionItem } ?? []
        Task {
            let outcome = await ShareInboxWriter().write(items)
            state.outcome = outcome
            if outcome == .saved {
                try? await Task.sleep(nanoseconds: Self.savedNanoseconds)
                close()
            }
        }
    }

    /// Once only: Done tapped while the saved share is about to close the sheet by itself would complete twice.
    private func close() {
        guard !isClosed else { return }
        isClosed = true
        extensionContext?.completeRequest(returningItems: nil)
    }

    private var isClosed = false

    /// Long enough to read "Saved to Campfire" and the line under it.
    private static let savedNanoseconds: UInt64 = 2_000_000_000
}
