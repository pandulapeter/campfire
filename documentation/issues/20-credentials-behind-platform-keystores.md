# 20 · Sync credentials behind the Android Keystore and the iOS Keychain

**Severity:** medium (a refresh token is a long-lived credential in a plain file) · **Area:** `:data:source:local:api/implementation` · **Decision:** Android + iOS get a secret store; desktop and web keep the plain file

## Today

`SyncStateLocalSourceImpl` (`data/source/local/implementation/.../source/SyncStateLocalSourceImpl.kt`) writes the
credentials document (refresh token, access token, PKCE verifier) as `preferences/sync-credentials.json` on every
platform. Its interface KDoc already names the Keychain/Keystore as "the next step". Nothing above the local source
knows the shape of the document (it is an opaque string), which is what makes this a contained change.

## Design

A `SecretStore` in `:data:source:local:implementation` commonMain, one platform actual each, used only by
`SyncStateLocalSourceImpl` for the credentials document. The index stays a plain file (it holds hashes and revisions,
nothing secret).

```kotlin
/** A small string kept where the platform keeps secrets. Null when there is none. */
internal interface SecretStore {
    suspend fun load(key: String): String?
    suspend fun save(key: String, value: String?)
}
```

- **Android** (`androidMain`, `@Single class AndroidSecretStore(@Provided context: Context, fileStorage: FileStorage)`):
  an AES-256-GCM key in `AndroidKeyStore` (`KeyGenParameterSpec` with `PURPOSE_ENCRYPT or PURPOSE_DECRYPT`,
  `BLOCK_MODE_GCM`, `ENCRYPTION_PADDING_NONE`, alias `campfire.sync`, generated on first use). Encrypt the UTF-8
  bytes, store `iv + ciphertext` through the existing `FileStorage` as `preferences/sync-credentials.bin` (atomic
  write for free). Do **not** use `androidx.security:security-crypto` (deprecated). A key that became unusable
  (`KeyPermanentlyInvalidatedException`, `AEADBadTagException`) is treated as "no credentials": log, delete the file,
  return null — the user reconnects.
- **iOS** (`iosMain`, `@Single class IosSecretStore`): `kSecClassGenericPassword` with `kSecAttrService = "com.pandulapeter.campfire.sync"`,
  `kSecAttrAccount = key`, `kSecAttrAccessible = kSecAttrAccessibleAfterFirstUnlock` (a background sync must still
  read it). `SecItemCopyMatching` / `SecItemAdd` / `SecItemUpdate` / `SecItemDelete` via `platform.Security`; wrap the
  `CFDictionary` building in `memScoped` and `CFBridgingRetain`. `errSecItemNotFound` → null.
- **Desktop / web** (`desktopMain`, `wasmJsMain`): `@Single class FileSecretStore(fileStorage)` writing
  `preferences/<key>` as today. The interface KDoc says why: no keychain worth the dependency on desktop, no
  equivalent on the web.

`SyncStateLocalSourceImpl` gets `secretStore: SecretStore` and routes `loadSyncCredentials` / `saveSyncCredentials`
through it with key `sync-credentials.json` (so the desktop/web file name does not change).

## Migration

On the first `loadSyncCredentials` on Android/iOS: if the secret store has nothing and the plain
`preferences/sync-credentials.json` exists, read it, save it into the store, delete the file. One method,
`migratePlainFileIfPresent()`, called from `loadSyncCredentials`; log one line.

## Steps

1. Add the interface and the four actuals; the Koin scan finds annotated classes in platform source sets (see root
   `CLAUDE.md`, Koin section: `AndroidFileStorage` is the precedent).
2. Wire `SyncStateLocalSourceImpl`; add the migration.
3. Update `SyncStateLocalSource`'s KDoc (drop "the next step" sentence), `data/source/local/implementation/CLAUDE.md`,
   the root `CLAUDE.md` library layout block (`preferences/sync-credentials.json` → "on desktop and the web; the
   Keystore/Keychain on Android and iOS"), and `documentation/sync.md`.
4. The Android backup rules already exclude files (only `sharedpref` is included), so nothing changes there.

## Verification

- Android: connect, kill, relaunch → still connected; `run-as` into the app dir: no `sync-credentials.json`, a
  `.bin` that is not plaintext. Upgrade path: install the previous build, connect, install the new one → connected,
  file gone.
- iOS: same, and check the Keychain entry survives a relaunch and goes away on Disconnect.
- Desktop/web unchanged.
