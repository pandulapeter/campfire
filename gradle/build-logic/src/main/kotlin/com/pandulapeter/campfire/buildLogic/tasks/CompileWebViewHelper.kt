/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.buildLogic.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.zip.ZipFile
import javax.inject.Inject

/**
 * Compiles the desktop's web view helper (`campfirewebview`, the JNI library `NativeWebView.kt` in
 * `:data:source:remote:implementation` loads) for the machine the build runs on, as jpackage packages for it alone:
 * Objective-C over WebKit with the Command Line Tools' clang on macOS, C++ over WebView2 with MSVC on Windows (its
 * loader linked in statically from the pinned WebView2 SDK, fetched from NuGet and checked against its SHA-256), and C on
 * Linux, which reaches WebKitGTK through `dlopen` and so needs nothing but a C compiler.
 *
 * Where the host has no such compiler the task writes nothing and says so: a fresh clone still builds and runs every
 * platform, and that build only cannot read pages. A compiler that is there and fails fails the build, and so does a
 * missing one where [isRequired] (the workflows), so that no release goes out without the helper.
 */
abstract class CompileWebViewHelper : DefaultTask() {

    @get:Inject
    abstract val execOperations: ExecOperations

    /** The `native/` folder, with a `macos`, `windows`, and `linux` folder in it. */
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: DirectoryProperty

    /** The JDK whose `include` folder has the JNI headers: the one the app is packaged with. */
    @get:Input
    abstract val javaHome: Property<String>

    /** `macos`, `windows`, or `linux`, and the host's `os.arch`, which decide what is built. */
    @get:Input
    abstract val hostSystem: Property<String>

    @get:Input
    abstract val hostArchitecture: Property<String>

    @get:Input
    abstract val isRequired: Property<Boolean>

    @get:Input
    abstract val webView2Version: Property<String>

    @get:Input
    abstract val webView2Sha256: Property<String>

    /** Where the WebView2 SDK is downloaded to and unpacked, kept between builds. */
    @get:Internal
    abstract val downloadDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun compile() {
        val output = outputDirectory.get().asFile
        output.deleteRecursively()
        output.mkdirs()
        val include = File(javaHome.get(), "include")
        val source = sources.get().asFile
        when (hostSystem.get()) {
            "macos" -> compileForMac(source.resolve("macos/CampfireWebView.m"), include, output.resolve("libcampfirewebview.dylib"))
            "linux" -> compileForLinux(source.resolve("linux/CampfireWebView.c"), include, output.resolve("libcampfirewebview.so"))
            "windows" -> compileForWindows(source.resolve("windows/CampfireWebView.cpp"), include, output.resolve("campfirewebview.dll"))
            else -> skip("no web view helper is made for ${hostSystem.get()}")
        }
    }

    private fun compileForMac(source: File, include: File, library: File) {
        if (!succeeds("xcrun", "--find", "clang")) return skip("the Xcode Command Line Tools are not installed")
        val architecture = if (hostArchitecture.get() in setOf("aarch64", "arm64")) "arm64" else "x86_64"
        run(
            "xcrun", "clang", "-fobjc-arc", "-dynamiclib", "-Os", "-Wall", "-Werror",
            // The app's own minimum (minimumSystemVersion in :app:desktop).
            "-mmacosx-version-min=12.0", "-arch", architecture,
            "-I${include.path}", "-I${include.resolve("darwin").path}",
            "-framework", "Foundation", "-framework", "WebKit",
            "-Wl,-S,-x", "-o", library.path, source.path,
        )
    }

    private fun compileForLinux(source: File, include: File, library: File) {
        if (!succeeds("cc", "--version")) return skip("there is no C compiler (cc)")
        run(
            "cc", "-shared", "-fPIC", "-Os", "-Wall", "-Werror", "-s",
            "-I${include.path}", "-I${include.resolve("linux").path}",
            "-o", library.path, source.path, "-ldl", "-lpthread",
        )
    }

