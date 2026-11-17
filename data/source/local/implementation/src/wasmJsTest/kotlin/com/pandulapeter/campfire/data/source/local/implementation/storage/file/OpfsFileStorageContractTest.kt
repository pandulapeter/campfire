/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
@file:OptIn(ExperimentalWasmJsInterop::class)

package com.pandulapeter.campfire.data.source.local.implementation.storage.file

import kotlinx.coroutines.await
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.Promise
import kotlin.uuid.Uuid

/**
 * The contract run in headless Chrome by `wasmJsBrowserTest`, against the origin private file system the app uses, each
 * test in a folder of its own, since an origin has one file system.
 */
class OpfsFileStorageContractTest : FileStorageContractTest() {

    private val root = "campfire-file-storage-${Uuid.random()}"

    override fun newStorage(): FileStorage = OpfsFileStorage(rootSegments = listOf(root))

    /**
     * Nothing outside the page removes a directory of the origin's file system, and the storage keeps the handles it
     * resolved, so the nearest thing that can happen, every file of the directory going, stands in for it.
     */
    override suspend fun deleteDirectoryBehindItsBack(directory: StorageDirectory) {
        emptyOpfsDirectory(path(directory)).await<JsAny?>()
    }

    override suspend fun makeDirectoryUnderFileName(directory: StorageDirectory, name: String) {
        makeOpfsDirectory(path(directory) + "/" + name).await<JsAny?>()
    }

    private fun path(directory: StorageDirectory) = (listOf(root) + directory.pathSegments).joinToString("/")
}

private fun emptyOpfsDirectory(path: String): Promise<JsAny?> = js(
    """(async function () {
        var handle = await navigator.storage.getDirectory();
        var segments = path.split('/');
        for (var i = 0; i < segments.length; i++) handle = await handle.getDirectoryHandle(segments[i], { create: true });
        var names = [];
        for await (var name of handle.keys()) names.push(name);
        for (var j = 0; j < names.length; j++) await handle.removeEntry(names[j], { recursive: true });
        return null;
    })()"""
)

private fun makeOpfsDirectory(path: String): Promise<JsAny?> = js(
    """(async function () {
        var handle = await navigator.storage.getDirectory();
        var segments = path.split('/');
        for (var i = 0; i < segments.length; i++) handle = await handle.getDirectoryHandle(segments[i], { create: true });
        return null;
    })()"""
)
