# Make the unit tests independent of the host: an ephemeral port for the sync authenticator, bundled fonts only for text measurement

**Kind:** testing · **Severity:** high (step 1), low (step 2) · **Platforms:** desktop (tests) · **Priority:** P1 (step 1), P3 (step 2) · **Effort:** S (step 1), M (step 2)
**Files:** step 1: `data/source/remote/implementation/src/desktopMain/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/auth/DesktopSyncAuthenticator.kt`,
new `…/auth/LoopbackRedirectListener.kt` (same folder),
`data/source/remote/implementation/src/desktopTest/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/auth/DesktopSyncAuthenticatorTest.kt`
(renamed `LoopbackRedirectListenerTest.kt`), `data/source/remote/implementation/CLAUDE.md`,
`.claude/skills/codebase-review/SKILL.md`; step 2: `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintRenderer.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/export/ExportScreen.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/theme/ScriptFace.kt` (KDoc, and a run
finder), `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/theme/CLAUDE.md` (Script faces),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/CLAUDE.md` (Renderer),
`presentation/src/desktopTest/kotlin/com/pandulapeter/campfire/presentation/ui/print/PdfRoundTripTest.kt`,
`presentation/src/desktopTest/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintRendererTest.kt`

**Challenged:** amended — step 1's production-address check no longer binds 53682 (calling `prepareRedirectUri()` on
`DesktopSyncAuthenticator` would); the address is a pure function tested on its own. Step 2 decided by the owner's
bundled-fonts rule: the PDF sets Hebrew in the bundled Noto Sans Hebrew on every platform, and the RTL exclusion goes;
its order with plans 06/07 stated.

## Problem

Since today the pre-commit hook runs `spotlessCheck desktopTest` whenever more than Markdown is staged, so a unit test
that depends on the machine now blocks commits, parallel worktree lanes included.

**Fixed port.** `DesktopSyncAuthenticator` binds `127.0.0.1:53682` (`const val PORT = 53682`; Dropbox matches the
registered redirect URI character for character, so production must keep it). Its test binds and probes the same
port at four places (`Socket("127.0.0.1", 53682)`), and `cancelling the authorization releases the port` waits for
`isPortOpen()` to turn false. Two `desktopTest` runs at once (two worktree lanes, or a lane and the owner's commit), or
the owner's own desktop app mid-sign-in, hold the port: the test fails or waits out its 5 s bound. The codebase-review
skill already carries the workaround as "Known false alarms: `DesktopSyncAuthenticatorTest` holds port 53682, so rerun
it alone…". `636a2456b` also had to widen the probe's catch from `ConnectException` to `SocketException` because Linux
resets where macOS refuses.

**System fonts.** `PdfRoundTripTest` measures with `createFontFamilyResolver()` and the bundled Inter and JetBrains
Mono, neither of which has Hebrew; Skia then falls back to the platform's fonts first (`ui/theme/CLAUDE.md`: "Skia asks
the platform's fonts first"), so the Hebrew rows' spacing is each OS's own. `b12da916d` excluded one print after it
failed on CI:

```kotlin
// At 15 points this song's two-chord row over a short Hebrew lyric prints like a line of chords alone over the
// next line, which the import tells apart by no measure but the rows' spacing, and that depends on the
// fallback font the platform draws Hebrew in; this print is the import's known limit, not compared.
fontSizes.filterNot { name == "60-generated-rtl-15.cho" && it == 15 }.mapNotNull { fontSize ->
```

The exclusion applies on every OS, so it no longer blocks a commit; but every other right-to-left case of the test
(eight of them) still measures in whatever Hebrew face the host has, and could flip the same way on another machine.
Compose offers no resolver without the platform font manager (`FontCache` sets `FontMgrWithFallback` itself), so a
test cannot opt out of system fonts; only the text it lays out can name its face.

## Fix

### Step 1: an ephemeral port in the test (P1)

The rule "no defaulted parameter on a Koin definition's constructor" (code-style) rules out
`DesktopSyncAuthenticator(…, port: Int = PORT)`. Follow `DesktopFileStorage : FileStorage by JvmFileStorage(…)`:

- Move the class body into `internal class LoopbackRedirectListener(systemBrowser: SystemBrowser, logger: Logger, port: Int) : SyncAuthenticator`
  (not a Koin definition). `prepareRedirectUri` binds `ServerSocket(port, 1, loopback)` and answers
  `"http://$LOOPBACK_ADDRESS:${socket.localPort}"`; `awaitRedirect` builds the `Received` address from the same
  `localPort` (today it interpolates `PORT`).
- `@Single internal class DesktopSyncAuthenticator(systemBrowser: SystemBrowser, logger: Logger) : SyncAuthenticator by LoopbackRedirectListener(systemBrowser, logger, PORT)`,
  keeping `PORT = 53682` and its KDoc on why it is fixed.
- Both addresses (`prepareRedirectUri`'s answer and `awaitRedirect`'s `Received` prefix) come from one top-level
  `internal fun loopbackRedirectUri(port: Int) = "http://$LOOPBACK_ADDRESS:$port"`.
- The test constructs `LoopbackRedirectListener(…, port = 0)`, reads the bound port from `prepareRedirectUri()`'s
  answer, and probes that port. The production address is checked without binding anything:
  `assertEquals("http://127.0.0.1:53682", loopbackRedirectUri(DesktopSyncAuthenticator.PORT))` (`PORT` becomes
  `internal` in a companion of `DesktopSyncAuthenticator`). Never call `prepareRedirectUri()` on a
  `DesktopSyncAuthenticator` in a test: it binds 53682. Behaviour of the app is unchanged.
- Drop the "Known false alarms: `DesktopSyncAuthenticatorTest` holds port 53682…" clause from
  `.claude/skills/codebase-review/SKILL.md` (keep the incremental-cache one); in
  `data/source/remote/implementation/CLAUDE.md` point the Desktop bullet's test name at the new class.

### Step 2: the PDF sets Hebrew in the bundled face (P3, decided)

The root `CLAUDE.md` rule (the app is set in its bundled fonts on every platform, never the system's) applies to the
print: today a PDF's Hebrew is drawn in whatever face the platform's text engine falls back to (the system's on macOS,
Windows, iOS, and Android; Noto only on the web and a bare Linux), so the same export differs per platform and the
round-trip test measures each host's face.

- **Reachability, checked per platform.** The face is `Res.font.noto_sans_hebrew_regular` (`scriptFaces`, the only row).
  Compose resources' `preloadFont(resource)` (common, already used by the web's `ProvideScriptFaces`) answers a `Font`
  on every target (null until it is read): from the JVM classpath (desktop), the app bundle (iOS), and assets (Android),
  fetched once on the web (and kept by the offline copy, `app/web`). An explicit `FontFamily(font)` on a span is honoured
  by the Android text engine as by Skia, so unlike the fallback registration (`ProvideScriptFaces`, a no-op on Android)
  it reaches every platform.
- **`PrintRenderer`** takes `scriptFontFamilies: List<Pair<ScriptFace, FontFamily>>` (default empty: not a Koin
  definition). In `text(…)`, which `width`, `lyricLayout`, and the drawing all go through, every run of characters
  inside a row's `ranges` gets `SpanStyle(fontFamily = …)` on top of the formatting spans; the run finder is a function on
  `ScriptFace` beside `isNeededFor` (same code point walk, answering `IntRange`s of the string). Bold and italic stay on
  the `TextStyle`; with only Regular bundled, bold Hebrew is synthesized (Compose's default `FontSynthesis`), the same on
  every platform.
- **`ExportScreen`** reads each row with `preloadFont(face.font)`, builds the renderer only once every one has arrived
  (it is keyed in the `remember` with the other fonts), and lays out nothing before that, as it already waits for
  notations, so no preview is laid out twice. On the web, where a failed fetch is never reported, it lays out without
  the face after the same five seconds `ProvideScriptFaces` waits.
- **Docs.** `ScriptFace`'s KDoc ("It is never asked for by name") gains: except by the print, which sets the script's
  characters in it so a PDF is the same on every platform. One line each in `ui/theme/CLAUDE.md` (Script faces) and
  `ui/print/CLAUDE.md` (Renderer).
- **`PdfRoundTripTest`** passes `scriptFontFamilies` built from `noto_sans_hebrew_regular.ttf` the way
  `bundledFontFamily` reads Inter, and the exclusion `fontSizes.filterNot { name == "60-generated-rtl-15.cho" && it == 15 }`
  and its comment go. Run the test on macOS and let CI (Linux) run it: both now measure the same face. If that print
  still fails on both, it is the importer's limit with a host-independent cause: keep the exclusion with its comment
  rewritten to say so (it disappears with the narrow-column test in plan 07 step 8), never an OS check. If any other
  right-to-left case changes outcome and fails on both, that is an importer defect: fix it in its own commit first, or
  stop and ask.

**Order with plans 06 and 07** (both touch `PdfRoundTripTest`): this step lands first (lane H). Plan 06 step 2 then only
replaces the test's `ExtractedDocument.toChordSheet()` mapper; plan 07 step 8 cuts the test to its one single-column
case, which keeps this step's renderer construction. `PrintRenderer` is shared with plan 07 step 9 (the word joiners,
lines far from `text(…)`).

## Tests

Step 1: `LoopbackRedirectListenerTest` is the moved tests, on port 0; after the change `53682` appears in the test
only inside the expected address string, so nothing it does depends on that port being free. Step 2: `PdfRoundTripTest` as
described; plus a `PrintRendererTest` case that a Hebrew text, and a mixed Hebrew and Latin one, measure as laid out in
the given face: `width` equals the width a `TextMeasurer` gives for the same text spanned by hand with that face.
