/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.storage.secret

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import com.pandulapeter.campfire.data.source.local.api.LibraryStorageException
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.FileStorage
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.StorageDirectory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The Android [SecretStore]: the value is encrypted with an AES-GCM key that is generated inside the Android Keystore
 * and never leaves it, and only the initialization vector and the ciphertext are written to the preferences directory,
 * as a `.bin` file named after the key. The file on its own is worthless, on this device or any other.
 *
 * Not `androidx.security:security-crypto`, which is deprecated and would be a dependency for twenty lines of this.
 */
@Single
internal class AndroidSecretStore(
    private val fileStorage: FileStorage,
) : SecretStore {

    override suspend fun load(key: String): String? = withContext(Dispatchers.IO) {
        val fileName = encryptedFileName(key)
        val bytes = fileStorage.readBytes(StorageDirectory.PREFERENCES, fileName) ?: return@withContext null
        try {
            // Too short to hold an initialization vector and a tag, so this key cannot have written it.
            if (bytes.size <= IV_LENGTH_BYTES) throw AEADBadTagException("The stored secret is truncated.")
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_LENGTH_BITS, bytes, 0, IV_LENGTH_BYTES))
            cipher.doFinal(bytes, IV_LENGTH_BYTES, bytes.size - IV_LENGTH_BYTES).decodeToString()
        } catch (exception: GeneralSecurityException) {
            // Only two of these can never come right. A tag that does not verify is a file this key did not write, and
            // a permanently invalidated key is one the system took away when the lock screen changed; both mean the
            // ciphertext is unreadable for good, so both go and the user connects again. Every other
            // GeneralSecurityException is the Keystore having a bad moment - a KeyStoreException or an
            // UnrecoverableKeyException, which some OEM builds answer with under memory pressure and then answer
            // correctly a second later. Deleting the key for one of those destroys a refresh token that was never
            // lost, so it is reported as a storage failure instead and the next launch reads the same file again.
            if (!exception.isPermanent) throw LibraryStorageException("Could not read the stored secret.", exception)
            println("Could not decrypt the stored secret: ${exception::class.simpleName}")
            fileStorage.delete(StorageDirectory.PREFERENCES, fileName)
            keyStore().deleteEntry(KEY_ALIAS)
            null
        }
    }

    override suspend fun save(key: String, value: String?) = withContext(Dispatchers.IO) {
        val fileName = encryptedFileName(key)
        if (value == null) {
            fileStorage.delete(StorageDirectory.PREFERENCES, fileName)
        } else {
            // The Keystore picks a random initialization vector itself and refuses one chosen by the caller.
            val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
            val ciphertext = cipher.doFinal(value.encodeToByteArray())
            check(cipher.iv.size == IV_LENGTH_BYTES) { "Unexpected initialization vector length: ${cipher.iv.size}." }
            fileStorage.writeBytes(StorageDirectory.PREFERENCES, fileName, cipher.iv + ciphertext)
        }
    }

    /**
     * Whether the ciphertext can never be read again, as opposed to the Keystore having refused this one attempt. A
     * failed tag arrives as a plain `BadPaddingException` or `IllegalBlockSizeException` on some API levels, carrying
     * the `AEADBadTagException` as its cause.
     */
    private val GeneralSecurityException.isPermanent
        get() = this is KeyPermanentlyInvalidatedException || this is AEADBadTagException || cause is AEADBadTagException

    private fun encryptedFileName(key: String) = "${key.substringBeforeLast('.')}.bin"

    private fun secretKey() = keyStore().getKey(KEY_ALIAS, null) as? SecretKey ?: KeyGenerator
        .getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        .apply {
            init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(KEY_SIZE_BITS)
                    .build()
            )
        }
        .generateKey()

    private fun keyStore() = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "campfire.sync"
        const val KEY_SIZE_BITS = 256
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH_BYTES = 12
        const val TAG_LENGTH_BITS = 128
    }
}
