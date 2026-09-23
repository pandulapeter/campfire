/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire

import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.concurrent.thread

/**
 * Decides whether this process is the one that opens the library in [dataDirectory], and hands [paths] to the one
 * that already has if it is not.
 *
 * Every repository reads its files once and writes them whole from what it remembers, which is right for one
 * process and makes two of them take back each other's changes. So the first process locks a file for as long as
 * it lives and listens on the loopback interface, and every later one - which is what "open with" starts on
 * Windows and Linux while Campfire is running, once per selected file - sends it what it was asked to open and
 * leaves. The lock is the operating system's, so it goes with the process however that ends: the lock *file* being
 * there says nothing, and a crash leaves nothing to clean up.
 *
 * This has to run before Koin is started, since nothing of a second process may touch the library.
 *
 * @param paths Absolute paths: the two processes do not share a working directory.
 * @param onActivated Called on a background thread each time another process handed over, with the paths it was
 *   started with - none when it was started with none, which still means "come forward".
 * The lock is asked for again before every hand-over attempt, and the attempts are more than one, for two reasons.
 * The running instance may be one that started a moment ago - five files opened together are five processes, and the
 * four that lost the lock can be here before the winner has written its port, which is why the endpoint file is read
 * again each time too. And the holder may be a process that is closing: it has stopped listening but still holds the
 * lock, and once it is gone this process is the one that opens the library, with the lock and a listener of its own,
 * rather than one that starts next to whatever comes after it.
 *
 * @return False if the running instance took over and this process should exit. True means carry on starting,
 *   which is also the answer when the lock cannot be asked for at all (a read-only or a network home directory) or
 *   when the running instance does not answer: an app that starts twice is the lesser evil next to one that does
 *   not start.
 */
internal fun claimSingleInstance(
    dataDirectory: File,
    paths: List<String>,
    onActivated: (paths: List<String>) -> Unit,
): Boolean {
    repeat(HAND_OVER_ATTEMPTS) {
        val lock = try {
            dataDirectory.mkdirs()
            val channel = FileChannel.open(File(dataDirectory, LOCK_FILE_NAME).toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
            channel.tryLock() ?: run {
                channel.close()
                null
            }
        } catch (exception: Exception) {
            println("Could not ask for the single instance lock: ${exception.message}")
            return true
        }
        if (lock != null) {
            heldLock = lock
            startListening(dataDirectory, onActivated)
            return true
        }
        if (sendPaths(dataDirectory, paths)) return false
        Thread.sleep(HAND_OVER_RETRY_MILLIS)
    }
    println("The running instance did not answer, starting next to it.")
    return true
}

/**
 * Stops accepting other processes' files while keeping the lock, for an instance that has decided to exit: it would
 * only acknowledge them and go, and the file would be opened by nobody. The lock stays until the process is gone, so
 * that a newcomer waits for it (see [claimSingleInstance]) rather than reading the library while this process may
 * still be writing it; releasing it by hand is left to the operating system for that reason.
 */
internal fun stopListeningForOtherInstances() {
    try {
        listeningSocket?.close()
    } catch (exception: IOException) {
        println("Could not stop listening for other instances: ${exception.message}")
    }
    listeningSocket = null
    listeningEndpointFile?.delete()
}

/**
 * Referenced for the life of the process on purpose: a lock belongs to its channel, and a channel nobody holds is
 * closed by the garbage collector, which would let the next process in while this one is still running. Nothing reads
 * it, so the release build keeps it with a rule in `proguard-rules.pro` that names it: renaming or moving it means
 * changing that rule too.
 */
private var heldLock: FileLock? = null

private var listeningSocket: ServerSocket? = null

private var listeningEndpointFile: File? = null

private fun startListening(dataDirectory: File, onActivated: (paths: List<String>) -> Unit) {
    val endpointFile = File(dataDirectory, ENDPOINT_FILE_NAME)
    try {
        // Whatever is there was written by a process that is gone, and a newcomer that read it would knock on a
        // port that is closed, or somebody else's by now.
        endpointFile.delete()
        val serverSocket = ServerSocket(0, BACKLOG, InetAddress.getByName(LOOPBACK_ADDRESS))
        listeningSocket = serverSocket
        val token = ByteArray(TOKEN_BYTES).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it) }
        endpointFile.writeForOwnerOnly("${serverSocket.localPort}\n$token\n")
        listeningEndpointFile = endpointFile
        Runtime.getRuntime().addShutdownHook(Thread { endpointFile.delete() })
        // A daemon, so that it is never the thread that keeps a closed application alive.
        thread(isDaemon = true, name = "campfire-single-instance") { serverSocket.serve(token, onActivated) }
    } catch (exception: Exception) {
        // The lock is held all the same, so a later process finds nobody to talk to and starts next to this one.
        println("Could not listen for other instances: ${exception.message}")
        endpointFile.delete()
    }
}

