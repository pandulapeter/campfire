# 13 · Android "Open with Campfire" is not offered for a ChordPro file whose name or folder has another dot in it

**Severity:** wrong behaviour (Android. Likely for a real share of song files: `Hey.Jude.cho`, `Mr. Brightside.cho`,
`song.v2.cho`, anything under `Download/Songs 2.0/`, and `Song.CHO` from a FAT-formatted card or a Windows machine.
Only for providers whose content URIs carry the path; the user can still import from inside the app) · **Area:**
`:app:android` (`AndroidManifest.xml`)

## Symptom
In a file manager whose content URIs carry the file's path (external storage documents, most third-party file
managers), Campfire is missing from "Open with" for:
- `Hey.Jude.cho`, `Mr. Brightside.cho`, `song.v2.cho`
- any `.cho` inside a folder with a dot in its name (`Download/Songs 2.0/Wonderwall.cho`)
- `Song.CHO` (the match is case-sensitive)

`song.cho` in a folder without dots is offered.

## Cause
`app/android/src/main/AndroidManifest.xml:83-91`:

```xml
<data android:scheme="content" />
<data android:host="*" />
<data android:mimeType="*/*" />
<data android:pathPattern=".*\\.cho" />
...
```

`android:pathPattern` is `PatternMatcher.PATTERN_SIMPLE_GLOB`, matched against the decoded `Uri.getPath()` (for
external storage documents that is `/document/primary:Download/Hey.Jude.cho`). In
`android.os.PatternMatcher.matchGlobPattern` (read in the android-34 sources, and unchanged since), a `.*` followed by
a literal consumes the input up to the **first** occurrence of that literal and never backtracks:

```java
do {
    if (match.charAt(im) == nextChar) {
        break;
    }
    im++;
} while (im < NM);
```

So `.*\.cho` (the XML `\\.` is `\.` after aapt, an escaped literal dot) against `/document/primary:Download/Hey.Jude.cho`
stops at the dot after `Hey`, then needs `cho` and finds `Jud`: no match. Each such pattern matches only a path with
exactly one dot in it, and the comparison is case-sensitive. The same holds for a dot anywhere earlier in the path.

The reviewer's suggested `android:pathAdvancedPattern=".*\\.[cC][hH][oO]"` **would not work**: the advanced
matcher is greedy with no backtracking either (`matchAdvancedPattern` / `matchChars` in the same file take as many
characters as the token allows and never give any back), so its `.*` swallows the whole path and the `\.` that
follows finds nothing: that pattern matches no path at all. The API 31 attribute that does what is wanted is
`android:pathSuffix` (`PATTERN_SUFFIX`, `match.endsWith(pattern)`), which is exact whatever the path holds, and is
also case-sensitive.

