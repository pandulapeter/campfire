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
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import javax.inject.Inject

/**
 * Copies the app image, writes the manifest and the logos next to it, indexes the logos with makepri and packs the
 * whole with makeappx. The logos are scaled from the app icon on every run rather than committed, so they cannot fall
 * out of step with it. The launcher of the copy is also told the package family name, which is what makes the app keep
 * its library in the package's own folder rather than in AppData (`desktopDataDirectory()` in `:presentation`).
 */
abstract class PackageMsix : DefaultTask() {

    @get:Inject
    abstract val execOperations: ExecOperations

    @get:InputDirectory
    abstract val appImage: DirectoryProperty

    @get:InputFile
    abstract val manifest: RegularFileProperty

    @get:InputFile
    abstract val icon: RegularFileProperty

    @get:Input
    abstract val displayName: Property<String>

    @get:Input
    abstract val identityName: Property<String>

    @get:Input
    abstract val publisher: Property<String>

    @get:Input
    abstract val publisherDisplayName: Property<String>

    @get:Input
    abstract val packageVersion: Property<String>

    @get:Input
    abstract val sdkBinDirectory: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun packageMsix() {
        val sdk = sdkBinDirectory.get().takeIf { it.isNotEmpty() }?.let(::File) ?: newestWindowsSdkBinDirectory()
        val makeAppx = sdk.resolve("makeappx.exe").takeIf { it.isFile }
        val makePri = sdk.resolve("makepri.exe").takeIf { it.isFile }
        if (makeAppx == null || makePri == null) {
            throw GradleException(
                "makeappx.exe and makepri.exe are not in $sdk. Install the Windows SDK, or point campfire.windows.sdkBinDirectory " +
                    "at the bin/<version>/x64 folder of the Microsoft.Windows.SDK.BuildTools NuGet package.",
            )
        }
        val root = temporaryDir.resolve("package")
        root.deleteRecursively()
        appImage.get().asFile.copyRecursively(root)
        addPackageFamilyNameToLauncher(root.resolve("app/Campfire.cfg"))
        // The logos are indexed on their own: makepri indexes every file under the folder it is given, and the app
        // image is a few hundred of them that are not resources of any kind.
        val resources = temporaryDir.resolve("resources")
        resources.deleteRecursively()
        writeLogos(resources.resolve("assets"))
        val manifestText = manifest.get().asFile.readText()
            .replace("@DISPLAY_NAME@", xmlEscaped(displayName.get()))
            .replace("@IDENTITY_NAME@", xmlEscaped(identityName.get()))
            .replace("@PUBLISHER@", xmlEscaped(publisher.get()))
            .replace("@PUBLISHER_DISPLAY_NAME@", xmlEscaped(publisherDisplayName.get()))
            .replace("@VERSION@", packageVersion.get())
        resources.resolve("AppxManifest.xml").writeText(manifestText)
        val priConfig = temporaryDir.resolve("priconfig.xml")
        execOperations.exec { commandLine(makePri, "createconfig", "/cf", priConfig, "/dq", "en-US", "/o") }
        execOperations.exec {
            commandLine(makePri, "new", "/pr", resources, "/cf", priConfig, "/mn", resources.resolve("AppxManifest.xml"), "/of", root.resolve("resources.pri"), "/o")
        }
        resources.copyRecursively(root, overwrite = true)
        val output = outputFile.get().asFile
        output.parentFile.mkdirs()
        // makeappx names every file of the app image as it packs it, which is only worth reading when it fails.
        val log = ByteArrayOutputStream()
        val result = execOperations.exec {
            commandLine(makeAppx, "pack", "/d", root, "/p", output, "/o")
            standardOutput = log
            errorOutput = log
            isIgnoreExitValue = true
        }
        if (result.exitValue != 0) throw GradleException("makeappx could not pack ${output.name}:\n$log")
        logger.lifecycle("Packed $output")
    }

    /**
     * Adds the package family name to the Java options of the jpackage launcher's configuration, as the system property
     * the app reads it from. Nothing in the process can ask Windows for it without native code, and it names the
     * folder, `%LOCALAPPDATA%\Packages\<family name>`, that Windows keeps for the package's data and leaves out of the
     * AppData virtualization.
     */
    private fun addPackageFamilyNameToLauncher(configuration: File) {
        val lines = configuration.takeIf { it.isFile }?.readLines().orEmpty()
        val option = "java-options=-Dcampfire.packageFamilyName=${identityName.get()}_${msixPublisherId(publisher.get())}"
        val withFamilyName = withJavaOption(lines, option) {
            "There is no [JavaOptions] section in $configuration to add the package family name to."
        }
        configuration.writeText(withFamilyName.joinToString(System.lineSeparator(), postfix = System.lineSeparator()))
    }

    /**
     * The logos the manifest names, in the sizes Windows picks from. The taskbar, the Start menu's list and the title
     * bar ask for a target size, and without the unplated variants they would draw the icon shrunk onto a coloured
     * tile. The 50 pixel one is the logo of the package, which Partner Center shows on the Store's pages.
     */
    private fun writeLogos(directory: File) {
        directory.mkdirs()
        val source = ImageIO.read(icon.get().asFile)
        val logos = buildMap {
            put("Square44x44Logo.scale-100.png", 44)
            put("Square44x44Logo.scale-200.png", 88)
            listOf(16, 24, 32, 48, 256).forEach { size ->
                put("Square44x44Logo.targetsize-$size.png", size)
                put("Square44x44Logo.targetsize-${size}_altform-unplated.png", size)
            }
            put("Square150x150Logo.scale-100.png", 150)
            put("Square150x150Logo.scale-200.png", 300)
            put("StoreLogo.scale-100.png", 50)
            put("StoreLogo.scale-200.png", 100)
        }
        logos.forEach { (name, size) -> ImageIO.write(source.scaledTo(size), "png", directory.resolve(name)) }
    }

    /** Halves the image until one more halving would pass the target: a single bilinear step from 512 to 16 pixels skips most of them and aliases. */
    private fun BufferedImage.scaledTo(size: Int): BufferedImage {
        var image = this
        while (image.width != size) {
            val next = maxOf(size, image.width / 2)
            image = BufferedImage(next, next, BufferedImage.TYPE_INT_ARGB).also { scaled ->
                val graphics = scaled.createGraphics()
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                graphics.drawImage(image, 0, 0, next, next, null)
                graphics.dispose()
            }
        }
        return image
    }

    private fun newestWindowsSdkBinDirectory(): File {
        val kits = File(System.getenv("ProgramFiles(x86)").orEmpty(), "Windows Kits/10/bin")
        return kits.listFiles { file -> file.isDirectory && file.name.startsWith("10.") }.orEmpty()
            .map { it.resolve("x64") }
            .filter { it.resolve("makeappx.exe").isFile }
            .maxByOrNull { directory -> newestSdkVersionKey(directory.parentFile.name) }
            ?: kits
    }
}
