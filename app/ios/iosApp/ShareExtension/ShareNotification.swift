/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
import Foundation
import UserNotifications

/// A share written to the inbox: its folder's name and what the user would call what they shared.
struct SavedShare {
    /// The share's folder in the inbox, which is also its notification's identifier, so that a second share adds a
    /// notification rather than replacing the first one's.
    let id: String
    let name: SharedName
}

/// What was shared, as the sheet and the notification name it.
enum SharedName {
    /// A page's or a subject's title, or the one file's name.
    case titled(String)
    case files(Int)
    /// A text with no title, or an address the app reads by itself.
    case unnamed

    /// "Them" rather than "it" in the line under the title.
    var isSeveral: Bool {
        if case .files = self { return true } else { return false }
    }

    var waitingText: String {
        switch self {
        case let .titled(title): return String(format: NSLocalizedString("share_waiting_named", comment: ""), Self.shortened(title))
        case let .files(count): return String.localizedStringWithFormat(NSLocalizedString("share_waiting_files", comment: ""), count)
        case .unnamed: return NSLocalizedString("share_waiting_unnamed", comment: "")
        }
    }

    /// A page's title often carries the site's name and a slogan, which would push the sentence around it off the sheet.
    private static func shortened(_ title: String) -> String {
        title.count > maxTitleLength ? String(title.prefix(maxTitleLength - 1)).trimmingCharacters(in: .whitespaces) + "…" : title
    }

    private static let maxTitleLength = 60
}

/// The notification that takes the user from the sharing app to Campfire, which an extension may not open by itself.
/// Opening it brings the app to the front, which drains the inbox, so it carries no address to route.
///
/// It is posted only where the user has already allowed Campfire's notifications (the app asks when a sync run starts):
/// an extension asking would put the question in the middle of somebody else's app. Posted from here, it belongs to the
/// containing app, whose name and icon it shows and whose settings decide it.
enum ShareNotification {

    /// Whether the notification was added, which is what the sheet's second line says. Awaited for at most a second:
    /// the extension is ended once its request completes, and must not be held open by the notification center.
    static func post(_ share: SavedShare) async -> Bool {
        let center = UNUserNotificationCenter.current()
        let settings = await center.notificationSettings()
        guard settings.authorizationStatus == .authorized || settings.authorizationStatus == .provisional,
              settings.alertSetting == .enabled || settings.notificationCenterSetting == .enabled else { return false }
        let content = UNMutableNotificationContent()
        content.body = share.name.waitingText
        content.threadIdentifier = threadIdentifier
        let request = UNNotificationRequest(identifier: share.id, content: content, trigger: nil)
        return await withCheckedContinuation { continuation in
            let once = Once(continuation)
            center.add(request) { error in once.resume(error == nil) }
            DispatchQueue.main.asyncAfter(deadline: .now() + timeoutSeconds) { once.resume(false) }
        }
    }

    /// What the app takes the share notifications down by once it is in front (`IosShareInbox.kt`); keep the two in step.
    private static let threadIdentifier = "share-inbox"
    private static let timeoutSeconds = 1.0

    /// Resumes its continuation with the first answer, the add's completion or the timeout, and ignores the other. Sendable
    /// by its lock, which guards the one thing it holds.
    private final class Once: @unchecked Sendable {
        private var continuation: CheckedContinuation<Bool, Never>?
        private let lock = NSLock()

        init(_ continuation: CheckedContinuation<Bool, Never>) {
            self.continuation = continuation
        }

        func resume(_ value: Bool) {
            lock.lock()
            let continuation = self.continuation
            self.continuation = nil
            lock.unlock()
            continuation?.resume(returning: value)
        }
    }
}
