# Declare the system boot time API with reason 35F9.1 in the iOS privacy manifest

**Kind:** store-policy · **Severity:** low · **Platforms:** iOS
**Challenged:** amended — the file does have an explaining comment and `app/ios/CLAUDE.md` does list the declared
categories ("the one required-reason category"), so both edits are now spelled out; `nm -u` on the device
(`iosArm64`) release framework confirms `_mach_absolute_time`, and no `systemUptime` / `NSUserDefaults` string is in it.
**Files:** `app/ios/iosApp/iosApp/PrivacyInfo.xcprivacy`, `app/ios/CLAUDE.md`

## Problem

The release framework imports `mach_absolute_time` (`nm -u` on
`app/ios/build/bin/iosSimulatorArm64/releaseFramework/ComposeApp.framework/ComposeApp` lists `_mach_absolute_time`
next to `_stat`, `_fstat` and `NSFileModificationDate`). It comes in through a library (Kotlin/Native's clock, skiko,
coroutines or Ktor); no project source calls it. `mach_absolute_time` is a required-reason API
(`NSPrivacyAccessedAPICategorySystemBootTime`), and `PrivacyInfo.xcprivacy` declares only the file timestamp category:

```xml
			<string>NSPrivacyAccessedAPICategoryFileTimestamp</string>
			<key>NSPrivacyAccessedAPITypeReasons</key>
			...
				<string>C617.1</string>
				<string>3B52.1</string>
```

Apple's upload checks can mail ITMS-91053 ("Missing API declaration") for an undeclared category, and a future check
may refuse the upload. Builds with the same library versions have been accepted, so this is insurance, but it is
also simply true now: the metronome measures elapsed time inside the app.

## Fix

Add one more dictionary to the `NSPrivacyAccessedAPITypes` array, matching the existing entry's formatting:

```xml
		<dict>
			<key>NSPrivacyAccessedAPIType</key>
			<string>NSPrivacyAccessedAPICategorySystemBootTime</string>
			<key>NSPrivacyAccessedAPITypeReasons</key>
			<array>
				<string>35F9.1</string>
			</array>
		</dict>
```

`35F9.1`: "access the system boot time in order to measure the amount of time that has elapsed between events that
occurred within the app or to perform calculations to enable timers" — the only use. Run
`plutil -lint app/ios/iosApp/iosApp/PrivacyInfo.xcprivacy`.

Extend the XML comment above `NSPrivacyAccessedAPITypes`, after "(3B52.1).": "The runtime's monotonic clock reads
mach_absolute_time, which every elapsed time in the app is measured with, the metronome's included (35F9.1)."

In `app/ios/CLAUDE.md`'s `PrivacyInfo.xcprivacy` paragraph: "and the one required-reason category the linked binary
references: file timestamps, …(3B52.1)." becomes "and the two required-reason categories the linked binary references:
file timestamps, …(3B52.1), and the system boot time, `mach_absolute_time` from the runtime's monotonic clock, for
elapsed time inside the app (35F9.1)."; and the list of likely newcomers at its end becomes
"(`NSUserDefaults` is the likely one; `systemUptime` would fall under the boot time category already declared)".

## Tests

None (a plist). `plutil -lint` must pass; `./gradlew :app:ios:linkDebugFrameworkIosSimulatorArm64` is unaffected.

## Manual check

The next App Store Connect upload produces no ITMS-91053 email naming SystemBootTime.
