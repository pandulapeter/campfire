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

/// What the sheet shows, set once the share is written.
final class ShareState: ObservableObject {
    /// Nil while the share is still being written.
    @Published var outcome: ShareOutcome?
    /// Whether a saved share's notification was posted, set before [outcome].
    var isNotified = false
}

/// One line of state, one under it, and Done. A saved share closes the sheet by itself (`ShareViewController`); a
/// refusal stays until Done, since its second line is the instruction.
struct ShareView: View {
    @ObservedObject var state: ShareState
    let onDone: () -> Void

    var body: some View {
        NavigationView {
            VStack(spacing: 12) {
                if let outcome = state.outcome {
                    Text(outcome.title).font(.headline)
                    if let detail = outcome.detail(isNotified: state.isNotified) {
                        Text(detail).foregroundColor(.secondary)
                    }
                } else {
                    ProgressView()
                    Text(NSLocalizedString("share_saving", comment: "")).font(.headline)
                }
            }
            .multilineTextAlignment(.center)
            .padding(24)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .navigationTitle("Campfire")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    // Colored by hand: the toolbar draws its buttons in the label color, not the accent color.
                    Button(NSLocalizedString("share_done", comment: ""), action: onDone)
                        .foregroundColor(Color("AccentColor"))
                        .disabled(state.outcome == nil)
                }
            }
        }
        .navigationViewStyle(.stack)
    }
}

private extension ShareOutcome {
    var title: String {
        switch self {
        case let .saved(share): return share.name.waitingText
        case .unsupported: return NSLocalizedString("share_unsupported", comment: "")
        case .tooLarge: return NSLocalizedString("share_too_large", comment: "")
        case .inboxFull: return NSLocalizedString("share_inbox_full", comment: "")
        case .noSpace: return NSLocalizedString("share_no_space", comment: "")
        case .failed: return NSLocalizedString("share_failed", comment: "")
        }
    }

    func detail(isNotified: Bool) -> String? {
        switch self {
        case let .saved(share):
            switch (isNotified, share.name.isSeveral) {
            case (true, false): return NSLocalizedString("share_open_notification", comment: "")
            case (true, true): return NSLocalizedString("share_open_notification_several", comment: "")
            case (false, false): return NSLocalizedString("share_open_app", comment: "")
            case (false, true): return NSLocalizedString("share_open_app_several", comment: "")
            }
        case .unsupported: return NSLocalizedString("share_unsupported_detail", comment: "")
        case .tooLarge: return NSLocalizedString("share_too_large_detail", comment: "")
        case .inboxFull: return NSLocalizedString("share_inbox_full_detail", comment: "")
        case .noSpace: return NSLocalizedString("share_no_space_detail", comment: "")
        case .failed: return nil
        }
    }
}