A second, separate limitation: many providers hand out URIs with no file name in them at all (the Downloads
provider's `document/msf:1234`, mail and cloud attachments), usually typed `application/octet-stream`. No path
filter can match those, and claiming `*/*` without a path would put Campfire in front of every file on the device,
so they stay out; the in-app import handles them.

## Fix
`AndroidManifest.xml`, the ChordPro `<intent-filter>` (`:74-91`). Keep the scheme, host and MIME `<data>` elements
and replace the six `pathPattern` lines with:

```xml
                <!-- Android 12 and later: an exact suffix, whatever the rest of the path holds. The matcher is
                     case-sensitive, so the capitalized spellings a FAT card or a Windows machine produces are listed
                     too. pathAdvancedPattern is no help here: it never backtracks, so ".*\\.cho" in it matches nothing. -->
                <data android:pathSuffix=".cho" />
                <data android:pathSuffix=".chopro" />
                <data android:pathSuffix=".chordpro" />
                <data android:pathSuffix=".crd" />
                <data android:pathSuffix=".chord" />
                <data android:pathSuffix=".pro" />
                <data android:pathSuffix=".CHO" />
                <data android:pathSuffix=".CHOPRO" />
                <data android:pathSuffix=".CHORDPRO" />
                <data android:pathSuffix=".CRD" />
                <data android:pathSuffix=".CHORD" />
                <data android:pathSuffix=".PRO" />
                <!-- Android 9 to 11 have only pathPattern, whose ".*" stops at the first occurrence of the character
                     after it and never backtracks, so a pattern matches a path with exactly as many dots as it has.
                     One pattern per count, up to four dots in the whole path; a path with more is imported from
                     inside the app instead. -->
                <data android:pathPattern=".*\\.cho" />
                <data android:pathPattern=".*\\..*\\.cho" />
                <data android:pathPattern=".*\\..*\\..*\\.cho" />
                <data android:pathPattern=".*\\..*\\..*\\..*\\.cho" />
                <data android:pathPattern=".*\\.chopro" />
                <data android:pathPattern=".*\\..*\\.chopro" />
                <data android:pathPattern=".*\\..*\\..*\\.chopro" />
                <data android:pathPattern=".*\\..*\\..*\\..*\\.chopro" />
                <data android:pathPattern=".*\\.chordpro" />
                <data android:pathPattern=".*\\..*\\.chordpro" />
                <data android:pathPattern=".*\\..*\\..*\\.chordpro" />
                <data android:pathPattern=".*\\..*\\..*\\..*\\.chordpro" />
                <data android:pathPattern=".*\\.crd" />
                <data android:pathPattern=".*\\..*\\.crd" />
                <data android:pathPattern=".*\\..*\\..*\\.crd" />
                <data android:pathPattern=".*\\..*\\..*\\..*\\.crd" />
                <data android:pathPattern=".*\\.chord" />
                <data android:pathPattern=".*\\..*\\.chord" />
                <data android:pathPattern=".*\\..*\\..*\\.chord" />
                <data android:pathPattern=".*\\..*\\..*\\..*\\.chord" />
                <data android:pathPattern=".*\\.pro" />
                <data android:pathPattern=".*\\..*\\.pro" />
                <data android:pathPattern=".*\\..*\\..*\\.pro" />
                <data android:pathPattern=".*\\..*\\..*\\..*\\.pro" />
```

Notes for the implementer:
- Both kinds live in one filter. Paths of one filter are alternatives, so on API 31+ a path that either kind
  matches is accepted; on 28-30 the `pathSuffix` elements are an attribute the platform does not know and add
  nothing. `tools:ignore="UnusedAttribute"` on `<application>` already covers the lint warning for the newer
  attribute.
- Walk-through of why the extra patterns are exact: `.*\\..*\\.cho` on `/x/Hey.Jude.cho` consumes to the first
  dot, then to the second, then matches `cho` at the end. On `/x/song.cho` its second `.*` finds no further dot and
  fails, and on `/x/a.cho.bak` the final literal meets `bak` and fails, and the loop exits with input left over,
  which is also a failure. So none of them over-matches.
- The upper-case `pathPattern` spellings are not added for 28-30: that would double 24 lines to 48 for a minority
  of an old minority.
- Keep the MIME wildcard and the `content` scheme exactly as they are; the existing comment above the filter stays,
  with "and the extension is what actually decides" still true.

## Tests
None (manifest).

## Verify
1. `./gradlew :app:android:assembleDebug` builds; `aapt2 dump xmltree --file AndroidManifest.xml <apk>` shows the
   `pathSuffix` and `pathPattern` entries.
2. On an API 34+ emulator, with the debug build installed:
   `adb shell cmd package query-activities -a android.intent.action.VIEW -t text/plain -d "content://com.android.externalstorage.documents/document/primary%3ADownload%2FSongs%202.0%2FHey.Jude.cho"`
   lists `CampfireActivity`; so does the same with `...%2FSong.CHO`. With `...%2Fsong.txt` it does not.
3. On an API 29 or 30 emulator: the same query lists Campfire for `Hey.Jude.cho` and `Songs 2.0/Hey.Jude.cho`
   (three dots), not for `song.txt`, and not for `Song.CHO` (known and documented).
4. In the Files app on each: long-press `Hey.Jude.cho` in Download, Open with: Campfire is offered and imports it.

## Docs
`app/android/CLAUDE.md`, the `AndroidManifest.xml` paragraph: replace "with a wildcard MIME type so that the
`pathPattern`s are what actually decide — a `content://` URI has no extension in the eyes of the intent resolver unless
a type is declared." with:

"with a wildcard MIME type so that the path filters are what actually decide — a `content://` URI has no extension in
the eyes of the intent resolver unless a type is declared. Android 12 and later match the extension with
`pathSuffix`, in lower and upper case. Before that there is only `pathPattern`, whose `.*` stops at the first
occurrence of the next character and never backtracks, so each extension has one pattern per number of dots in the
path, up to four (`Hey.Jude.cho` in `Songs 2.0/` is three), and matching there is lower case only.
`pathAdvancedPattern` is no substitute: it never backtracks either, so `.*\\.cho` in it matches nothing. A URI with no
file name in its path (the Downloads provider's `msf:` ids, most attachments) cannot be matched by any of them, and
such a file is imported from inside the app."

## Touches
- `app/android/src/main/AndroidManifest.xml`
- `app/android/CLAUDE.md`

## Depends on
Nothing.
