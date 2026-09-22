# 31 — Open the consent page the way every other link is opened

## What the user sees

On desktop, and in practice on Linux. The user presses **Connect** next to Dropbox in Settings. No browser opens.
Depending on which of the three failures it is, they get one of:

- **Nothing, for five minutes.** Settings says the app is waiting for the browser; the only way out is the Cancel
  row. This is the common case: `java.awt.Desktop` reports that it supports `BROWSE`, then throws `IOException`
  when asked to do it — a Linux session with no `xdg-settings` default browser, a snap or flatpak browser AWT
  cannot invoke, a GNOME library stack that half-loads. The fallback that would have run `xdg-open` is never
  reached, because it only runs when BROWSE is reported as *unsupported*.
- **A settings screen stuck on "connecting", for good.** If the desktop peer fails to link — the AWT native
  libraries missing from a packaged runtime, which is what `LinkageError` means — the error is not an `Exception`
  and is caught by nothing on the way up. The connection state stays `Connecting`, the coroutine is gone, and only
  the Cancel row gets the user out.
- **A browser that opens and then hangs the app.** Where the fallback does run, `xdg-open` may *become* the
  browser process; its output goes into a pipe nobody reads, and when that pipe fills the browser stops.

Meanwhile the About section's links — the GitHub page, the privacy policy, the donation page — open correctly in
all three cases, because the URL opener next to them has been taught every one of these lessons.

## Cause

Two implementations of the same thing, one of them a third of the other.

`data/source/remote/implementation/src/desktopMain/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/auth/SyncAuthenticator.desktop.kt:171-184`,
verified at HEAD `984861e4`:

```kotlin
/** Every desktop Campfire runs on can open a browser; the JDK just cannot always see how. */
private fun openInSystemBrowser(url: String) {
    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
        Desktop.getDesktop().browse(URI(url))
    } else {
        val operatingSystem = System.getProperty("os.name").orEmpty().lowercase()
        val command = when {
            operatingSystem.contains("mac") -> arrayOf("open", url)
            operatingSystem.contains("win") -> arrayOf("rundll32", "url.dll,FileProtocolHandler", url)
            else -> arrayOf("xdg-open", url)
        }
        ProcessBuilder(*command).start()
    }
}
```

versus `presentation/src/desktopMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireDesktopApp.kt:166-205`:

```kotlin
/**
 * Opens [url] in the system's browser and answers whether anything took it. `java.awt.Desktop` goes first, and the
 * operating system's own command is what is left - both where AWT has no desktop to speak of, which is a Linux
 * session without the GNOME libraries it looks for, and where it has one that fails, as `browse` does on a machine
 * with no default browser registered.
 */
private fun openUrl(url: String) = openWithAwt(url) || openWithSystemCommand(url)

/** `getDesktop` throws where `isDesktopSupported` says no, so it is only asked for after that has said yes. */
private fun openWithAwt(url: String) = try {
    val desktop = if (Desktop.isDesktopSupported()) Desktop.getDesktop().takeIf { it.isSupported(Desktop.Action.BROWSE) } else null
    desktop?.browse(URI(url))
    desktop != null
} catch (exception: Exception) {
    println("Could not open $url through java.awt.Desktop: ${exception.message}")
    false
} catch (error: LinkageError) {
    // The desktop peer loads native libraries the first time it is asked for, and one that does not link is an
    // Error rather than an Exception. This runs inside a click handler, where either would close the window.
    println("Could not open $url through java.awt.Desktop: ${error.message}")
    false
}

private fun openWithSystemCommand(url: String) = try {
    …
    // Discarded rather than piped: xdg-open may become the browser itself, which then writes its log into a pipe
    // nobody reads and stops once that is full.
    ProcessBuilder(*command)
        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
        .redirectError(ProcessBuilder.Redirect.DISCARD)
        .start()
    true
} catch (exception: Exception) {
    println("Could not open $url: ${exception.message}")
    false
}
```

Three differences, each of them one of the symptoms above: the fallback is reached on a *failure* and not only on
"unsupported"; `LinkageError` is caught; the process's output is discarded.

