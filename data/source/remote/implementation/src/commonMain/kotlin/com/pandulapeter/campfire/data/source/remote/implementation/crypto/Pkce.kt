@file:OptIn(ExperimentalEncodingApi::class, ExperimentalUuidApi::class)

package com.pandulapeter.campfire.data.source.remote.implementation.crypto

import com.pandulapeter.campfire.data.source.remote.api.hashing.Sha256
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The public client half of OAuth 2.0: a random verifier stays on the device, only its hash travels with the
 * authorization request, and the token exchange proves the two belong together. This is what lets Campfire talk to
 * a cloud service with no client secret, and so with no server of its own to keep one on.
 */
internal object Pkce {

    /**
     * 96 characters of the unreserved set, comfortably inside the 43…128 the specification allows. Built from
     * [Uuid], which is the one source of randomness available on all four targets.
     */
    fun createVerifier() = buildString {
        while (length < VERIFIER_LENGTH) {
            append(Uuid.random().toHexString())
        }
    }.take(VERIFIER_LENGTH)

    fun createChallenge(verifier: String) = base64Url(Sha256.hash(verifier.encodeToByteArray()))

    /** A value echoed back in the redirect, so that a redirect nobody asked for can be told apart from a real one. */
    fun createState() = Uuid.random().toHexString()

    private fun base64Url(bytes: ByteArray) = Base64.UrlSafe.encode(bytes).trimEnd('=')

    private const val VERIFIER_LENGTH = 96
}
