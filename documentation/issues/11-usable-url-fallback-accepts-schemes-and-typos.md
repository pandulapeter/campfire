# Only take an address without a scheme as https when what it starts with is a host

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Files:** chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProLinks.kt, chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProLinksTest.kt, chordpro/CLAUDE.md

## Problem
`ChordProLinks.usableUrl` (`ChordProLinks.kt:49-52`) is what the "Add link" dialog enables its button by and what
`addLink` writes:

```kotlin
val trimmed = value.trim()
return ChordProSyntax.webUrl(trimmed) ?: trimmed.takeIf { "://" !in it && '.' in it }?.let { ChordProSyntax.webUrl("https://$it") }
```

The scheme-less fallback takes anything with a dot and no `://`, so text that already has a scheme, or a mistyped
one, is saved as a nonsense `https` address instead of being refused:

| typed / pasted | saved as | chip (`linkLabel`) | opens |
|---|---|---|---|
| `https:/example.com` (one slash) | `https://https:/example.com` | `https` | nothing useful |
| `https//example.com` (no colon) | `https://https//example.com` | `https` | nothing useful |
| `mailto:me@example.com` | `https://mailto:me@example.com` | `example.com` | example.com with credentials `mailto:me` |
| `me@example.com` | `https://me@example.com` | `example.com` | example.com |
| `javascript:alert(document.title)` | `https://javascript:alert(document.title)` | `javascript` | nothing |

The dialog (`presentation/.../ui/dialogs/Dialogs.kt:955-997`, `AddSongLinkDialog`) enables "Add link" for all of
them, and the result is written into the user's file for good. The KDoc says the fallback exists for addresses
"typed without its scheme (`youtu.be/…`)", i.e. text that *starts with a host*; none of the above does.

## Fix
1. In `ChordProLinks.usableUrl`, apply the `https://` fallback only when the text before the first `/`, `?` or `#`
   is a plain host with an optional port: every character a letter, a digit, `-` or `.` (use `Char.isLetterOrDigit`,
   not a regex — see `ChordProSyntax.words` on why this module avoids `\s`/class regexes across platforms), then
   optionally `:` followed by digits only; it holds at least one `.` that is neither its first nor its last
   character. That keeps `youtu.be/abc`, `www.example.com`, `example.com:8080/x`, `hu.wikipedia.org/wiki/Tükör`, and
   refuses every row of the table above (a `:` followed by a non-digit, a `@`, a host with no dot).
2. Keep the direct `ChordProSyntax.webUrl(trimmed)` path unchanged — a full `https://` address the user typed is taken
   as it is.
3. Extend `ChordProLinksTest.\`an address typed without its scheme is taken as https\``: `null` for
   `https:/example.com`, `https//example.com`, `mailto:me@example.com`, `me@example.com`,
   `javascript:alert(document.title)`; `https://example.com:8080/x` for `example.com:8080/x`.
4. `chordpro/CLAUDE.md`, the `ChordProLinks` bullet: say the scheme-less form is taken only where it starts with a
   host.

## Verification
`./gradlew :chordpro:desktopTest`. Manually: song details → Add link → type `https:/example.com`; the Add button stays
disabled.

## Conflicts
None known (`ChordProLinks.kt` is only touched by this lane; `Dialogs.kt` needs no change).