And what the authenticator does with a browser that never opened,
`SyncAuthenticator.desktop.kt:83-95`:

```kotlin
        try {
            withContext(Dispatchers.IO) {
                openInBrowser(authorizationUrl)
                socket.awaitRedirect(completionPage)
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            SyncAuthenticator.AuthorizationOutcome.Cancelled(exception.toString())
        } finally {
```

An `IOException` here becomes `Cancelled(message)`, which `SyncRepositoryImpl.connect` (line 220-233) reports as a
failed connection — so that case at least says something. A `LinkageError` is not an `Exception` and passes through
this catch, through `connect`'s `catch (exception: Exception)` (`SyncRepositoryImpl.kt:240`) and through
`CampfireViewModel.launchLibraryChange` (`CampfireViewModel.kt:1890-1899`), which also catches only `Exception` —
leaving `_syncState` at `SyncState.Connecting(providerId)` with nothing left running.

## The change

One implementation, in the module that already has it, reached from the module that needs it through a contract —
because `:data:source:remote:implementation` and `:presentation` cannot see each other, and only one of them may
depend on the other.

### 1. The contract, in `:data:source:remote:api`

`data/source/remote/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/remote/api/SystemBrowser.kt`
(new file, MPL header):

```kotlin
/**
 * Opening a URL in the user's own browser, which on the desktop is something the application shell knows how to do
 * and the data layer does not: `java.awt.Desktop` is right about half the Linux sessions there are, and what the
 * other half needs is the operating system's own command. The app already has that one implementation, for the
 * links in Settings, and this is how the consent page reaches it.
 *
 * Declared here next to [SyncAuthenticator] for the same reason that one is: the platform's half of the
 * authorization flow belongs in the contracts, and each platform provides its own. Only the desktop needs it - the
 * other three authenticators open their own browser through an API of their own.
 */
fun interface SystemBrowser {

    /** @return Whether anything took the URL. False where no browser could be opened at all. */
    fun open(url: String): Boolean
}
```

### 2. The one implementation, where it already lives

`presentation/src/desktopMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/SystemBrowser.desktop.kt`
(new file): move `openUrl`, `openWithAwt` and `openWithSystemCommand` out of `CampfireDesktopApp.kt` into it
unchanged, make `openUrl` `internal` so the composable can still call it, and add the Koin definition:

```kotlin
/**
 * The desktop [SystemBrowser]: the same opener the links in Settings use, offered to the sync authorizer, which is
 * in a module that cannot see this one. A `@Single` found by `PresentationModule`'s component scan, the way
 * `AndroidFilePicker` is.
 */
@Single
internal class DesktopSystemBrowser : SystemBrowser {

    override fun open(url: String) = openUrl(url)
}
```

`CampfireDesktopApp.kt:95` keeps its line — `urlOpener = { url -> if (!openUrl(url)) viewModel.onLinkNotOpened(url) }`
— and loses the three private functions and the `Desktop`, `URI` imports it no longer needs.

### 3. The authenticator asks for it

`data/source/remote/implementation/src/desktopMain/.../auth/SyncAuthenticator.desktop.kt`:

```kotlin
@Single
internal class DesktopSyncAuthenticator(
    /**
     * The shell's own browser opener, which knows the three ways a Linux session can refuse to open one. Injected
     * rather than called directly because the socket half of this is tested without a browser window opening on
     * whoever runs the tests.
     */
    private val systemBrowser: SystemBrowser,
) : SyncAuthenticator {
```

with no default value, so that the Koin compiler plugin's check at `:app:di` fails the desktop build if nobody
declares one, and `openInSystemBrowser` deleted from the bottom of the file along with the `java.awt.Desktop` and
`java.net.URI` imports.

A browser that did not open ends the authorization there and then, rather than holding the port for five minutes:

