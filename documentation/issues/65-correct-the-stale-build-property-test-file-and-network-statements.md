# Correct three stale statements: the build properties list, the web launch test's file name, and what the Android INTERNET permission is for

**Kind:** docs  ·  **Severity:** low  ·  **Platforms:** all
**Files:** root `CLAUDE.md` (one sentence), `app/web/src/wasmJsMain/resources/index.html` (one comment),
`app/android/src/main/AndroidManifest.xml` (one comment)

## Problem

At 8ee010b36:

1. Root `CLAUDE.md:471-474`, Build section:

   > read with `project.property("campfire.x")`: the app version, the Android version code and the iOS build number,
   > the Android release signing values, the Dropbox app key, which of its four distributions a desktop build is, the
   > Mac App Store build number and signing, the Microsoft Store package identity, and whether the web distribution is
   > precompressed.

   `gradle.properties` has one `campfire.buildNumber` for every platform (the bullet right above says so), and there
   is no Android version code, iOS build number or Mac App Store build number property; nor does any property say
   which distribution a desktop build is (the store build is decided by the task name, `isMacAppStoreBuild` in
   `app/desktop/build.gradle.kts`, and the store by `platformStore` at run time). The properties `gradle.properties`
   declares are `versionName`, `buildNumber`, `android.{keyAlias,keyPassword,keystoreFile,keystorePassword}`,
   `mac.{signingIdentity,signingKeychain,provisioningProfile,runtimeProvisioningProfile}`,
   `windows.{displayName,identityName,publisher,publisherDisplayName,sdkBinDirectory}`, `dropbox.appKey` and
   `web.precompress`.

2. `index.html:43-45`:

   ```html
   <!-- The decisions behind keeping the app in the browser (see the end of the page), apart from everything that
        acts on them so that app/web/tests/launch.test.cjs can run them as they are. ... -->
   ```

   There is no `launch.test.cjs`; the test that loads this script is `app/web/tests/offline.test.cjs` (`tests.yml`
   runs `opfs-writer.test.cjs` and `offline.test.cjs`).

3. `AndroidManifest.xml:13-16`:

   ```xml
   <!--
     The one thing Campfire ever reaches the network for: the cloud folder the user connected in Settings. Nothing
     else in the app talks to anything, and sync is off until the user turns it on.
   -->
   <uses-permission android:name="android.permission.INTERNET" />
   ```

   Since cover art, the permission also serves the cover downloads and the MusicBrainz / iTunes search (root
   `CLAUDE.md`'s opening paragraph and Cover art section).

## Fix

1. In the root `CLAUDE.md`, replace only the words "the app version, the Android version code and the iOS build
   number, the Android release signing values, the Dropbox app key, which of its four distributions a desktop build
   is, the Mac App Store build number and signing, the Microsoft Store package identity, and whether the web
   distribution is precompressed" with "the app version and the build number, the Android release signing values, the
   Dropbox app key, the Mac App Store signing, the Microsoft Store package identity, and whether the web distribution
   is precompressed". Other lanes edit this file too; touch nothing else.
2. In `index.html`, `app/web/tests/launch.test.cjs` → `app/web/tests/offline.test.cjs`.
3. In `AndroidManifest.xml`, replace the comment with: "Campfire reaches the network for two things the user asks
   for: the cloud folder they connected in Settings, which is off until they do, and the cover images their songs
   name and the cover search, which the Cover art switch in Settings turns off. Nothing else in the app talks to
   anything."

## Tests

None: documentation and comments.

## Manual check

None.
