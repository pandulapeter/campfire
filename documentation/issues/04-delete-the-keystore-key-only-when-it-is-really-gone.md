# 04 — Delete the Keystore key only when it is really gone

## What the user sees

On Android, the user opens Campfire and finds sync disconnected. Settings shows no account, and Dropbox has to be
authorized again from scratch. Nothing says why. Doing it again works, and then a week later it happens again.

This is the shape of a transient Android Keystore failure. Some OEM builds — the ones with a flaky Keymaster HAL,
and any device under memory pressure right after boot — return a `KeyStoreException` or an
`UnrecoverableKeyException` for a key that is perfectly intact, and answer correctly a second later. Campfire
treats that answer as proof the key is gone, deletes the key *and* the encrypted credentials file, and the refresh
token is destroyed. The Keystore recovering afterwards changes nothing: the ciphertext is gone too.

The user's library is safe — this is only the sync credentials — but a user who had sync set up is silently signed
out of it, and until they notice, nothing on that phone is being backed up to their cloud folder.

## Cause

`data/source/local/implementation/src/androidMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/secret/SecretStore.android.kt:39-56`,
verified at HEAD `984861e4`:

```kotlin
    override suspend fun load(key: String): String? = withContext(Dispatchers.IO) {
        val fileName = encryptedFileName(key)
        val bytes = fileStorage.readBytes(StorageDirectory.PREFERENCES, fileName) ?: return@withContext null
        try {
            if (bytes.size <= IV_LENGTH_BYTES) throw GeneralSecurityException("The stored secret is truncated.")
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_LENGTH_BITS, bytes, 0, IV_LENGTH_BYTES))
            cipher.doFinal(bytes, IV_LENGTH_BYTES, bytes.size - IV_LENGTH_BYTES).decodeToString()
        } catch (exception: GeneralSecurityException) {
            // A key the system has invalidated, or a file this key did not write, can never be decrypted again. Keeping
            // either would leave the user unable to connect, so both go and the value is simply not there: the
            // account is connected again and a new key encrypts what that writes.
            println("Could not decrypt the stored secret: ${exception::class.simpleName}")
            fileStorage.delete(StorageDirectory.PREFERENCES, fileName)
            keyStore().deleteEntry(KEY_ALIAS)
            null
        }
    }
```

The comment describes two conditions that really are permanent. The `catch` is `GeneralSecurityException`, which
is the base class of essentially everything the JCA throws, and the try block calls `secretKey()` (line 73), so
what it actually covers is:

- `AEADBadTagException` — the tag did not verify: the file was not written by this key. Permanent.
- `KeyPermanentlyInvalidatedException` — the system invalidated the key (a changed lock screen, a new fingerprint).
  Permanent, and its name says so.
- `KeyStoreException` — "the Keystore is not available", "keystore operation failed", the transient HAL answers.
  **Not permanent.**
- `UnrecoverableKeyException` — the key could not be recovered *this time*. **Not permanent.**
- `NoSuchAlgorithmException`, `NoSuchPaddingException`, `InvalidAlgorithmParameterException` — programming errors
  or a device without the algorithm. Not something deleting the user's credentials helps with.

`KeyStoreException` and `UnrecoverableKeyException` both extend `GeneralSecurityException`, so both take the
branch that deletes.

The upper layer already handles a throw correctly, which is what makes the fix cheap.
`data/source/local/implementation/src/commonMain/.../source/SyncStateLocalSourceImpl.kt:25-34`:

```kotlin
    /** Unreadable credentials are treated as none, which the user answers by connecting again. */
    override suspend fun loadSyncCredentials() = try {
        secretStore.load(CREDENTIALS_FILE_NAME) ?: migratePlainFileIfPresent()
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        // The message of a failure is free to quote what it failed on, which here would be a token.
        println("Could not read the sync credentials: ${exception::class.simpleName}")
        null
    }
```

So rethrowing rather than deleting produces exactly the same *visible* behaviour on the failing launch — the app
starts disconnected — and leaves the key and the ciphertext intact, so the next launch reconnects on its own.

## The change

Narrow the catch to the two permanent failures and let everything else out as a storage failure.

```kotlin
        try {
            if (bytes.size <= IV_LENGTH_BYTES) throw AEADBadTagException("The stored secret is truncated.")
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_LENGTH_BITS, bytes, 0, IV_LENGTH_BYTES))
            cipher.doFinal(bytes, IV_LENGTH_BYTES, bytes.size - IV_LENGTH_BYTES).decodeToString()
        } catch (exception: GeneralSecurityException) {
            // Only two of these can never come right. A tag that does not verify is a file this key did not write,
            // and a permanently invalidated key is one the system took away when the lock screen changed; both mean
            // the ciphertext is unreadable for good, so both go and the user connects again. Every other
            // GeneralSecurityException is the Keystore having a bad moment - a KeyStoreException or an
            // UnrecoverableKeyException, which some OEM builds answer with under memory pressure and then answer
            // correctly a second later. Deleting the key for one of those destroys a refresh token that was never
            // lost, so it is reported as a storage failure instead and the next launch reads the same file again.
            if (exception !is AEADBadTagException && exception !is KeyPermanentlyInvalidatedException) {
                throw LibraryStorageException("Could not read the stored secret.", exception)
            }
            println("Could not decrypt the stored secret: ${exception::class.simpleName}")
            fileStorage.delete(StorageDirectory.PREFERENCES, fileName)
            keyStore().deleteEntry(KEY_ALIAS)
            null
        }
```

Details that matter:

- The truncation check on line 43 currently throws a bare `GeneralSecurityException` to reach the delete branch.
  With the branch narrowed it must throw `AEADBadTagException` instead, or a truncated file would be reported as a
  storage failure and never cleaned up. A file too short to hold an IV plus a tag genuinely cannot have been
  written by this key, so `AEADBadTagException` is not a lie.