```kotlin
        try {
            withContext(Dispatchers.IO) {
                // Nothing will arrive at the socket if no browser was opened, and waiting out the five minute
                // timeout for that is telling the user nothing while they watch a spinner.
                if (!systemBrowser.open(authorizationUrl)) {
                    return@withContext SyncAuthenticator.AuthorizationOutcome.Cancelled("No browser could be opened.")
                }
                socket.awaitRedirect(completionPage)
            }
        } catch (exception: CancellationException) {
```

`Cancelled` with a non-null message is what `SyncRepositoryImpl.connect` turns into
`SyncState.ConnectionFailed(providerId, SyncFailureReason.UNKNOWN)`, which Settings already has words for — the
message itself is only printed, so **this adds no user-facing string** and nothing goes into `strings.xml`.

The `LinkageError` is handled by being caught where it happens (inside `openWithAwt`), which is what makes the
`catch (exception: Exception)` in `authorize` sufficient rather than merely lucky.

### Where this helper should live — the alternatives, and why not

- **In `:data:source:remote:api`'s `desktopMain`, as a function.** It is the one module both sides can see, so it
  is the tempting answer. Rejected: that module says of itself *"The sync contracts, and nothing else… Depends only
  on `:data:model`"*, and an AWT-and-`ProcessBuilder` helper there puts a desktop toolkit inside the contracts
  every platform compiles. An interface is a contract; an opener is not.
- **In `:data:model`.** Worse in the same way, and `:data:model` is shared by literally everything.
- **Duplicating the fixed logic in the authenticator.** Two copies of a fix, and the next lesson learned about
  Linux browsers gets applied to one of them.
- **A new Gradle module for desktop platform helpers.** Twenty lines do not earn a module, a convention plugin
  application and a line in `settings.gradle.kts`.

What is chosen inverts the dependency instead: the contract goes where the contracts are, and the implementation
stays in the shell that already owns it — the same shape as `AuthorizationCompletionPage`, which travels *down*
from the UI because the data layer cannot see the translations.

## Tests

`data/source/remote/implementation/src/desktopTest/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/auth/DesktopSyncAuthenticatorTest.kt`
— the existing three tests construct the authenticator with a do-nothing launcher (`DesktopSyncAuthenticator { }`);
because `SystemBrowser` is a `fun interface`, they become `DesktopSyncAuthenticator { true }` and keep working as
SAM conversions. The seam the test file's KDoc already describes (*"No browser is opened: the launcher is injected,
which is the only reason this seam exists"*) is now a typed one.

One new test, which is the whole of what this plan makes testable:

```kotlin
    /** A browser that never opened means nothing will ever arrive: waiting out the five minute timeout for it is
     *  telling the user nothing for five minutes. */
    @Test
    fun `an authorization whose browser could not be opened ends at once`() = runBlocking {
        val authenticator = DesktopSyncAuthenticator { false }
        authenticator.prepareRedirectUri()
        val outcome = withTimeout(5_000) { authenticator.authorize("https://example.com/authorize", COMPLETION_PAGE) }
        assertIs<SyncAuthenticator.AuthorizationOutcome.Cancelled>(outcome)
        assertNotNull(outcome.message, "A browser that could not be opened is a failure, not the user backing out.")
        withTimeout(5_000) { while (isPortOpen()) delay(50) }
    }
```

The opener itself (`openWithAwt` / `openWithSystemCommand`) stays untested, as it is today: it lives in
`:presentation`, which is UI and untested by policy, and what it does is ask the operating system for a browser.

## Verification

```
./gradlew :data:source:remote:implementation:desktopTest
./gradlew :chordpro:desktopTest :domain:implementation:desktopTest :data:source:local:implementation:desktopTest :data:source:remote:api:desktopTest :data:source:remote:implementation:desktopTest :data:repository:implementation:desktopTest
./gradlew :app:desktop:run
```

The Koin graph is checked at compile time in `:app:di`, so all four shells have to be built — a missing
`SystemBrowser` definition would only show up there:

```
./gradlew :app:desktop:run
./gradlew :app:android:assembleDebug
./gradlew :app:ios:linkDebugFrameworkIosSimulatorArm64
./gradlew :app:web:wasmJsBrowserDistribution
```