/**
 * The loopback interface is shared by every user of the machine and reachable from any web page a browser has
 * open, so the port alone must not be enough to make Campfire import a path: the token is, and it is in a file
 * only this user can read. On Windows the application data folder is the user's own already.
 */
private fun File.writeForOwnerOnly(text: String) {
    val path = toPath()
    if ("posix" in path.fileSystem.supportedFileAttributeViews()) {
        Files.createFile(path, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
    }
    writeText(text)
}

private fun ServerSocket.serve(token: String, onActivated: (paths: List<String>) -> Unit) {
    val expectedHeader = "$PROTOCOL_HEADER $token".toByteArray()
    while (!isClosed) {
        try {
            accept().use { connection ->
                connection.soTimeout = CONNECTION_TIMEOUT_MILLIS
                // The sender closes its half once it has said everything, so the end of the stream is the end of
                // the request, and the cap is what a stranger gets to make this process hold.
                val lines = connection.getInputStream().readNBytes(MAX_REQUEST_BYTES).decodeToString().lines()
                if (MessageDigest.isEqual(expectedHeader, lines.first().toByteArray())) {
                    onActivated(lines.drop(1).filter(String::isNotEmpty).map { URLDecoder.decode(it, Charsets.UTF_8) })
                    connection.getOutputStream().write("$ACKNOWLEDGEMENT\n".toByteArray())
                }
            }
        } catch (exception: IOException) {
            // A connection that never spoke or left early. The next one is no worse for it.
        } catch (exception: IllegalArgumentException) {
            println("Another instance sent a path that could not be read: ${exception.message}")
        }
    }
}

/** Answers whether the running instance acknowledged [paths]; one attempt, see [claimSingleInstance] for the retries. */
private fun sendPaths(dataDirectory: File, paths: List<String>): Boolean {
    // Encoded so that a path with a line break in it is still one line of the request.
    val encodedPaths = paths.joinToString("\n") { URLEncoder.encode(it, Charsets.UTF_8) }
    return try {
        val (port, token) = File(dataDirectory, ENDPOINT_FILE_NAME).readLines()
        Socket().use { socket ->
            socket.connect(InetSocketAddress(InetAddress.getByName(LOOPBACK_ADDRESS), port.toInt()), CONNECTION_TIMEOUT_MILLIS)
            socket.soTimeout = CONNECTION_TIMEOUT_MILLIS
            socket.getOutputStream().write("$PROTOCOL_HEADER $token\n$encodedPaths".toByteArray())
            socket.shutdownOutput()
            socket.getInputStream().bufferedReader().readLine() == ACKNOWLEDGEMENT
        }
    } catch (exception: Exception) {
        // No file yet, half a file, a closed port, or something on that port that is not Campfire.
        false
    }
}

private const val LOCK_FILE_NAME = "instance.lock"
private const val ENDPOINT_FILE_NAME = "instance.endpoint"
private const val LOOPBACK_ADDRESS = "127.0.0.1"
private const val PROTOCOL_HEADER = "CAMPFIRE 1"
private const val ACKNOWLEDGEMENT = "OK"
private const val BACKLOG = 16
private const val TOKEN_BYTES = 32
private const val MAX_REQUEST_BYTES = 1 shl 20
private const val CONNECTION_TIMEOUT_MILLIS = 2_000
private const val HAND_OVER_ATTEMPTS = 20
private const val HAND_OVER_RETRY_MILLIS = 250L