    private fun compileForWindows(source: File, include: File, library: File) {
        if (hostArchitecture.get() !in setOf("amd64", "x86_64")) return skip("only x64 Windows gets a web view helper")
        val vcvars = visualStudioEnvironmentScript() ?: return skip("Visual Studio's C++ tools (MSVC) are not installed")
        val sdk = webView2Sdk()
        val work = temporaryDir.resolve("windows").apply {
            deleteRecursively()
            mkdirs()
        }
        // cl needs the environment vcvars64.bat sets up, which only lives as long as the cmd that called it.
        val script = work.resolve("compile.cmd")
        script.writeText(
            listOf(
                "@echo off",
                "call \"${vcvars.path}\" >nul || exit /b 1",
                // The objects land in the working folder, never next to the sources.
                "cd /d \"${work.path}\"",
                listOf(
                    "cl", "/nologo", "/O1", "/MT", "/EHsc", "/std:c++17", "/W3", "/WX", "/LD",
                    "/I\"${include.path}\"", "/I\"${include.resolve("win32").path}\"", "/I\"${sdk.resolve("include").path}\"",
                    "\"${source.path}\"", "/Fe\"${library.path}\"",
                    "/link", "\"${sdk.resolve("WebView2LoaderStatic.lib").path}\"",
                    "ole32.lib", "oleaut32.lib", "user32.lib", "advapi32.lib", "shell32.lib", "version.lib",
                ).joinToString(" "),
            ).joinToString("\r\n", postfix = "\r\n"),
        )
        run("cmd", "/c", script.path)
        // The linker leaves an import library and an export file beside the DLL, which nothing loads.
        library.parentFile.listFiles { file -> file.extension in setOf("lib", "exp") }.orEmpty().forEach { it.delete() }
    }

    /** vcvars64.bat of the newest Visual Studio (Build Tools included) that has the x64 C++ tools, found by vswhere. */
    private fun visualStudioEnvironmentScript(): File? {
        val programFiles = System.getenv("ProgramFiles(x86)") ?: return null
        val vswhere = File(programFiles, "Microsoft Visual Studio/Installer/vswhere.exe").takeIf { it.isFile } ?: return null
        val path = output(
            vswhere.path, "-latest", "-products", "*", "-requires", "Microsoft.VisualStudio.Component.VC.Tools.x86.x64",
            "-find", "VC\\Auxiliary\\Build\\vcvars64.bat",
        )?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim()
        return path?.let(::File)?.takeIf { it.isFile }
    }

    /**
     * The WebView2 SDK's headers and static loader for x64, from the NuGet package of the pinned version, downloaded
     * once and refused unless it is the exact package its SHA-256 names.
     */
    private fun webView2Sdk(): File {
        val folder = downloadDirectory.get().asFile.resolve("webview2-${webView2Version.get()}")
        val unpacked = folder.resolve("sdk")
        if (unpacked.resolve("WebView2LoaderStatic.lib").isFile) return unpacked
        folder.mkdirs()
        val nupkg = folder.resolve("microsoft.web.webview2.${webView2Version.get()}.nupkg")
        URI("https://www.nuget.org/api/v2/package/Microsoft.Web.WebView2/${webView2Version.get()}").toURL().openStream().use { input ->
            nupkg.outputStream().use { input.copyTo(it) }
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(nupkg.readBytes()).joinToString("") { "%02x".format(it) }
        if (digest != webView2Sha256.get().lowercase()) {
            nupkg.delete()
            throw GradleException("The WebView2 SDK ${webView2Version.get()} downloaded from NuGet has the SHA-256 $digest, not ${webView2Sha256.get()}.")
        }
        unpacked.resolve("include").mkdirs()
        ZipFile(nupkg).use { zip ->
            mapOf(
                "build/native/include/WebView2.h" to "include/WebView2.h",
                "build/native/include/WebView2EnvironmentOptions.h" to "include/WebView2EnvironmentOptions.h",
                "build/native/x64/WebView2LoaderStatic.lib" to "WebView2LoaderStatic.lib",
            ).forEach { (entry, target) ->
                val found = zip.getEntry(entry) ?: throw GradleException("The WebView2 SDK has no $entry.")
                zip.getInputStream(found).use { input -> unpacked.resolve(target).outputStream().use { input.copyTo(it) } }
            }
        }
        nupkg.delete()
        return unpacked
    }

    private fun skip(reason: String) {
        val message = "No web view helper is built, so this build cannot read pages: $reason."
        if (isRequired.get()) throw GradleException(message)
        logger.warn(message)
    }

    private fun succeeds(vararg command: String) = output(*command) != null

    /** What [command] prints, or null where it cannot be started or fails. */
    private fun output(vararg command: String): String? = try {
        val stream = ByteArrayOutputStream()
        val result = execOperations.exec {
            commandLine(*command)
            standardOutput = stream
            errorOutput = ByteArrayOutputStream()
            isIgnoreExitValue = true
        }
        if (result.exitValue == 0) stream.toString() else null
    } catch (_: Exception) {
        null
    }

    private fun run(vararg command: String) {
        execOperations.exec { commandLine(*command) }
    }
}