Manual, **needs a Dropbox app key in `local.properties`** (`campfire.dropbox.appKey`; without it no provider is
registered and Settings says sync is not configured), on macOS or Linux:

1. `./gradlew :app:desktop:run`, Settings → **Connect**. The browser opens on the consent page, and finishing the
   consent connects the account. (Regression: the ordinary path.)
2. Press Cancel in Settings while the page is open: the port is released at once, and a second Connect works. The
   existing test covers this; do it by hand once, since this plan touches the same coroutine.
3. The failure that started this, on Linux (a VM or a container with a desktop session): remove the default browser
   association (`xdg-settings set default-web-browser` pointing at something that does not exist, or unset
   `BROWSER` and remove the `.desktop` default) and press Connect. Before the change: a five minute wait with no
   browser. After: Settings reports that the connection failed, immediately.
4. Regression on the same machine: an About link — **GitHub**, **Privacy policy** — must behave exactly as before,
   since its code moved file.
5. Windows, if a machine is available: connect once and open one About link. Nothing in this plan is
   Windows-specific, but the opener moved.

A machine that reproduces the `LinkageError` case is not something to hunt for: it is a packaged runtime without
the AWT desktop peer, and the catch is carried over from code that was written for it.

## Docs

`data/source/remote/implementation/CLAUDE.md`, the **Desktop** bullet of the `auth/` section — true, and silent
about how the browser is opened, which is the part that was broken:

> **Desktop** becomes a web server for the length of one authorization — a socket on `127.0.0.1:53682` whose
> address is the redirect URI.

Add: the browser itself is opened through `SystemBrowser`, which the desktop shell's own URL opener implements, so
that the consent page and the links in Settings take exactly the same path — AWT first, the operating system's
command after it, `LinkageError` caught and the child process's output discarded — and an authorization whose
browser never opened ends at once instead of waiting out the five minute timeout.

`data/source/remote/api/CLAUDE.md` — the bullet list of contracts gains `SystemBrowser`, with the one sentence
about why a contract for opening a URL lives in the sync module (only the desktop's authorization needs one) and
who implements it.

`presentation/CLAUDE.md` — wherever the desktop shell's URL opening is described, it now also answers the sync
authorizer, and it is a Koin definition rather than a private function.

`app/desktop/CLAUDE.md:44-46` describes the loopback socket for sync; it is still accurate, but the sentence about
the socket is the natural place to note that the browser is opened by the shared opener.

## Files touched

- `data/source/remote/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/remote/api/SystemBrowser.kt` (new)
- `presentation/src/desktopMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/SystemBrowser.desktop.kt` (new)
- `presentation/src/desktopMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireDesktopApp.kt`
- `data/source/remote/implementation/src/desktopMain/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/auth/SyncAuthenticator.desktop.kt`
- `data/source/remote/implementation/src/desktopTest/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/auth/DesktopSyncAuthenticatorTest.kt`
- `data/source/remote/api/CLAUDE.md`, `data/source/remote/implementation/CLAUDE.md`, `presentation/CLAUDE.md`,
  `app/desktop/CLAUDE.md`

## Depends on

Nothing. It is the only plan in this lane that touches `:presentation`'s desktop source set and the remote source
modules.

## Rules

- Load the `code-style` skill before the first edit: both new files carry the MPL header, KDoc on the interface,
  its function and the new class, `//` comments inside statements, trailing commas, "why, not what".
- No new strings: the failure is reported through the existing `SyncFailureReason.UNKNOWN` wording, and the message
  that names the browser is a log line.
- `commonMain` stays JVM-free — the new interface in `:data:source:remote:api` is pure Kotlin, and every line that
  touches `java.awt` stays in a `desktopMain` source set.
- Never inject a `List<T>` (root `CLAUDE.md`); nothing here does, and `SystemBrowser` is a type of its own rather
  than a `(String) -> Boolean`, which the Koin plugin could not resolve as a definition anyway.
- Unit tests are the command in the root `CLAUDE.md`, quoted under Verification.
- The per-module `CLAUDE.md` files are part of the change, not a follow-up.
