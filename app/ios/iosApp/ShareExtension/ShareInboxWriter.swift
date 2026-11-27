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
import UniformTypeIdentifiers

/// What a share came to, which is the one thing the extension's sheet says.
enum ShareOutcome {
    case saved(SavedShare)
    case unsupported
    case tooLarge
    case inboxFull
    case noSpace
    case failed
}

/// Copies what was shared into the App Group's inbox for the app to import the next time it is in front, which is all
/// the extension does: the library lives in the app's own container, out of an extension's reach, and importing here
/// would mean linking the whole Kotlin framework into a process with a fraction of the app's memory.
///
/// Each share is one folder, `ShareInbox/<time>-<UUID>`, written under a `.partial` name and renamed only once
/// everything is in it, so the app never reads half a share. In it, `files/<n>/<name>` per file, `texts/<n>.txt` per
/// text and `share.json`, which `decodeShareInboxManifest` in `:presentation` reads; keep the two in step. A page Safari
/// read adds its text's structure marks (`PageText.js`) to the manifest as `marks`, for the app's page model.
struct ShareInboxWriter {

    /// Nil in a build signed without the App Group, where there is nowhere to write to.
    private let containerURL: URL?

    init(bundle: Bundle = .main) {
        let group = bundle.object(forInfoDictionaryKey: "CampfireAppGroup") as? String
        containerURL = group.flatMap { FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: $0) }
    }

    func write(_ items: [NSExtensionItem]) async -> ShareOutcome {
        let attachments = items.flatMap { $0.attachments ?? [] }.compactMap(Attachment.init)
        let files = attachments.compactMap { if case let .file(provider, type) = $0 { return (provider, type) } else { return nil } }
        let links = attachments.compactMap { if case let .link(provider) = $0 { return provider } else { return nil } }
        let pages = attachments.compactMap { if case let .page(provider) = $0 { return provider } else { return nil } }
        var texts: [String] = []
        var marks: [Any]?
        var subject = Self.subject(of: items)
        // A text next to a file is its caption - the file's name, a "sent from" line - as it is on Android.
        if files.isEmpty, let page = await firstPage(pages) {
            if Self.isCampfireLink(page.address) {
                // Campfire's own landing page, whose link the app opens as the share it is, never as a page's text.
                texts = [page.address]
            } else {
                // Safari's own text of the page (PageText.js): the app crops it as a pasted page, nothing loaded again.
                if Int64(page.text.utf8.count) > ShareLimits.maxTextSize { return .tooLarge }
                texts = [page.text]
                marks = page.marks
                subject = subject ?? page.title
            }
        } else if files.isEmpty {
            for case let .text(provider) in attachments {
                switch await loadText(provider) {
                case let .text(text): texts.append(text)
                case .tooLarge: return .tooLarge
                case .failed: continue
                }
            }
            texts = texts.filter { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
            // Some apps send a page's title as a text beside its address, which is one line, where a chord sheet is many.
            let isOneLine = { (text: String) in !text.trimmingCharacters(in: .whitespacesAndNewlines).contains(where: \.isNewline) }
            // A Campfire share link goes to the app, which tells a real one from the rest and opens it. Only the first
            // is kept: the app opens one link per text, and would take a second one as a file of its own.
            if let link = texts.first(where: Self.isCampfireLink), texts.allSatisfy(isOneLine) {
                texts = [link.trimmingCharacters(in: .whitespacesAndNewlines)]
            } else if texts.allSatisfy(isOneLine), let link = await firstAddress(links, where: Self.isCampfireLink) {
                texts = [link]
            } else if texts.isEmpty || texts.allSatisfy(isOneLine) && !links.isEmpty {
                // Any other page shared by its address (a title beside it is only that) goes as its address, which the app
                // reads once by itself: a web view does not fit in an extension's memory. An address in a text goes as it is.
                if let address = await firstAddress(links, where: Self.isWebAddress) {
                    texts = [address]
                } else if texts.isEmpty {
                    return .unsupported
                }
            }
        }
        guard let containerURL else { return .failed }
        let fileManager = FileManager.default
        let inbox = containerURL.appendingPathComponent(Self.inboxName, isDirectory: true)
        do {
            try fileManager.createDirectory(at: inbox, withIntermediateDirectories: true)
            // An App Group container is part of the device backup, and a share restored onto another phone, long after
            // it was made, would be imported out of nowhere. The flag on the folder covers everything inside it.
            var values = URLResourceValues()
            values.isExcludedFromBackup = true
            var excludedInbox = inbox
            try excludedInbox.setResourceValues(values)
        } catch {
            return .failed
        }
        let waiting = Self.waitingShares(in: inbox)
        if waiting.count >= Self.maxWaitingShares { return .inboxFull }
        let name = "\(Self.timestamp())-\(UUID().uuidString)"
        let partial = inbox.appendingPathComponent(name + Self.partialSuffix, isDirectory: true)
        let outcome = await writeShare(files: files, texts: texts, marks: marks, subject: subject, id: name, into: partial, waitingBytes: waiting.bytes)
        guard case .saved = outcome else {
            try? fileManager.removeItem(at: partial)
            return outcome
        }
        do {
            try fileManager.moveItem(at: partial, to: inbox.appendingPathComponent(name, isDirectory: true))
        } catch {
            try? fileManager.removeItem(at: partial)
            return .failed
        }
        return outcome
    }

    /// The one way an extension can reach an app that is running: an iPad's other half of Split View, where Campfire stays
    /// active and so would not look at the inbox until it next comes to the front. Said after the share's notification is
    /// added, so the drain it starts finds that notification to take down.
    static func announce(bundle: Bundle = .main) {
        guard let name = notificationName(bundle: bundle) else { return }
        CFNotificationCenterPostNotification(CFNotificationCenterGetDarwinNotifyCenter(), CFNotificationName(name as CFString), nil, nil, true)
    }

    /// Named after the App Group the app and the extension share, so that a share into one install (the store app, or a
    /// debug build beside it, each with a group of its own) never wakes the other. The app listens under the same name,
    /// read from its own `Info.plist` (`iOSApp.swift`). Nil in a build signed without the group.
    static func notificationName(bundle: Bundle = .main) -> String? {
        (bundle.object(forInfoDictionaryKey: "CampfireAppGroup") as? String).map { "\($0).share-inbox" }
    }

    private func writeShare(files: [(NSItemProvider, String)], texts: [String], marks: [Any]?, subject: String?, id: String, into folder: URL, waitingBytes: Int64) async -> ShareOutcome {
        let fileManager = FileManager.default
        var remaining = ShareLimits.maxShareSize
        var filePaths: [String] = []
        var textPaths: [String] = []
        var fileNames: [String] = []
        do {
            try fileManager.createDirectory(at: folder, withIntermediateDirectories: true)
            for (index, (provider, type)) in files.enumerated() {
                switch await copyFile(provider, typeIdentifier: type, to: folder, index: index, remaining: remaining) {
                case let .copied(path, shownName, counted):
                    filePaths.append(path)
                    fileNames.append(shownName)
                    remaining -= counted
                case .tooLarge: return .tooLarge
                case .noSpace: return .noSpace
                case .failed: return .failed
                }
            }
            for (index, text) in texts.enumerated() {
                let data = Data(text.utf8)
                if Int64(data.count) > remaining { return .tooLarge }
                remaining -= Int64(data.count)
                let path = "texts/\(index).txt"
                try fileManager.createDirectory(at: folder.appendingPathComponent("texts", isDirectory: true), withIntermediateDirectories: true)
                try data.write(to: folder.appendingPathComponent(path))
                textPaths.append(path)
            }
            // The marks only help the crop, so a share without room for them (or with marks the page broke) goes without.
            var markedLines: [Any]?
            if let marks, JSONSerialization.isValidJSONObject(marks), let data = try? JSONSerialization.data(withJSONObject: marks), Int64(data.count) <= remaining {
                remaining -= Int64(data.count)
                markedLines = marks
            }
            if waitingBytes + ShareLimits.maxShareSize - remaining > ShareLimits.maxWaitingBytes { return .inboxFull }
            var manifest: [String: Any] = ["version": Self.manifestVersion, "files": filePaths, "texts": textPaths]
            manifest["subject"] = subject
            manifest["marks"] = markedLines
            try JSONSerialization.data(withJSONObject: manifest).write(to: folder.appendingPathComponent("share.json"))
            let name: SharedName = fileNames.count > 1 ? .files(fileNames.count) : (fileNames.first ?? subject).map(SharedName.titled) ?? .unnamed
            return .saved(SavedShare(id: id, name: name))
        } catch {
            return .failed
        }
    }

    private enum Copy {
        /// [shownName] is the name the sender gave, as the user saw it. [counted] is what the file takes of the share's
        /// bounds: nothing for an archive the app moves on into its staging folder, which is bounded by the device's free
        /// space instead.
        case copied(path: String, shownName: String, counted: Int64)
        case tooLarge
        case noSpace
        case failed
    }

    /// Copied rather than read, since a share may be 40 MB and the extension has about 120 MB to live in, and from inside
    /// the completion handler, after which the file it was handed is gone. The size is the resource value's, not
    /// `attributesOfItem`'s, which is a file timestamp API with a privacy reason to give.
    private func copyFile(_ provider: NSItemProvider, typeIdentifier: String, to folder: URL, index: Int, remaining: Int64) async -> Copy {
        let suggestedName = provider.suggestedName
        return await withCheckedContinuation { continuation in
            _ = provider.loadFileRepresentation(forTypeIdentifier: typeIdentifier) { url, _ in
                guard let url, let size = (try? url.resourceValues(forKeys: [.fileSizeKey]))?.fileSize.map(Int64.init) else {
                    return continuation.resume(returning: .failed)
                }
                let name = Self.fileName(suggestedName: suggestedName, url: url, typeIdentifier: typeIdentifier)
                let isStaged = ShareLimits.isStagedArchive(name)
                guard size <= ShareLimits.maxSize(ofFileNamed: name), isStaged || size <= remaining else {
                    return continuation.resume(returning: .tooLarge)
                }
                // The one copy of an archive an extension cannot avoid (the app moves it on), so checked against the
                // device's free space first, leaving it the margin the app's own staging keeps.
                if isStaged, !Self.hasRoom(for: size, at: folder) {
                    return continuation.resume(returning: .noSpace)
                }
                let path = "files/\(index)/\(name)"
                do {
                    try FileManager.default.createDirectory(at: folder.appendingPathComponent("files/\(index)", isDirectory: true), withIntermediateDirectories: true)
                    try FileManager.default.copyItem(at: url, to: folder.appendingPathComponent(path))
                    continuation.resume(returning: .copied(path: path, shownName: suggestedName ?? url.lastPathComponent, counted: isStaged ? 0 : size))
                } catch {
                    continuation.resume(returning: .failed)
                }
            }
        }
    }

    private enum LoadedText {
        case text(String)
        case tooLarge
        case failed
    }

    /// Selected text arrives as a string, but an app may hand over data or a file instead, all of it read as UTF-8.
    private func loadText(_ provider: NSItemProvider) async -> LoadedText {
        await withCheckedContinuation { continuation in
            provider.loadItem(forTypeIdentifier: UTType.plainText.identifier) { item, _ in
                let text: String?
                switch item {
                case let string as String: text = string
                case let attributed as NSAttributedString: text = attributed.string
                case let data as Data: text = String(data: data, encoding: .utf8)
                case let url as URL where url.isFileURL:
                    let size = (try? url.resourceValues(forKeys: [.fileSizeKey]))?.fileSize ?? 0
                    if Int64(size) > ShareLimits.maxTextSize { return continuation.resume(returning: .tooLarge) }
                    text = try? String(contentsOf: url, encoding: .utf8)
                default: text = nil
                }
                guard let text else { return continuation.resume(returning: .failed) }
                continuation.resume(returning: Int64(text.utf8.count) > ShareLimits.maxTextSize ? .tooLarge : .text(text))
            }
        }
    }

    private struct SharedPage {
        let text: String
        let title: String?
        let address: String
        /// One per line of [text] split at `\n`, as `PageText.js` wrote them; nil where it wrote none.
        let marks: [Any]?
    }

    /// The first page Safari read for the extension (`PageText.js`, under `NSExtensionJavaScriptPreprocessingResultsKey`)
    /// that has any text; an empty one is shared by its address instead, which the app reads itself.
    private func firstPage(_ providers: [NSItemProvider]) async -> SharedPage? {
        for provider in providers {
            let page: SharedPage? = await withCheckedContinuation { continuation in
                provider.loadItem(forTypeIdentifier: UTType.propertyList.identifier) { item, _ in
                    let results = (item as? NSDictionary)?[NSExtensionJavaScriptPreprocessingResultsKey] as? NSDictionary
                    guard let text = results?["text"] as? String, !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
                        return continuation.resume(returning: nil)
                    }
                    let title = (results?["title"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines)
                    let address = (results?["address"] as? String) ?? ""
                    let marks = results?["marks"] as? [Any]
                    continuation.resume(returning: SharedPage(text: text, title: title?.isEmpty == false ? title : nil, address: address, marks: marks))
                }
            }
            if let page { return page }
        }
        return nil
    }

    /// The first address a URL attachment carries that [accepted] takes, read and never fetched. An app hands it over as
    /// a URL, its text, or its data representation.
    private func firstAddress(_ providers: [NSItemProvider], where accepted: (String) -> Bool) async -> String? {
        for provider in providers {
            let address: String? = await withCheckedContinuation { continuation in
                provider.loadItem(forTypeIdentifier: UTType.url.identifier) { item, _ in
                    switch item {
                    case let url as URL where !url.isFileURL: continuation.resume(returning: url.absoluteString)
                    case let string as String: continuation.resume(returning: string)
                    case let data as Data:
                        let url = URL(dataRepresentation: data, relativeTo: nil)
                        continuation.resume(returning: url?.isFileURL == false ? url?.absoluteString : nil)
                    default: continuation.resume(returning: nil)
                    }
                }
            }
            if let address, accepted(address) { return address.trimmingCharacters(in: .whitespacesAndNewlines) }
        }
        return nil
    }

    /// What one attachment is to the import. A text whose provider names it as a file the import reads - a `.txt` in
    /// Files - is that file, so that it keeps its name.
    private enum Attachment {
        case file(NSItemProvider, typeIdentifier: String)
        case text(NSItemProvider)
        case link(NSItemProvider)
        case page(NSItemProvider)

        init?(_ provider: NSItemProvider) {
            let types = provider.registeredTypeIdentifiers
            if types.contains(where: { Self.conforms($0, to: UTType.propertyList.identifier) }) {
                self = .page(provider)
            } else if let type = types.first(where: { type in ShareLimits.fileTypes.contains { Self.conforms(type, to: $0) } }) {
                self = .file(provider, typeIdentifier: type)
            } else if let type = types.first(where: { Self.conforms($0, to: UTType.plainText.identifier) }) {
                self = provider.suggestedName.map(ShareLimits.isImportable) == true ? .file(provider, typeIdentifier: type) : .text(provider)
            } else if types.contains(where: { Self.conforms($0, to: UTType.url.identifier) && !Self.conforms($0, to: UTType.fileURL.identifier) }) {
                self = .link(provider)
            } else {
                return nil
            }
        }

        private static func conforms(_ type: String, to other: String) -> Bool {
            guard let type = UTType(type), let other = UTType(other) else { return false }
            return type.conforms(to: other)
        }
    }

    /// The name the sender gave, written the way every name the library writes is, as far as it can be without the app's
    /// Kotlin: lowercase words joined by underscores. The rest of that rule (`LibraryFiles.normalizedName`: accents
    /// folded, abbreviations, a length cap by words) is applied by the app to whatever the shared file's song comes to be
    /// called. Separators, control characters, the invisible spaces and the bidirectional controls that would show the
    /// name backwards go, and so do leading dots, which would hide the file or name a directory (`..`). The extension its
    /// type has is added where the name has none the import knows - a provider often names a file without one - and the
    /// name is kept well inside the file system's 255 bytes, cut by whole characters with its extension intact.
    private static func fileName(suggestedName: String?, url: URL, typeIdentifier: String) -> String {
        let scalars = (suggestedName ?? url.lastPathComponent).unicodeScalars.map { scalar -> Unicode.Scalar in
            "/\\:".unicodeScalars.contains(scalar) || scalar.properties.generalCategory == .control || invisibleScalars.contains(scalar.value) ? " " : scalar
        }
        var name = String(String.UnicodeScalarView(scalars)).split(whereSeparator: \.isWhitespace).joined(separator: "_").lowercased()
        while let first = name.first, first == "." || first == "_" { name.removeFirst() }
        if name.isEmpty { name = "Shared" }
        if !ShareLimits.isImportable(name), let fileExtension = UTType(typeIdentifier)?.preferredFilenameExtension {
            name += ".\(fileExtension)"
        }
        let fileExtension = (name as NSString).pathExtension
        var base = (name as NSString).deletingPathExtension
        // Whole characters at a time, so that no character is cut in half.
        while base.utf8.count + fileExtension.utf8.count + 1 > maxFileNameBytes, !base.isEmpty { base.removeLast() }
        while base.hasSuffix("_") { base.removeLast() }
        return fileExtension.isEmpty ? base : "\(base.isEmpty ? "Shared" : base).\(fileExtension)"
    }

    private static func subject(of items: [NSExtensionItem]) -> String? {
        items.lazy.compactMap { $0.attributedTitle?.string.trimmingCharacters(in: .whitespacesAndNewlines) }.first { !$0.isEmpty }
    }

    /// Whether the volume of [url] has room for [size] bytes beside what `ImportLimits.minFreeSpaceFor` keeps free, by
    /// the capacity for important use, the figure the app's staging asks too (a disk space API with a declared reason).
    private static func hasRoom(for size: Int64, at url: URL) -> Bool {
        guard let available = (try? url.resourceValues(forKeys: [.volumeAvailableCapacityForImportantUsageKey]))?.volumeAvailableCapacityForImportantUsage else {
            return false
        }
        return available - ShareLimits.minFreeSpace(for: size) >= size
    }

    /// The shares the app has not imported yet, counted towards the inbox's bounds; a `.partial` one is not a share.
    /// An archive the app moves on into its staging folder is not counted, as it is not counted towards a share's.
    private static func waitingShares(in inbox: URL) -> (count: Int, bytes: Int64) {
        let fileManager = FileManager.default
        let shares = ((try? fileManager.contentsOfDirectory(at: inbox, includingPropertiesForKeys: nil)) ?? []).filter { !$0.lastPathComponent.hasSuffix(partialSuffix) }
        let bytes = shares.reduce(Int64(0)) { total, share in
            let files = (fileManager.enumerator(at: share, includingPropertiesForKeys: [.fileSizeKey])?.compactMap { $0 as? URL } ?? [])
                .filter { !ShareLimits.isStagedArchive($0.lastPathComponent) }
            return total + files.reduce(Int64(0)) { $0 + Int64((try? $1.resourceValues(forKeys: [.fileSizeKey]))?.fileSize ?? 0) }
        }
        return (shares.count, bytes)
    }

    /// Sortable as text, which is how the app takes the shares oldest first.
    private static func timestamp() -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(identifier: "UTC")
        formatter.dateFormat = "yyyyMMdd'T'HHmmssSSS'Z'"
        return formatter.string(from: Date())
    }

    /// A web page's address and nothing else, which the app reads once by itself; any other kind of address is no page.
    static func isWebAddress(_ text: String) -> Bool {
        text.trimmingCharacters(in: .whitespacesAndNewlines).range(of: #"^https?://\S+$"#, options: [.regularExpression, .caseInsensitive]) != nil
    }

    /// `ShareLinks`' address regex in `:data:model`, copied by hand as the landing page's is (keep the three in step),
    /// without its bare payload, which is no address and so passes on as any text does. The fragment is required: the
    /// web app's own address without one is a page like any other. The payload's grammar is the app's to check; this
    /// only tells a link that may be Campfire's from another page's.
    static func isCampfireLink(_ text: String) -> Bool {
        text.trimmingCharacters(in: .whitespacesAndNewlines).range(of: #"^(?:https://campfire-songbook\.com/(?:s|app)/(?:\?[^#\s]*)?#|campfire://s/)\S+$"#, options: .regularExpression) != nil
    }

    private static let inboxName = "ShareInbox"
    private static let partialSuffix = ".partial"
    private static let manifestVersion = 1
    private static let maxWaitingShares = 20
    private static let maxFileNameBytes = 200

    /// Zero-width spaces, the soft hyphen and the bidirectional marks, embeddings, overrides and isolates.
    private static let invisibleScalars = Set<UInt32>([0x00AD, 0x061C, 0x200B, 0x200E, 0x200F, 0x2060, 0xFEFF])
        .union(0x202A...0x202E).union(0x2066...0x2069)
}

/// `ImportLimits` in `:data:model`, written again: a share the import would refuse is refused here, before it is copied.
private enum ShareLimits {

    static let maxTextSize: Int64 = 8 << 20
    static let maxDocumentSize: Int64 = 16 << 20
    /// `ImportLimits.MAX_STAGED_ARCHIVE_SIZE`: the app moves an archive on into its staging folder, so one is bounded by
    /// that and the device's free space, not by the share's bounds or the inbox's.
    static let maxArchiveSize: Int64 = 16 << 30
    /// `ImportLimits.MAX_PICKED_IMAGE_FILE_SIZE`: a photo the app shrinks into a song's picture once it has it.
    static let maxPictureSize: Int64 = 40 << 20
    /// What one share copies, all its files together: as much as its largest file may be, so that one picture fits.
    static let maxShareSize: Int64 = maxPictureSize
    static let maxWaitingBytes: Int64 = 64 << 20

    /// The types the activation rule in `Info.plist` names besides plain text (pictures included), and the ChordPro family
    /// and ABC tunes the app declares.
    static let fileTypes = [
        "com.adobe.pdf",
        "org.openxmlformats.wordprocessingml.document",
        "public.zip-archive",
        "public.json",
        "com.pandulapeter.campfire.songbookpro-backup",
        "org.chordpro.cho",
        "org.chordpro.chordpro",
        "org.abcnotation.abc",
        "public.png",
        "public.jpeg",
        "com.compuserve.gif",
        "org.webmproject.webp",
        "public.heic",
        "public.heif",
    ]

    /// `LibraryFiles.IMPORTABLE_EXTENSIONS`, without the legacy Word format the extension is not offered for, and without
    /// `.onsong`, which has no system type the activation rule could name; JSON (JustChords' `.justchords` included), ABC,
    /// and OpenSong's `.osts` sets are read with the text limit, as a song is.
    private static let songExtensions = ["cho", "chopro", "chordpro", "crd", "chord", "pro", "abc", "osts", "txt", "json", "justchords"]
    private static let documentExtensions = ["pdf", "docx"]
    private static let archiveExtensions = ["zip", "sbpbackup", "sbp", "osb", "osbs", "ossb"]
    /// The pictures the activation rule names, which the app opens a new song on, shrinking one too large for a song and
    /// converting a HEIC or HEIF photo.
    private static let pictureExtensions = ["png", "jpg", "jpeg", "gif", "webp", "heic", "heif"]

    static func isImportable(_ name: String) -> Bool {
        (songExtensions + documentExtensions + archiveExtensions + pictureExtensions).contains(fileExtension(of: name))
    }

    static func isStagedArchive(_ name: String) -> Bool {
        archiveExtensions.contains(fileExtension(of: name))
    }

    /// `ImportLimits.minFreeSpaceFor` on a device: a quarter of what is needed, at most 512 MiB.
    static func minFreeSpace(for needed: Int64) -> Int64 {
        min(needed / 4, 512 << 20)
    }

    static func maxSize(ofFileNamed name: String) -> Int64 {
        let fileExtension = fileExtension(of: name)
        if archiveExtensions.contains(fileExtension) { return maxArchiveSize }
        if documentExtensions.contains(fileExtension) { return maxDocumentSize }
        if pictureExtensions.contains(fileExtension) { return maxPictureSize }
        return maxTextSize
    }

    private static func fileExtension(of name: String) -> String {
        (name as NSString).pathExtension.lowercased()
    }
}
