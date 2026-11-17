/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
import BackgroundTasks
import ComposeApp
import Foundation

/// The continued processing tasks an import is kept working by once the app is left (the Kotlin side decides when:
/// `IosImportKeeper`). Swift because the API is iOS 26's, and `@available` is what weakly links it for the iOS 15.3 the
/// app still supports.
///
/// Every task gets a fresh identifier under the Info plist's one `$(PRODUCT_BUNDLE_IDENTIFIER).import.*` entry,
/// registered just before it is submitted and never reused: registering an identifier twice, or submitting one that was
/// never registered, raises an Objective-C exception that ends the app, and a wildcard cannot be registered itself.
/// Continued tasks, unlike the other kinds, may be registered after launch, which is what makes this possible.
@available(iOS 26.0, *)
final class ContinuedImportTaskScheduler: ContinuedImportTasks {

    /// One submitted task, with what it shows, kept until it ends or the next one is submitted.
    private final class Submission {
        let identifier: String
        let title: String
        let onExpired: () -> Void
        var subtitle: String
        var completed: Int64 = 0
        var total: Int64 = -1
        var task: BGContinuedProcessingTask?
        var isFinished = false

        /// The expiration handler may run off the main thread, so the task is completed under a lock, and only once.
        private let completionLock = NSLock()
        private var isTaskCompleted = false

        func completeTask(success: Bool) {
            completionLock.lock()
            defer { completionLock.unlock() }
            guard let task, !isTaskCompleted else { return }
            isTaskCompleted = true
            task.setTaskCompleted(success: success)
        }

        init(identifier: String, title: String, subtitle: String, onExpired: @escaping () -> Void) {
            self.identifier = identifier
            self.title = title
            self.subtitle = subtitle
            self.onExpired = onExpired
        }

        /// A total that is not positive is indeterminate, which a progress says with a negative count.
        func show() {
            guard let task else { return }
            task.updateTitle(title, subtitle: subtitle)
            task.progress.totalUnitCount = total > 0 ? total : -1
            task.progress.completedUnitCount = total > 0 ? min(max(completed, 0), total) : -1
        }
    }

    private var current: Submission?

    func submit(title: String, subtitle: String, onExpired: @escaping () -> Void) -> Bool {
        finish()
        let identifier = "\(Bundle.main.bundleIdentifier ?? "com.pandulapeter.campfire").import.\(UUID().uuidString)"
        let submission = Submission(identifier: identifier, title: title, subtitle: subtitle, onExpired: onExpired)
        let isRegistered = BGTaskScheduler.shared.register(forTaskWithIdentifier: identifier, using: .main) { [weak self] task in
            // A task launched after the import went idle, or after a newer one was submitted, has nothing to do.
            guard let task = task as? BGContinuedProcessingTask, !submission.isFinished, self?.current === submission else {
                task.setTaskCompleted(success: true)
                return
            }
            submission.task = task
            // Called when the system ends the task early, and when the user stops it from the system's progress. Ended
            // here and now, since a task left running past this gets the app killed; the import is only asked to stop.
            task.expirationHandler = { [weak self] in
                submission.completeTask(success: false)
                DispatchQueue.main.async {
                    guard let self, self.current === submission, !submission.isFinished else { return }
                    submission.isFinished = true
                    self.current = nil
                    submission.onExpired()
                }
            }
            submission.show()
        }
        guard isRegistered else { return false }
        let request = BGContinuedProcessingTaskRequest(identifier: identifier, title: title, subtitle: subtitle)
        // Refused at once rather than queued: an import waiting for a slot it may never get is better kept by background
        // time, which the Kotlin side falls back on.
        request.strategy = .fail
        current = submission
        do {
            try BGTaskScheduler.shared.submit(request)
        } catch {
            print("Could not submit the import's continued processing task: \(error.localizedDescription)")
            current = nil
            submission.isFinished = true
            return false
        }
        return true
    }

    func update(subtitle: String, completed: Int64, total: Int64) {
        guard let current else { return }
        current.subtitle = subtitle
        current.completed = completed
        current.total = total
        current.show()
    }

    func finish() {
        guard let submission = current else { return }
        current = nil
        submission.isFinished = true
        if submission.task != nil {
            submission.completeTask(success: true)
        } else {
            BGTaskScheduler.shared.cancel(taskRequestWithIdentifier: submission.identifier)
        }
    }
}
