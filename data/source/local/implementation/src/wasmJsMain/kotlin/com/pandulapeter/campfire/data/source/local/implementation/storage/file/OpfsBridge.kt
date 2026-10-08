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

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.Promise
import kotlin.js.toJsString

internal fun isOpfsAvailable(): Boolean =
    js("typeof navigator !== 'undefined' && navigator.storage != null && typeof navigator.storage.getDirectory === 'function'")

internal fun opfsRoot(): Promise<JsAny?> = js("navigator.storage.getDirectory()")

internal fun getDirectoryHandle(parent: JsAny, name: String): Promise<JsAny?> = js("parent.getDirectoryHandle(name, { create: true })")

/**
 * Resolves to `null` instead of rejecting with a `NotFoundError` when the file is not there. Every other rejection is
 * passed on: a file that is there but cannot be reached is not the same as a missing one.
 */
internal fun getFileHandle(parent: JsAny, name: String): Promise<JsAny?> = js(
    """parent.getFileHandle(name).catch(function (error) {
        if (error && error.name === 'NotFoundError') return null;
        throw error;
    })"""
)

/**
 * Every file of the directory as name, size and last modification time, separated by control characters.
 *
 * An entry that is gone by the time it is asked for its file is left out rather than failing the listing: a sync run
 * deletes files while the live rescan is listing the same directory, and on one thread the two interleave at every
 * `await`. A `NotFoundError` there says the file is not in the directory any more, which is what leaving it out of
 * the listing says too. Every other rejection still fails the listing, since a file that is there and cannot be
 * reached must never be reported as absent - sync plans a deletion for a file it cannot see.
 */
internal fun listEntries(directory: JsAny): Promise<JsString?> = js(
    """(async function () {
        var field = String.fromCharCode(0);
        var handles = [];
        for await (var entry of directory.entries()) if (entry[1].kind === 'file' && !entry[0].endsWith('.campfire-tmp') && !entry[0].endsWith('.campfire-commit') && !entry[0].endsWith('.campfire-new')) handles.push(entry);
        var files = await Promise.all(handles.map(function (entry) {
            return entry[1].getFile().catch(function (error) { if (error && error.name === 'NotFoundError') return null; throw error; });
        }));
        var entries = [];
        for (var i = 0; i < handles.length; i++) if (files[i]) entries.push(handles[i][0] + field + files[i].size + field + files[i].lastModified);
        return entries.join(String.fromCharCode(1));
    })()"""
)

internal fun listEntryNames(directory: JsAny): Promise<JsString?> = js(
    """(async function () {
        var names = [];
        for await (var name of directory.keys()) if (!name.endsWith('.campfire-tmp') && !name.endsWith('.campfire-commit') && !name.endsWith('.campfire-new')) names.push(name);
        return names.join(String.fromCharCode(1));
    })()"""
)

/**
 * The size and the last modification time of one file, separated by the same control character `listEntries` uses.
 * Resolves to `null` for a file deleted between the handle and the question, which `info`'s contract calls missing.
 */
internal fun fileInfo(handle: JsAny): Promise<JsString?> = js(
    """handle.getFile().then(function (file) {
        return file.size + String.fromCharCode(0) + file.lastModified;
    }).catch(function (error) {
        if (error && error.name === 'NotFoundError') return null;
        throw error;
    })"""
)

/**
 * The text of every file of [names] (separated by the same control character `listEntries` uses), all of them asked
 * for at once, one answer each and in order: the text as `TextDecoder` reads it behind a leading NUL, a lone SOH where
 * the file has to be decoded by `decodeLibraryText` instead (not valid UTF-8, or a NUL in it), `null` for a file that
 * is not there, and STX with the error's name and message for one that is there and could not be read. A failure is its
 * own answer rather than the batch's, and the texts cross into Kotlin as strings, where bytes would cross one call each.
 */
