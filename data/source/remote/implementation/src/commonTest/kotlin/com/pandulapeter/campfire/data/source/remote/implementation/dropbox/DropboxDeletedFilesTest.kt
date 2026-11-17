/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.remote.implementation.dropbox

import com.pandulapeter.campfire.data.model.library.LibraryFileKind
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A deleted library file brought back from Dropbox's history, and every refusal of that read as "not restored". */
class DropboxDeletedFilesTest {

    private val restored = mutableListOf<String>()

    @Test
    fun `the newest revision of a deleted file is restored at its own path`() = runTest {
        var listedPath: JsonPrimitive? = null
        val provider = dropboxProvider { request ->
            val body = Json.parseToJsonElement((request.body as TextContent).text) as JsonObject
            when (request.url.toString()) {
                LIST_REVISIONS_URL -> {
                    listedPath = body["path"] as JsonPrimitive
                    respondJson(revisions(isDeleted = true, "rev3", "rev2"))
                }
                else -> {
                    restored += (body["rev"] as JsonPrimitive).content
                    respondJson("""{"name":"$NAME","rev":"rev4"}""")
                }
            }
        }

        assertTrue(provider.restoreDeleted(LibraryFileKind.FILE, NAME))
        assertEquals(JsonPrimitive("/files/$NAME"), listedPath)
        assertEquals(listOf("rev3"), restored)
    }

    @Test
    fun `a revision Dropbox will not restore gives way to the one below it, three at most`() = runTest {
        val provider = dropboxProvider { request ->
            when (request.url.toString()) {
                LIST_REVISIONS_URL -> respondJson(revisions(isDeleted = true, "rev5", "rev4", "rev3", "rev2"))
                else -> {
                    restored += (Json.parseToJsonElement((request.body as TextContent).text) as JsonObject)["rev"].toString()
                    respondJson("""{"error_summary":"invalid_revision/..","error":{}}""", HttpStatusCode.Conflict)
                }
            }
        }

        assertFalse(provider.restoreDeleted(LibraryFileKind.FILE, NAME))
        assertEquals(3, restored.size)
    }

    @Test
    fun `a name with no history is not restored`() = runTest {
        val provider = dropboxProvider { respondJson("""{"error_summary":"path/not_found/..","error":{}}""", HttpStatusCode.Conflict) }

        assertFalse(provider.restoreDeleted(LibraryFileKind.FILE, NAME))
    }

    @Test
    fun `a connection without the scope restores nothing and fails nothing`() = runTest {
        val provider = dropboxProvider {
            respondJson(
                """{"error":{".tag":"missing_scope","required_scope":"files.content.read"},"error_summary":"missing_scope/"}""",
                HttpStatusCode.Unauthorized,
            )
        }

        assertFalse(provider.restoreDeleted(LibraryFileKind.FILE, NAME))
    }

    @Test
    fun `a name the folder holds again needs no restore`() = runTest {
        val provider = dropboxProvider { request ->
            check(request.url.toString() == LIST_REVISIONS_URL) { "Only the history is read." }
            respondJson(revisions(isDeleted = false, "rev2"))
        }

        assertTrue(provider.restoreDeleted(LibraryFileKind.FILE, NAME))
    }

    private fun revisions(isDeleted: Boolean, vararg revisions: String) =
        """{"is_deleted":$isDeleted,"entries":[${revisions.joinToString { """{"name":"$NAME","rev":"$it"}""" }}]}"""

    private companion object {
        const val NAME = "0123456789abcdef.png"
        const val LIST_REVISIONS_URL = "https://api.dropboxapi.com/2/files/list_revisions"
    }
}