- `KeyPermanentlyInvalidatedException` is `android.security.keystore.KeyPermanentlyInvalidatedException`; it
  extends `InvalidKeyException` extends `KeyException` extends `GeneralSecurityException`, so the order of the
  `is` checks does not matter.
- `AEADBadTagException` is `javax.crypto.AEADBadTagException`, a subclass of `BadPaddingException`. Note that the
  Android Keystore has historically thrown a plain `BadPaddingException` or an `IllegalBlockSizeException` (with an
  `AEADBadTagException` cause) for a failed tag on some API levels. Widen the permanent set to cover that:
  `exception is AEADBadTagException || exception.cause is AEADBadTagException || exception is KeyPermanentlyInvalidatedException`.
  Write it as a small private extension so the condition reads:

  ```kotlin
  /** Whether the ciphertext can never be read again, as opposed to the Keystore having refused this one attempt. */
  private val GeneralSecurityException.isPermanent
      get() = this is KeyPermanentlyInvalidatedException || this is AEADBadTagException || cause is AEADBadTagException
  ```
- `LibraryStorageException` is `com.pandulapeter.campfire.data.source.local.api.LibraryStorageException`, already
  the type this module throws for "the file is there and cannot be used"
  (`JvmFileStorage.failingAsStorage`), and `AndroidSecretStore` already depends on `:data:source:local:api`
  transitively through `FileStorage`. Check the import; if the module does not see it, use it anyway — this is the
  right type, and the dependency is there.
- `save()` is untouched. A write that fails already throws, and it should.

### What the user sees afterwards

Nothing new on the failing launch: `loadSyncCredentials` catches the throw and the app starts disconnected, as it
does today. The difference is that the *next* launch works. Do **not** add a message: telling the user that the
Keystore had a bad moment is not something they can act on, and the app recovers on its own. If the failure is
permanent the existing delete branch still runs and the user connects again, which is the one case that needs
their attention and already gets it.

## Tests

`AndroidSecretStore` is Android-only code with no Robolectric or instrumentation test infrastructure in this
project — only pure logic is tested here, on the desktop target, and the Keystore is neither. So:

- **No new unit test.** State this in the commit message rather than adding a test dependency for one class.
- If the reviewer wants the decision pinned, extract the predicate:

  ```kotlin
  internal fun GeneralSecurityException.isPermanentDecryptionFailure(): Boolean
  ```

  into a file in `androidMain` and note that it still cannot be tested from `commonTest`. Not recommended; the
  `when` is three terms and reads as its own proof.
- The suite that must stay green is the existing one, which this change cannot touch:

  ```
  ./gradlew :chordpro:desktopTest :domain:implementation:desktopTest :data:source:local:implementation:desktopTest :data:source:remote:api:desktopTest :data:source:remote:implementation:desktopTest :data:repository:implementation:desktopTest
  ```

## Verification

```
./gradlew :app:android:assembleDebug
./gradlew :chordpro:desktopTest :domain:implementation:desktopTest :data:source:local:implementation:desktopTest :data:source:remote:api:desktopTest :data:source:remote:implementation:desktopTest :data:repository:implementation:desktopTest
```

Manual, **needs an Android device or emulator**:

1. Connect Dropbox on the device. Confirm `preferences/sync-credentials.bin` exists
   (`adb shell run-as com.pandulapeter.campfire.debug ls files/preferences`, or whatever the debug application id
   is — check `app/android/build.gradle.kts`).
2. Kill the app and start it again. The account is still connected. (Baseline.)
3. **The permanent case:** change the device's lock screen (set a PIN, or remove one), which is what invalidates a
   Keystore key on many devices. Start the app: it must start disconnected, and the `.bin` file must be gone.
   Reconnect and confirm it works.
4. **The transient case**, which can only be simulated: temporarily patch `secretKey()` to throw
   `KeyStoreException("simulated")` on the first call of the process, build, connect, restart. The app must start
   disconnected **and the `.bin` file must still be there**; remove the patch, restart again, and the account must
   come back without the user doing anything. Revert the patch before committing.
5. Confirm the log line on the transient path names `KeyStoreException` and never quotes a token — the existing
   `println` prints `exception::class.simpleName` only, and the new `LibraryStorageException` message must keep to
   the same rule: no `name`, no bytes, no value in the message.

## Docs

`data/source/local/implementation/CLAUDE.md` — this sentence is what becomes untrue:

> `AndroidSecretStore` encrypts it with an AES-GCM key generated inside the Android Keystore (never
> `security-crypto`, which is deprecated) and writes the IV and ciphertext as `preferences/sync-credentials.bin`;
> a key that became unusable, or a file it cannot decrypt, is deleted and reads as no credentials, so the user
> connects again rather than being stuck.

"a key that became unusable" is now specifically a key the system *permanently invalidated*; a Keystore that
refused one attempt is reported as a storage failure and the file is kept, so the next launch reads it again.

Nothing in the root `CLAUDE.md` or in `documentation/` describes this level of detail, so nothing else changes.

## Files touched

- `data/source/local/implementation/src/androidMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/secret/SecretStore.android.kt`
- `data/source/local/implementation/CLAUDE.md`

## Depends on

Nothing.

## Rules

- Load the `code-style` skill before the first edit: the "why, not what" comment voice matters here, because the
  whole change is a distinction between two exception classes and the comment is what carries it.
- No new UI strings.
- `commonMain` is untouched; this is `androidMain` only, so `java.*` and `javax.*` imports are fine.
- Keep the existing rule that nothing in a log line or an exception message may quote the secret.
