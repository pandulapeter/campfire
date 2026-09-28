# Read a setlist whose date is not text, losing only the date

**Challenged:** amended — proved with a probe (a private document with the same `Json` options and a `String?` property annotated `@Serializable(with = …)`): the custom `KSerializer<String?>` with a `.nullable` descriptor works with the plugin-generated serializer, decodes `"2026-09-28"`, `20260928`, `{}`, `[1]`, `true`, `null` and a missing key as intended (string kept, all else null), encodes a null date as nothing and a date as `"date":"x"`, and leaves the descriptor's element names (so `DOCUMENT_KEYS`) unchanged. Added the `@OptIn(ExperimentalSerializationApi::class)` and the `kotlinx.serialization.descriptors.nullable` import that `.nullable` needs (without the import it does not compile); the `as? JsonDecoder` fallback form is the one to use.

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/model/SetlistDocument.kt`,
`data/source/local/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/model/SetlistDocumentFormatTest.kt`

## Problem

`SetlistDocument` (at 42c970dd) promises that a hand-written date that does not read as one loses only itself:

```kotlin
/** An ISO date (`2026-09-28`), kept as text so that one written by hand that does not read as a date loses only itself. */
val date: String? = null,
```

That holds for any *string* (`"next friday"`, `"2026-09-28T10:00"`: `SetlistMappers.toLocalDate()` catches the
`IllegalArgumentException` and returns null). It does not hold for a value that is not a string. `SetlistDocumentFormat`'s
`Json` is not lenient (`coerceInputValues` only covers `null`), so `decodeFromJsonElement` throws a
`SerializationException` for `"date": 20260928`, `"date": 2026` or `"date": {}` — which is exactly what somebody editing
the file by hand, or another tool writing it, is likely to produce. Every read goes through `SetlistDocumentFormat.decode`
(`loadSetlist`, `parseSetlist`, the directory scan), so the whole setlist becomes unreadable: it is missing from the
library, and an import of it fails, over a field that is optional.

## Fix

Give `date` a serializer of its own that takes a JSON string's content and turns anything else into null, instead of
changing its type:

```kotlin
@Serializable(with = OptionalTextSerializer::class)
val date: String? = null,
```

`OptionalTextSerializer` is a private `KSerializer<String?>` next to the document (descriptor
`PrimitiveSerialDescriptor(..., PrimitiveKind.STRING).nullable`): `deserialize` reads
`(decoder as JsonDecoder).decodeJsonElement()` and returns `(element as? JsonPrimitive)?.takeIf { it.isString }?.content`;
`serialize` writes the string (or null) as usual. The document is only ever decoded from a `JsonElement`
(`SetlistDocumentFormat.decode`), so the `JsonDecoder` cast holds; fall back to `decoder.decodeString()` when it is not
one, rather than casting blindly (`val json = decoder as? JsonDecoder ?: return decoder.decodeString()`). `.nullable` on a descriptor is
`@ExperimentalSerializationApi` and lives in `kotlinx.serialization.descriptors`: opt in on the serializer object and import it. Encoding stays byte for byte what it is now (`encodeDefaults` is off, so a null date is
still left out) — check that `SetlistDocumentFormatTest`'s existing round trips still pass unchanged.

Update the KDoc to say "one written by hand that does not read as a date, or is not text at all, loses only itself".
Leave `title` and `description` as they are: they are not in scope, and a number in either is not a date question.

## Tests

In `SetlistDocumentFormatTest`, decode `{"title":"S","date":20260928,"songs":[{"file":"a.cho"}]}` and
`{"title":"S","date":{},"songs":[{"file":"a.cho"}]}`: neither throws, `date` is null, the title and the song are kept.
And `"date":"2026-09-28"` still decodes to `"2026-09-28"`.

## Manual check

None needed beyond the tests: put `"date": 2026` into a setlist file on the desktop build and see the setlist listed,
undated.
