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
import android.security.keystore.KeyProperties
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.FileStorage
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.StorageDirectory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import java.security.GeneralSecurityException
import java.security.KeyStore
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
