# 11 · Backing out of the Dropbox consent page on Android or iOS shows "The connection did not go through."

**Severity:** wrong behaviour (Android and iOS. Every time a user closes the browser or cancels the sign-in sheet
instead of finishing the connection, which is a common way to change one's mind) · **Area:**
`:data:source:remote:implementation` (the Android and iOS `SyncAuthenticator`s), `:data:source:remote:api` (the
`Cancelled` contract)

## Symptom
- Android: Settings, Library, Connect Dropbox. The browser opens; press Back or close the tab. Campfire comes
  forward and the sync section shows "The connection did not go through." under the Connect row.
- iOS: tap Cancel on the "“Campfire” Wants to Use “dropbox.com” to Sign In" alert, or on the sign-in sheet. The same
  error row appears.

Nothing went wrong on either platform. The desktop and the web do not have this: the desktop only gives up on a
timeout or a socket failure, and the web never gets a `Cancelled` at all.

## Cause
`SyncRepositoryImpl.connect` tells a closed page from a failure by the message alone
(`data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/SyncRepositoryImpl.kt:220-230`):

```kotlin
is SyncAuthenticator.AuthorizationOutcome.Cancelled -> {
    println("The authorization was cancelled: ${outcome.message}")
    discardPendingAuthorization()
    // A message is the authenticator saying something went wrong on the way; without one, the user
    // simply closed the page, which needs no explaining.
    _syncState.update {
        if (outcome.message == null) {
            SyncState.Disconnected
        } else {
            SyncState.ConnectionFailed(providerId, SyncFailureReason.UNKNOWN)
        }
    }
    false
}
```

(`SyncRepositoryImplTest` "a closed browser is not a failure even when the clean up is" pins the null case.) The two
authenticators whose user can back out were never changed to say null for that:

- Android, `data/source/remote/implementation/src/androidMain/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/auth/SyncAuthenticator.android.kt:67`:
  `abandoned.onAwait { SyncAuthenticator.AuthorizationOutcome.Cancelled("The browser was closed before the service answered.") }`.
  The abandoned branch is only ever the user coming back without a redirect.
- iOS, `.../src/iosMain/.../auth/SyncAuthenticator.ios.kt:72-73`: `Cancelled(error?.localizedDescription)`.
  `ASWebAuthenticationSession` always hands its completion handler an `NSError` when the user cancels (domain
  `ASWebAuthenticationSessionErrorDomain`, code `ASWebAuthenticationSessionErrorCodeCanceledLogin` = 1), so the
  message is never null there.

The api KDoc does not state the contract either
(`data/source/remote/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/remote/api/SyncAuthenticator.kt:58`):
`/** The user closed the browser, or the service said no. */`.

## Fix
1. `SyncAuthenticator.kt` in `:data:source:remote:api`, replace the KDoc of `Cancelled` (`:58`):

   ```kotlin
   /**
    * The authorization ended without a redirect. [message] is null when the user backed out - closed the browser,
    * dismissed the sheet - which the app answers by going back to where it was, and says what went wrong otherwise,
    * which the app reports as a failed connection. An implementation that cannot tell the two apart says why.
    */
   data class Cancelled(val message: String? = null) : AuthorizationOutcome
   ```

2. Android (`SyncAuthenticator.android.kt:67`), keep the sentence in the log and say null:

   ```kotlin
   abandoned.onAwait {
       println("The browser was closed before the service answered.")
       // The user backing out, which is not a failure to explain, see Cancelled.
       SyncAuthenticator.AuthorizationOutcome.Cancelled()
   }
   ```

   The `catch (exception: Exception)` below (`:75-76`) stays a failure, but `exception.message` can be null (an
   `ActivityNotFoundException` built without one, for a device with no browser), which would now read as the user
   backing out. Make it `SyncAuthenticator.AuthorizationOutcome.Cancelled(exception.toString())`.

3. iOS (`SyncAuthenticator.ios.kt:70-77`), tell the user's cancel from every other error:

   ```kotlin
   val redirect = callbackUrl?.absoluteString
   continuation.resume(
       when {
           redirect != null -> SyncAuthenticator.AuthorizationOutcome.Received(redirect)
           // The user tapped Cancel on the alert or on the sheet, which needs no explaining, see Cancelled.
           error != null && error.domain == ASWebAuthenticationSessionErrorDomain &&
               error.code == ASWebAuthenticationSessionErrorCodeCanceledLogin -> SyncAuthenticator.AuthorizationOutcome.Cancelled()
           else -> SyncAuthenticator.AuthorizationOutcome.Cancelled(error?.localizedDescription ?: "The consent page closed without an answer.")
       }
   )
   ```

   Imports: `platform.AuthenticationServices.ASWebAuthenticationSessionErrorDomain`,
   `platform.AuthenticationServices.ASWebAuthenticationSessionErrorCodeCanceledLogin` (in the Kotlin 2.4.20 platform
   klib the domain is a `String?` and the code a `const val ... : Long = 1L`, which is what `NSError.code` is, so the
   comparison needs no conversion; the explicit `error != null` is what lets the smart cast through, since the domain
   constant is nullable). The `else` keeps `presentationContextNotProvided` and `presentationContextInvalid` as
   failures, and its fallback text keeps an error without a description from reading as a cancel.

   While editing this function, merge the two KDoc blocks stacked on `authorize` (`:49-53`) into one: the second
   one ("The page is ignored...") is a declaration-level KDoc directly after another and is lost on the first.

4. The desktop authenticator needs nothing: every `Cancelled` it returns (not prepared, timed out, the
   `catch (exception: Exception)`) is a failure, and none of them is the user backing out. Its catch has the same
   null-message hole as Android's; change `Cancelled(exception.message)` at
   `SyncAuthenticator.desktop.kt:91` to `Cancelled(exception.toString())` as well.

Out of scope, on purpose: pressing Cancel on Dropbox's own page comes back as a redirect with
`error=access_denied`, which `data/source/remote/implementation/CLAUDE.md` documents as "handled like any other
refusal" ("Dropbox refused the connection."). That is the documented design and not what this finding is about.

## Tests
`SyncRepositoryImplTest` already covers the repository's side (`Cancelled()` ends `Disconnected`). Add one next to
it so the other half of the contract is pinned too:

```kotlin
@Test
fun `an authorization that ends with a reason reports the connection as failed`() = runTest {
    val repository = repository(
        provider = FakeSyncProvider(),
        authenticator = FakeSyncAuthenticator(outcome = SyncAuthenticator.AuthorizationOutcome.Cancelled("Timed out waiting for the browser.")),
    )

    assertFalse(repository.connect(SyncProviderId.DROPBOX, COMPLETION_PAGE))

    assertEquals(SyncState.ConnectionFailed(SyncProviderId.DROPBOX, SyncFailureReason.UNKNOWN), repository.syncState.value)
}
```

The authenticators themselves are platform shells and untested. Run
`./gradlew :data:repository:implementation:desktopTest`.

## Verify
1. Android (`.debug` build, sync key in `local.properties`): Connect Dropbox, press Back in the browser. Settings
   shows the Connect row with no error under it. Connect again and finish: it connects.
2. iOS simulator: Connect, tap Cancel on the system alert: no error. Connect, continue, tap Cancel on the sheet: no
   error. Connect and finish: it connects.
3. Desktop: Connect, close the browser tab and wait, or disconnect the network so the redirect fails: the timeout
   and failures still show their rows.
4. Compile: `./gradlew :data:source:remote:implementation:compileKotlinDesktop :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64`.

## Docs
`data/source/remote/implementation/CLAUDE.md`, the authorization bullet list:
- iOS bullet, after "its completion handler is given a null URL and an error.": add "A cancel is
  `ASWebAuthenticationSessionErrorCodeCanceledLogin` and becomes `Cancelled` with no message, which Settings takes
  as the user backing out; any other error keeps its description and is reported as a failed connection."
- Android bullet, after "...before the attempt counts as abandoned.": add "An abandoned attempt is `Cancelled` with no
  message, the user backing out rather than a failure."

## Touches
- `data/source/remote/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/remote/api/SyncAuthenticator.kt`
- `data/source/remote/implementation/src/androidMain/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/auth/SyncAuthenticator.android.kt`
- `data/source/remote/implementation/src/iosMain/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/auth/SyncAuthenticator.ios.kt`
- `data/source/remote/implementation/src/desktopMain/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/auth/SyncAuthenticator.desktop.kt`
- `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/SyncRepositoryImplTest.kt`
- `data/source/remote/implementation/CLAUDE.md`

## Depends on
Nothing.
