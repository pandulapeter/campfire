# Give the Manage tags dialog's create row a key no tag can have

**Kind:** bug (crash)  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/Dialogs.kt`

## Problem

The Manage tags dialog (`SongTagsDialog` in `Dialogs.kt` at 9ab7ca54e) lists a "Create the "…" tag" row above the
matching tags in one `LazyColumn`, keyed like this:

```kotlin
if (isCreatable) {
    item(key = CREATE_TAG_KEY) {
        …
    }
}
items(
    items = matches,
    key = { it },
) { tag ->
```

with

```kotlin
private const val CREATE_TAG_KEY = "createTag"
```

A tag row's key is the tag text itself, a `String`, and the create row's key is the `String` `"createTag"`. Tags are
user data, read from files that anybody may have written and that sync brings in from other devices. A library holding
a tag spelled `createTag`, and `createT` typed in the field, gives `isCreatable = true` (no offered tag equals
`createT`) and `matches` containing `createTag` (a substring match): two items with the key `"createTag"`, and
`LazyColumn` throws `IllegalArgumentException: Key "createTag" was already used`. The app crashes on a keystroke.

## Fix

Make the create row's key a value no `String` can equal. Replace the constant with a private object in the same file:

```kotlin
/** The key of the create row, a type of its own so that no tag, whatever it is spelled, can share it. */
private object CreateTagKey
```

and `item(key = CreateTagKey)`. `LazyColumn` keys must be saveable through `rememberSaveable` (the lazy layout saves
the key of the first visible item): an `object` is not, so use a saveable primitive that still cannot collide — the
simplest is to prefix the *tag* keys instead and keep the create row's string: `key = { "tag:$it" }` on the `items`
call and `CREATE_TAG_KEY = "create"`. Prefer this second form, since it needs no thought about savers; the two
namespaces (`create` and `tag:…`) cannot meet.

## Tests

None practical: the keys are Compose-internal and the two lambdas are one line each. If the executor prefers a pin,
pull the two keys into `internal fun tagRowKey(tag: String): String = "tag:$tag"` and a test asserting
`tagRowKey("create") != CREATE_TAG_KEY` — but the one-line prefix is clear enough on its own.

## Manual check

On the desktop build, add a tag `createTag` to a song through the editor (`{tag: createTag}`), open Manage tags on
another song and type `createT`: the create row and the `createTag` row are both listed, and nothing crashes.