internal fun readFileTexts(directory: JsAny, names: String): Promise<JsArray<JsString?>> = js(
    """Promise.all((names.length === 0 ? [] : names.split(String.fromCharCode(1))).map(function (name) {
        return directory.getFileHandle(name).then(function (handle) { return handle.getFile(); }).then(function (file) {
            return file.arrayBuffer();
        }).then(function (buffer) {
            var text;
            try { text = new TextDecoder('utf-8', { fatal: true }).decode(buffer); }
            catch (error) { return String.fromCharCode(1); }
            return text.indexOf(String.fromCharCode(0)) >= 0 ? String.fromCharCode(1) : String.fromCharCode(0) + text;
        }).catch(function (error) {
            if (error && error.name === 'NotFoundError') return null;
            return String.fromCharCode(2) + ((error && error.name) || 'Error') + ': ' + ((error && error.message) || String(error));
        });
    }))"""
)

/**
 * `readFileTexts` for a library scan, which also answers each file's size and last modification time from the one
 * `getFile()` per name: behind the leading character come the size and the date, each followed by a space, and then
 * the text where the browser could decode it. ETX with the size and the date is a file larger than [maxSize], which is
 * never read. A name that is a directory (`listEntryNames` lists every kind of entry) resolves to `null` like a file
 * that is not there, since leaving it out of the listing is what `listEntries` does.
 */
internal fun readFileScans(directory: JsAny, names: String, maxSize: Double): Promise<JsArray<JsString?>> = js(
    """Promise.all((names.length === 0 ? [] : names.split(String.fromCharCode(1))).map(function (name) {
        return directory.getFileHandle(name).then(function (handle) { return handle.getFile(); }).then(function (file) {
            var head = file.size + ' ' + file.lastModified;
            if (file.size > maxSize) return String.fromCharCode(3) + head;
            return file.arrayBuffer().then(function (buffer) {
                var text;
                try { text = new TextDecoder('utf-8', { fatal: true }).decode(buffer); }
                catch (error) { return String.fromCharCode(1) + head; }
                return text.indexOf(String.fromCharCode(0)) >= 0 ? String.fromCharCode(1) + head : String.fromCharCode(0) + head + ' ' + text;
            });
        }).catch(function (error) {
            if (error && (error.name === 'NotFoundError' || error.name === 'TypeMismatchError')) return null;
            return String.fromCharCode(2) + ((error && error.name) || 'Error') + ': ' + ((error && error.message) || String(error));
        });
    }))"""
)

/**
 * The file's bytes as a string of one character per byte, or `null` for a file that is not there. A string is the
 * one thing that crosses the Kotlin/Wasm boundary in bulk; an `Int8Array` is read one call per element. It is built a
 * chunk at a time, since `fromCharCode` takes its bytes as arguments and an argument list has a limit.
 */
internal fun readFileLatin1(handle: JsAny): Promise<JsString?> = js(
    """handle.getFile().then(function (file) { return file.arrayBuffer(); }).then(function (buffer) {
        var bytes = new Uint8Array(buffer);
        var parts = [];
        for (var i = 0; i < bytes.length; i += 0x8000) parts.push(String.fromCharCode.apply(null, bytes.subarray(i, i + 0x8000)));
        return parts.join('');
    }).catch(function (error) {
        if (error && error.name === 'NotFoundError') return null;
        throw error;
    })"""
)

/** The bytes `toLatin1JsString` carried across, for `writeFile`, which writes a string as UTF-8. */
internal fun latin1ToBytes(text: JsString): JsAny = js(
    """(function () {
        var bytes = new Uint8Array(text.length);
        for (var i = 0; i < text.length; i++) bytes[i] = text.charCodeAt(i);
        return bytes;
    })()"""
)

internal fun String.latin1Bytes() = ByteArray(length) { this[it].code.toByte() }

internal fun ByteArray.toLatin1JsString() = CharArray(size) { (this[it].toInt() and 0xFF).toChar() }.concatToString().toJsString()

internal const val FIELD_SEPARATOR_CODE = 0
internal const val ENTRY_SEPARATOR_CODE = 1
