/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
@file:OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)

package com.pandulapeter.campfire.data.source.local.implementation.storage.secret

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlock
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * The iOS [SecretStore]: one generic password item in the Keychain per key.
 *
 * Readable after the first unlock since the device started rather than only while it is unlocked, because a sync run
 * the app started carries on in the background, where the device may well be locked by the time it needs a token.
 */
@Single
internal class IosSecretStore : SecretStore {

    override suspend fun load(key: String): String? = withContext(Dispatchers.IO) {
        memScoped {
            val result = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(
                query(key, kSecReturnData to kCFBooleanTrue, kSecMatchLimit to kSecMatchLimitOne),
                result.ptr,
            )
            when (status) {
                errSecSuccess -> (CFBridgingRelease(result.value) as? NSData)
                    ?.let { NSString.create(data = it, encoding = NSUTF8StringEncoding) }
                    ?.toString()

                errSecItemNotFound -> null
                else -> throw IllegalStateException("The Keychain could not read \"$key\": $status.")
            }
        }
    }

    override suspend fun save(key: String, value: String?) = withContext(Dispatchers.IO) {
        memScoped {
            val status = if (value == null) {
                SecItemDelete(query(key)).takeUnless { it == errSecItemNotFound } ?: errSecSuccess
            } else {
                val data = retained(NSString.create(string = value).dataUsingEncoding(NSUTF8StringEncoding))
                val attributes = arrayOf<Pair<CFStringRef?, CFTypeRef?>>(
                    kSecValueData to data,
                    kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlock,
                )
                SecItemUpdate(query(key), dictionary(*attributes)).takeUnless { it == errSecItemNotFound }
                    ?: SecItemAdd(query(key, *attributes), null)
            }
            check(status == errSecSuccess) { "The Keychain could not write \"$key\": $status." }
        }
    }

    private fun MemScope.query(key: String, vararg attributes: Pair<CFStringRef?, CFTypeRef?>) = dictionary(
        kSecClass to kSecClassGenericPassword,
        kSecAttrService to retained(SERVICE),
        kSecAttrAccount to retained(key),
        *attributes,
    )

    /** A Core Foundation dictionary that lives as long as the scope, retaining what is put into it. */
    private fun MemScope.dictionary(vararg entries: Pair<CFStringRef?, CFTypeRef?>): CFDictionaryRef? {
        val dictionary = CFDictionaryCreateMutable(null, entries.size.convert(), kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
        entries.forEach { (key, value) -> CFDictionaryAddValue(dictionary, key, value) }
        defer { CFRelease(dictionary) }
        return dictionary
    }

    /** A Kotlin object handed to Core Foundation for as long as the scope lives. */
    private fun MemScope.retained(value: Any?): CFTypeRef? = CFBridgingRetain(value).also { defer { CFRelease(it) } }

    private companion object {
        const val SERVICE = "com.pandulapeter.campfire.sync"
    }
}
