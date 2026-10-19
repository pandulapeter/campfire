<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Require the fields of a Dropbox answer that a decision rests on, so an empty answer is not read as an empty folder

**Challenged:** sound — every field made required is required in Dropbox's own schema (`ListFolderResult.entries`/`cursor`/`has_more`, also for `list_folder/continue`; `FileMetadata.rev`; `FullAccount.account_id`; `.tag` of every union, `delete_batch`, `delete_batch/check`, and each entry), and every fixture in the module's tests carries them. Probe at HEAD: `{}` on `list_folder` decodes to an empty listing. With plan 06, `DropboxFileMetadata.size` stays `Long? = null`; nothing constructs these models in code but `DropboxDeleteBatchEntry(tag = …)`, which names its tag. With plan 05 a renewal answered without `access_token` is retried three times before the run ends, which the token test still sees as `SyncNetworkException`.

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all
**Lane:** T
**Files:** `data/source/remote/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/dropbox/DropboxModels.kt`,
`data/source/remote/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/dropbox/DropboxSyncProvider.kt`,
`data/source/remote/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/dropbox/DropboxTokens.kt`,
`data/source/remote/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/dropbox/DropboxFilesTest.kt`,
`data/source/remote/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/dropbox/DropboxTokensTest.kt`,
`data/source/remote/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/dropbox/DropboxDeletionTest.kt`,
`data/source/remote/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/dropbox/DropboxTestService.kt`,
`data/source/remote/implementation/CLAUDE.md`

Runs after plan 07 (an undecodable answer is a `SyncNetworkException`).

## Problem

Every model in `dropbox/DropboxModels.kt` is defaulted (its KDoc, `:19-22`: "Everything is defaulted and unknown keys
are ignored, so that a field added on the other side never turns into a parse failure"):

```kotlin
internal data class DropboxListFolderResponse(
    val entries: List<DropboxEntry> = emptyList(),
    val cursor: String = "",
    @SerialName("has_more") val hasMore: Boolean = false,
)
…
internal data class DropboxFileMetadata(… val rev: String = "", …)
…
internal data class DropboxTokenResponse(@SerialName("access_token") val accessToken: String = "", …)
```

So a JSON answer that is not Dropbox's but is JSON (`{}`, `{"error":"blocked"}` from a filtering proxy or an API
gateway) decodes:

- on `list_folder` → an empty listing. With an index, `DeletionGuard` stops the run (local deletions of most or all of
  the index) and asks whether everything was deleted remotely, because of a middlebox; a user who confirms deletes the
  library on this device. Without an index (a first sync) the run uploads every file as new into a folder that may hold
  them, and every upload conflicts.
- on an upload → `RemoteWriteResult.Written(revision = "")`, an index entry with an empty revision, and the next run
  sees the file as changed remotely.
- on the token endpoint → an access token `""`, stored, so every following call carries `Bearer ` and is refused, which
  after one forced renewal reads as `SyncAuthorizationException`.

Defaults are right for fields that may be added or are optional; they are wrong for the ones the answer exists to carry.

## Fix

- Make required (no default): `DropboxListFolderResponse.entries`, `.cursor`, `.hasMore`; `DropboxFileMetadata.rev`;
  `DropboxTokenResponse.accessToken`; `DropboxDeleteBatchResponse.tag`; `DropboxDeleteBatchEntry.tag`;
  `DropboxAccountResponse.accountId`. A missing one throws `MissingFieldException` (a `SerializationException`), which
  plan 07's mapping turns into `SyncNetworkException`; for the download header, `revisionOf`/`downloadMetadataOf`
  already treats an undecodable header as "the download named no revision". Keep `ignoreUnknownKeys` and keep every
  other field defaulted (`DropboxEntry` fields, `size`, `content_hash`, `expires_in`, `refresh_token`, `account_id` of
  the token answer, `email`, `name`).
- Present but empty is just as wrong for `rev` and `access_token`: check them where they are used,
  `metadata.rev.ifEmpty { throw SyncNetworkException("Dropbox's answer named no revision.") }` in `upload`, and the
  same for `token.accessToken` in `exchange`. `cursor` may be empty on a last page (`has_more` false); require it
  non-empty only when `hasMore` is true, else the `continue` loop would ask for page `""`.
- Rewrite the `DropboxModels` KDoc: unknown keys are ignored and fields that may be added or are optional are defaulted,
  so a new API field never breaks sync; the few fields an answer exists to carry are required, so an answer that is not
  Dropbox's (an empty object from something in between) is a failure rather than an empty folder.
- `data/source/remote/implementation/CLAUDE.md`, Dropbox bullet "`DropboxModels`, like `MusicBrainzModels`: everything
  defaulted, unknown keys ignored, so a new API field never breaks sync": say "unknown keys ignored, optional fields
  defaulted; the fields a decision rests on (listing entries, cursor, `has_more`; revisions; the access token; the
  delete job's tag) are required, and an answer without them is the network failing".

## Tests

Checked at HEAD: every fixture in the module's tests already carries the fields above (`EMPTY_LISTING` has `entries`,
`cursor`, `has_more`; upload receipts carry `rev`; token answers carry `access_token`; delete answers carry `.tag`; the
one account answer carries `account_id`). Any fixture found lacking one must get the field, not keep the default.

- `DropboxFilesTest`: `a listing answered with an empty object is the network failing`: `respondJson("{}")` →
  `assertFailsWith<SyncNetworkException> { provider.list() }`; the same for `{"error":"blocked"}`.
- `DropboxFilesTest`: `an upload receipt without a revision is the network failing`: `respondJson("""{"name":"song.cho"}""")`
  and `respondJson("""{"rev":""}""")` → `SyncNetworkException`.
- `DropboxFilesTest`: a listing page with `"has_more":true` and `"cursor":""` → `SyncNetworkException`.
- `DropboxTokensTest`: a renewal answered `{"expires_in":14400}` → `assertFailsWith<SyncNetworkException> { provider.list() }`,
  and the stored access token is unchanged.
- `DropboxDeletionTest`: `delete_batch` answered `{}` → `SyncNetworkException`.
