# 10 · Every iOS build uploaded to App Store Connect stops at "Missing Compliance"

**Severity:** store/policy, minor (iOS. Certain for every uploaded build once `ios-publish.yml` becomes the TestFlight
upload; each one waits for the export compliance questions to be answered by hand. Nothing reaches users either way) ·
**Area:** `:app:ios` (`iosApp/iosApp/Info.plist`)

## Symptom
Each build uploaded to App Store Connect shows "Missing Compliance" in TestFlight and cannot be tested or submitted
until somebody answers the encryption questionnaire for it in App Store Connect. That breaks the unattended release
`release.yml` is built around: publishing the GitHub release would upload the build and then stop.

## Cause
`app/ios/iosApp/iosApp/Info.plist` has no `ITSAppUsesNonExemptEncryption` key, and neither do the target's build
settings (`project.pbxproj`, no `INFOPLIST_KEY_ITSAppUsesNonExemptEncryption`); the Info.plist of the last Release
build for devices has none either.

The app's cryptography is all exempt:
- HTTPS to Dropbox through the system's networking (ktor's Darwin engine on `NSURLSession`);
- SHA-256 in pure Kotlin (`data/source/remote/api/.../hashing/Sha256.kt`), used for content hashes and the PKCE
  challenge (`crypto/Pkce.kt`, `crypto/ContentHash.kt`), which is hashing and authentication, not encryption;
- the Keychain, which is the system's.
The AES-GCM of `AndroidSecretStore` is Android-only and not in the iOS binary.

## Fix
`app/ios/iosApp/iosApp/Info.plist`, after the `LSSupportsOpeningDocumentsInPlace` entry (`<true/>`), add:

```xml
	<!-- Answers App Store Connect's export compliance question up front, so an upload does not wait for it by hand.
	     The only cryptography is exempt: HTTPS through the system's networking, SHA-256 for content hashes and the
	     PKCE challenge, and the Keychain. Anything that adds encryption of its own has to revisit this. -->
	<key>ITSAppUsesNonExemptEncryption</key>
	<false/>
```

## Tests
None (build configuration).

## Verify
1. `plutil -lint app/ios/iosApp/iosApp/Info.plist` prints `OK`.
2. Build with the `xcodebuild` command from the root `CLAUDE.md`, then
   `plutil -p <SYMROOT>/Debug-iphonesimulator/Campfire.app/Info.plist | grep ITSAppUsesNonExemptEncryption` shows
   `=> false`.
3. On the first TestFlight upload, the build goes to "Ready to Submit"/testing without the compliance prompt.

## Docs
`app/ios/CLAUDE.md`, after the paragraph about `CFBundleURLTypes`, add:

"`ITSAppUsesNonExemptEncryption` is `false`, so App Store Connect does not hold every upload for the export compliance
questions: the app's only cryptography is HTTPS through the system, SHA-256 hashing and the Keychain, all exempt.
Anything that adds encryption of its own has to change that answer."

## Touches
- `app/ios/iosApp/iosApp/Info.plist`
- `app/ios/CLAUDE.md`

## Depends on
Nothing. 09 and 15 edit the same Xcode project and 15 the same `Info.plist`, so run them one after another.
