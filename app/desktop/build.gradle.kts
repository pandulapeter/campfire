/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.FileSystems
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile
import javax.imageio.ImageIO
import javax.inject.Inject
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.desktop.application.tasks.AbstractJLinkTask

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
}

/**
 * The ANGLE libraries skiko renders through on Windows, kept off the runtime classpath: ProGuard would join them into
 * the jar, from which skiko would unpack them into the user's home on every new version. They are copied next to
 * skiko's own library instead (see `createReleaseDistributable` below).
 */
val angleRuntime: Configuration = configurations.create("angleRuntime")

dependencies {
    implementation(project(":app:di"))
    implementation(project(":data:model"))
    implementation(project(":presentation"))
    implementation(libs.compose.components.resources)
    implementation(compose.desktop.currentOs)
    implementation(libs.jbr.api)
    implementation(libs.koin.compose.viewmodel)
    runtimeOnly(libs.kotlin.coroutines.swing) // Provides Dispatchers.Main for viewModelScope.
    angleRuntime(libs.skiko.angle.windows.x64)
}

val versionName = project.property("campfire.versionName").toString()
group = "com.pandulapeter.campfire"
version = versionName

/**
 * Whether this is the build that goes to the Mac App Store, which is sandboxed and packaged as a signed `.pkg`. The
 * `.pkg` is made for nothing else, so asking for one is asking for the store build; every other Mac build - `run`, an
 * app image or a `.dmg` made by hand - stays an ordinary app, signed ad hoc, that starts on the machine it was made on,
 * which a sandboxed one tied to its App ID does not.
 */
val isMacAppStoreBuild = gradle.startParameter.taskNames.any { it.substringAfterLast(':').endsWith("Pkg") }

/** Empty for an unsigned store build, which on Apple silicon still gets the ad hoc signature it needs to start at all. */
val macSigningIdentity = project.property("campfire.mac.signingIdentity").toString()

/** The runtime every build that is handed out is made with, see [toolchainLauncher]. */
val jetBrainsRuntimeLauncher: Provider<JavaLauncher> = javaToolchains.launcherFor {
    languageVersion = JavaLanguageVersion.of(libs.versions.jvmTarget.get().toInt())
    vendor = JvmVendorSpec.JETBRAINS
}

/** Whether [toolchainLauncher] is a stand-in, with which a debug build works and a release build is never made. */
val isJetBrainsRuntimeMissing = runCatching { jetBrainsRuntimeLauncher.get() }.isFailure

/**
 * The JDK whose runtime image, `jpackage` and `java` the packaging and `run` use. It is the JetBrains Runtime rather
 * than whichever JDK of that version is installed, because only it lets the content be laid out under a title bar
 * that still behaves as one (its custom title bar, see TitleBar.kt) and lets a window follow the app's own light or
 * dark theme on macOS (`apple.awt.windowAppearance`), without which the window buttons disappear into the app's
 * background.
 * Foojay, which Gradle provisions toolchains from, lists no JetBrains Runtime of this version, so Gradle cannot download
 * it: it has to be installed where Gradle looks for JDKs, which the workflows do with `setup-java` and which the IDE's
 * own runtime is when it starts Gradle. Where there is none, any JDK of the version stands in, so that a fresh clone
 * still builds every module - `javaHome` below asks for the toolchain while every project of the build is configured -
 * and only the release builds, which are what is handed out, refuse to be made with it.
 */
val toolchainLauncher: Provider<JavaLauncher> = if (isJetBrainsRuntimeMissing) {
    logger.warn(
        "No JetBrains Runtime ${libs.versions.jvmTarget.get()} was found, so :app:desktop runs on another JDK, without its " +
            "custom title bar, and its release builds cannot be made.",
    )
    javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(libs.versions.jvmTarget.get().toInt()) }
} else {
    jetBrainsRuntimeLauncher
}

/**
 * Every build that leaves the machine is a release one - the `.deb`, the `.msix` and the Mac App Store `.pkg` are all
 * made of `createReleaseDistributable` - so a stand-in runtime stops each of them before it starts, rather than
 * shipping a window that keeps the system's title bar and draws its buttons for the wrong theme.
 */
tasks.matching { it.name.startsWith("createRelease") || it.name.startsWith("packageRelease") || it.name == "runRelease" }.configureEach {
    doFirst {
        if (isJetBrainsRuntimeMissing) {
            throw GradleException("$path is a release build, which has to bundle the JetBrains Runtime, and none was found.")
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.pandulapeter.campfire.CampfireDesktopApplicationKt"
        // The X11 toolkit's app class name is what a Linux desktop matches a window to its launcher by, and it is a
        // private field of a package java.desktop does not export. Only on a Linux host, since jpackage packages for
        // the machine it runs on and an --add-opens naming a package the runtime does not have warns on every start.
        if (isLinuxHost) {
            jvmArgs("--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED")
        }
        // The packages the JDK hands a trackpad pinch and the full screen notifications to only exist on macOS, and
        // are not exported either (see TouchpadMagnification.kt and TitleBar.kt).
        if (isMacHost) {
            jvmArgs(
                "--add-exports=java.desktop/com.apple.eawt=ALL-UNNAMED",
                "--add-exports=java.desktop/com.apple.eawt.event=ALL-UNNAMED",
            )
        }
        buildTypes.release.proguard {
            configurationFiles.from(project.file("proguard-rules.pro"))
            // One jar instead of a hundred. Windows Defender scans every file the app opens again whenever its
            // signatures have been updated since, which happens several times a day, and it takes as long over a jar of
            // thirty kilobytes as over one of twenty megabytes: the first start after an update took six seconds with
            // every dependency in a jar of its own and under two with one jar. Nothing is lost in the merge, as long as
            // no two jars carry a META-INF/services file of the same name - ProGuard keeps the first copy of a duplicate.
            joinOutputJars = true
        }
        // Package with the toolchain JDK rather than the JVM running Gradle, which may lack jpackage.
        javaHome = toolchainLauncher.get().metadata.installationPath.asFile.absolutePath
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Pkg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Campfire"
            packageVersion = versionName
            description = "Your songbook, on every screen you own"
            vendor = "Pandula Péter"
            copyright = "Copyright (c) Pandula Péter 2017-2026"
            // What jdeps finds on top of the modules Compose Desktop includes by itself, plus the two it cannot:
            // `jdk.localedata` is found through ServiceLoader at run time, so no class file names it, and without it
            // Locale.getDisplayLanguage answers in English whatever language it is asked in - the language filter
            // chips and the language picker would read "German" in a Hungarian app. `jdk.accessibility` is loaded by
            // the JDK itself and is what the Java Access Bridge lives in, without which Narrator and NVDA get nothing
            // from the Windows build. The packaged runtime holds the listed modules and nothing else, so one that is
            // missing is a NoClassDefFoundError - or, for these two, a silent wrong answer - that only an installed
            // build can show. `./gradlew :app:desktop:suggestRuntimeModules` prints what jdeps can see.
            modules("java.instrument", "java.management", "jdk.accessibility", "jdk.localedata", "jdk.unsupported")
            macOS {
                iconFile.set(project.file("src/main/resources/appIcon.icns"))
                bundleID = "com.pandulapeter.campfire"
                appCategory = "public.app-category.music"
                // The App Store takes a Mac app built for Apple silicon alone only from macOS 12 on. The bundled JDK and
                // skiko would run on 11, and the plugin's own default is older than either.
                minimumSystemVersion = "12.0"
                packageBuildVersion = project.property("campfire.buildNumber").toString()
                // Not fileAssociation(): the plugin writes its own document type with the "****" OS type, which claims
                // every kind of file, and with no rank or content type. These are the iOS app's document types.
                infoPlist {
                    extraKeysRawXml = macChordProDocumentTypes() + "\n" + nonExemptEncryptionKey()
                }
                if (isMacAppStoreBuild) {
                    // Only the store build is signed. A real signature enforces the hardened runtime, under which the
                    // JVM needs the entitlements that only this build is given; a .dmg built by hand keeps its ad hoc
                    // signature, which does not.
                    if (macSigningIdentity.isNotEmpty()) {
                        signing {
                            sign = true
                            identity = macSigningIdentity
                            project.property("campfire.mac.signingKeychain").toString().takeIf { it.isNotEmpty() }?.let {
                                keychain = it
                            }
                        }
                    }
                    appStore = true
                    entitlementsFile = project.file("app-store.entitlements")
                    runtimeEntitlementsFile = project.file("app-store-runtime.entitlements")
                    project.property("campfire.mac.provisioningProfile").toString().takeIf { it.isNotEmpty() }?.let {
                        provisioningProfile = project.file(it)
                    }
                    project.property("campfire.mac.runtimeProvisioningProfile").toString().takeIf { it.isNotEmpty() }?.let {
                        runtimeProvisioningProfile = project.file(it)
                    }
                }
            }
            windows {
                iconFile.set(project.file("src/main/resources/appIcon.ico"))
                chordProFileAssociations()
                // jpackage creates no shortcut it is not asked for, and an installer asked for none installs an application
                // that is nowhere to be found: these are the Start menu entry and the one on the desktop.
                menu = true
                menuGroup = "Campfire"
                shortcut = true
                // Installs into the user's own profile, so the installer never asks for an administrator.
                perUserInstall = true
                // What makes a newer installer replace the installed version rather than land next to it. Windows
                // Installer only looks for a product with the same upgrade code in the context it is installing into
                // itself, so this and perUserInstall above both have to stay what they are for as long as the app
                // exists: changing either leaves every existing installation where it is, with a second one in Apps.
                // 4.2.2 is that installation - per machine, under the code jpackage derives when it is given none -
                // and 4.2.3 was the change.
                upgradeUuid = "243e51a3-b5a0-49a7-9a61-2c276db59db9"
            }
            linux {
                iconFile.set(project.file("src/main/composeResources/drawable/app_icon.png"))
                // jpackage writes the .desktop entry — and with it the icon — only for a package that asks for a
                // shortcut or declares file associations, and this one declares none: a MIME type is what a Linux
                // association is keyed by, and ChordPro files have none of their own to claim.
                shortcut = true
                menuGroup = "AudioVideo;Audio;Music"
                appCategory = "sound"
                debMaintainer = "pandulapeter@gmail.com"
            }
        }
    }
}

/**
 * Writes the class data sharing archive of the JDK's own classes into the runtime image, which every JDK ships in
 * `lib/server` (`bin/server` on Windows) and the JVM maps at startup instead of loading and verifying those classes one
 * by one. jlink leaves it out of the image the Compose plugin asks for, and its own `--generate-cds-archive` cannot
 * put it back: that runs the image's `java`, which `--strip-native-commands` has removed. So the toolchain's launcher
 * - the image is jlinked from that same JDK - is put into the image for as long as the archive takes to write. A JVM
 * that finds the archive does not match ignores it without a word, so the worst it can do is nothing.
 *
 * The app's own classes are archived on top of it by `recordClassDataArchive`, below.
 */
tasks.withType<AbstractJLinkTask>().configureEach {
    val runtimeImage = destinationDir
    val java = toolchainLauncher.map { it.executablePath.asFile }
    doLast {
        val launcher = runtimeImage.get().asFile.resolve("bin/${java.get().name}")
        java.get().copyTo(launcher, overwrite = true)
        launcher.setExecutable(true)
        try {
            val dump = ProcessBuilder(launcher.absolutePath, "-Xshare:dump").redirectErrorStream(true).start()
            val output = dump.inputStream.bufferedReader().readText()
            if (dump.waitFor() != 0) throw GradleException("Could not write the class data sharing archive:\n$output")
        } finally {
            launcher.delete()
            // copyTo created the folder, which the stripped image does not have, and jpackage refuses a Mac App Store
            // runtime that has one at all, empty or not.
            launcher.parentFile.takeIf { it.list().isNullOrEmpty() }?.delete()
        }
        // The JVM writes it read-only, which on Windows stops the next jlink run from clearing its output directory.
        runtimeImage.get().asFile.walkTopDown().filter { it.extension == "jsa" }.forEach { it.setWritable(true) }
    }
}

/**
 * Puts the ANGLE libraries into the Windows release image's `$APPDIR`, which is the `skiko.library.path` the launcher
 * sets, so `main` finds them there and turns ANGLE on (see `CampfireDesktopApplication.kt`). The packaging only moves
 * skiko's own library and its ICU data out of the jar, and the `.msi` and the `.msix` are both made of this image. A
 * `doLast` of the image's own task, so the libraries are already there when `recordClassDataArchive` trains it.
 */
tasks.matching { isWindowsHost && it.name == "createReleaseDistributable" }.configureEach {
    val libraries = angleRuntime
    val appDirectory = layout.buildDirectory.dir("compose/binaries/main-release/app/Campfire/app")
    inputs.files(libraries)
    doLast {
        val names = setOf("libEGL.dll", "libGLESv2.dll")
        val copied = libraries.files.flatMap { jar ->
            ZipFile(jar).use { zip ->
                zip.entries().asSequence().filter { it.name in names }.map { entry ->
                    zip.getInputStream(entry).use { input -> appDirectory.get().asFile.resolve(entry.name).outputStream().use { input.copyTo(it) } }
                    entry.name
                }.toList()
            }
        }
        if (copied.toSet() != names) throw GradleException("Expected $names in the ANGLE runtime, found $copied.")
    }
}

/**
 * Records a dynamic class data sharing archive of the app's own classes into the release image on Windows and Linux,
 * and points the launcher at it. A start loads about eleven thousand classes, and only a sixth of them come from the
 * JDK's archive above; the rest are read and verified one by one out of the joined jar, which on a small machine is
 * most of the time it takes to show the library. With this archive nearly all of them are mapped instead.
 *
 * The archive is recorded once, here, by a training run of the image (`-XX:ArchiveClassesAtExit`) that the app ends
 * by itself once the demo library is on screen (`campfire.trainingRun` in `TrainingRun.kt`), since a
 * killed JVM writes nothing. It is shipped inside the image, so the package installs and removes it with everything
 * else, and it is recorded again with every image: the JVM checks the jar's size and the base archive it was recorded
 * on, so an archive written for other jars is never used - it prints a warning and starts without it, as it does on
 * any other mismatch, and never fails. The run gets a throwaway home and data directory, so nothing of it lands in the
 * image or in the builder's profile, and it trains the image `createReleaseDistributable` wrote, never `PackageMsix`'s
 * copy, whose launcher names the Store package's data folder.
 *
 * Not on macOS: the only Mac build that ships is the Mac App Store `.pkg`, signed inside `createReleaseDistributable`,
 * so a file added to the bundle afterwards breaks its seal, and the store-signed bundle starts nowhere but TestFlight,
 * so it cannot be trained. Doing it would mean training a copy signed ad hoc and signing the bundle a second time, for
 * the one build that runs on Apple silicon alone, where the start is already under a second.
 */
val recordClassDataArchive = tasks.register<RecordClassDataArchive>("recordClassDataArchive") {
    onlyIf { isWindowsHost || isLinuxHost }
    dependsOn("createReleaseDistributable")
    doNotTrackState("Edits the release app image in place")
    appImage = layout.buildDirectory.dir("compose/binaries/main-release/app/Campfire")
    targetsWindows = isWindowsHost
}
tasks.matching { it.name == "packageReleaseDeb" || it.name == "packageReleaseMsi" || it.name == "packageReleaseMsix" }.configureEach {
    dependsOn(recordClassDataArchive)
}

/**
 * Adds StartupWMClass to the desktop entry jpackage writes, which is the key that ties the running window to the
 * installed launcher: without it GNOME and KDE show the window under its WM_CLASS and a pin creates a second,
 * iconless entry. jpackage would take a .desktop template through --resource-dir, but the Compose plugin fixes that
 * directory, clears it inside its own task action and passes the flag after any freeArgs, so there is no way to hand
 * it one - the key goes into the finished package instead. The value is what `setLinuxWindowClassName` in
 * `:app:desktop`'s entry point sets the toolkit's app class name to; the two have to agree.
 */
val addStartupWmClassToDeb = tasks.register<AddStartupWmClassToDeb>("addStartupWmClassToDeb") {
    onlyIf { isLinuxHost }
    val packages = fileTree(layout.buildDirectory.dir("compose/binaries")) { include("main/deb/*.deb", "main-release/deb/*.deb") }
    this.packages.from(packages)
    windowClassName = "Campfire"
    workingDirectory = layout.buildDirectory.dir("tmp/addStartupWmClassToDeb")
    // The package is rewritten in place, so it is both what the task reads and what it leaves behind: an unchanged
    // package is not unpacked and built again on every run.
    inputs.files(packages)
    outputs.files(packages)
}
tasks.matching { it.name == "packageDeb" || it.name == "packageReleaseDeb" }.configureEach {
    finalizedBy(addStartupWmClassToDeb)
}

/**
 * Puts a ticked "Launch Campfire" checkbox on the last page of the Windows installer, which starts the app when the
 * installer is closed with Finish. jpackage has no option for it and takes WiX sources only through the --resource-dir
 * the Compose plugin owns, so `add-launch-after-install.ps1` adds it to the finished .msi - the script says how. It is
 * a finalizer of the packaging task, so whatever builds the .msi gets it without asking, and a script that cannot find
 * what it edits fails that build rather than letting an installer out without it.
 */
val addLaunchAfterInstallToMsi = tasks.register<AddLaunchAfterInstallToMsi>("addLaunchAfterInstallToMsi") {
    onlyIf { isWindowsHost }
    val packages = fileTree(layout.buildDirectory.dir("compose/binaries")) { include("main/msi/*.msi", "main-release/msi/*.msi") }
    this.packages.from(packages)
    script = project.file("add-launch-after-install.ps1")
    launcherName = "Campfire.exe"
    checkboxText = "Launch Campfire"
    // As with the .deb, the package is rewritten in place, so it is both what the task reads and what it leaves behind.
    inputs.files(packages)
    outputs.files(packages)
}
tasks.matching { it.name == "packageMsi" || it.name == "packageReleaseMsi" }.configureEach {
    finalizedBy(addLaunchAfterInstallToMsi)
}

/**
 * Builds the `.msix` the Microsoft Store is given. The Compose plugin makes no such package, but the release app image
 * is already everything one holds apart from `AppxManifest.xml` and its logos, so the image is packed as it is with the
 * Windows SDK's makeappx. The package is left unsigned: Partner Center signs what it certifies, and a package is only
 * installed outside the Store once somebody signs it with a certificate the machine trusts.
 */
tasks.register<PackageMsix>("packageReleaseMsix") {
    dependsOn("createReleaseDistributable")
    appImage = layout.buildDirectory.dir("compose/binaries/main-release/app/Campfire")
    manifest = project.file("AppxManifest.xml")
    icon = project.file("src/main/composeResources/drawable/app_icon.png")
    displayName = project.property("campfire.windows.displayName").toString()
    identityName = project.property("campfire.windows.identityName").toString()
    publisher = project.property("campfire.windows.publisher").toString()
    publisherDisplayName = project.property("campfire.windows.publisherDisplayName").toString()
    // A package version has four parts, and the Store keeps the last one for itself.
    packageVersion = (versionName.split('.') + listOf("0", "0")).take(3).joinToString(".") + ".0"
    sdkBinDirectory = project.property("campfire.windows.sdkBinDirectory").toString()
    outputFile = layout.buildDirectory.file("compose/binaries/main-release/msix/Campfire-$versionName.msix")
}

/**
 * Clears the extended attributes of the provisioning profiles before they are copied into the bundle. A profile is
 * downloaded through a browser, which marks it with `com.apple.quarantine`, the copy keeps the mark, and App Store
 * Connect refuses a build with a quarantined file anywhere in it - after the upload, by mail.
 */
tasks.matching { isMacAppStoreBuild && (it.name == "createDistributable" || it.name == "createReleaseDistributable") }.configureEach {
    val profiles = listOf("campfire.mac.provisioningProfile", "campfire.mac.runtimeProvisioningProfile")
        .map { project.property(it).toString() }
        .filter { it.isNotEmpty() }
        .map { project.file(it) }
    doFirst {
        profiles.filter { it.isFile }.forEach { profile ->
            val xattr = ProcessBuilder("xattr", "-c", profile.absolutePath).redirectErrorStream(true).start()
            val output = xattr.inputStream.bufferedReader().readText()
            if (xattr.waitFor() != 0) throw GradleException("Could not clear the attributes of ${profile.name}:\n$output")
        }
    }
}

/**
 * Takes the skiko library of the other kind of Mac out of the jar ProGuard joins everything into. The macOS skiko
 * runtime jar carries the libraries of both processors; the packaging moves the one for the machine it runs on into
 * the bundle and signs it there, and would leave the other one in the jar, unsigned. It is never loaded, but it is
 * twenty megabytes of every download, and a native binary without a signature inside a jar is exactly what the Mac
 * App Store's validation and notarization look for.
 */
tasks.matching { it.name == "proguardReleaseJars" }.configureEach {
    val proguardOutput = layout.buildDirectory.dir("compose/tmp/main-release/proguard")
    doLast {
        if (!System.getProperty("os.name").orEmpty().lowercase().contains("mac")) return@doLast
        val otherArchitecture = if (System.getProperty("os.arch") == "aarch64") "x64" else "arm64"
        val unused = listOf("libskiko-macos-$otherArchitecture.dylib", "libskiko-macos-$otherArchitecture.dylib.sha256")
        proguardOutput.get().asFile.listFiles { file -> file.extension == "jar" }.orEmpty().forEach { jar ->
            FileSystems.newFileSystem(jar.toPath()).use { zip ->
                unused.map { zip.getPath(it) }.forEach { Files.deleteIfExists(it) }
            }
        }
    }
}

compose.resources {
    publicResClass = false
    packageOfResClass = "com.pandulapeter.campfire.resources"
    generateResClass = always
}

kotlin {
    jvmToolchain(libs.versions.jvmTarget.get().toInt())
}

/** Whether this build runs on Linux, which is the only host jpackage builds the .deb on. */
val isLinuxHost get() = System.getProperty("os.name").orEmpty().lowercase().contains("linux")

/** Whether this build runs on macOS, the only host whose runtime has the trackpad gesture package in it. */
val isMacHost get() = System.getProperty("os.name").orEmpty().lowercase().contains("mac")

/** Whether this build runs on Windows, which is the only host jpackage builds the .msi on. */
val isWindowsHost get() = System.getProperty("os.name").orEmpty().lowercase().contains("windows")

/**
 * Starts the release image once with `-XX:ArchiveClassesAtExit` and adds the archive it writes to the launcher's
 * configuration. The process is started with `ProcessBuilder` rather than `ExecOperations`, which has no timeout, and
 * its output goes to a file, so that a chatty run never blocks on a full pipe.
 */
abstract class RecordClassDataArchive : DefaultTask() {

    @get:Internal
    abstract val appImage: DirectoryProperty

    @get:Input
    abstract val targetsWindows: Property<Boolean>

    @TaskAction
    fun record() {
        val image = appImage.get().asFile
        val launcher = if (targetsWindows.get()) image.resolve("Campfire.exe") else image.resolve("bin/Campfire")
        val appDirectory = if (targetsWindows.get()) image.resolve("app") else image.resolve("lib/app")
        val configuration = appDirectory.resolve("Campfire.cfg")
        val archive = appDirectory.resolve("campfire.jsa")
        if (!launcher.isFile || !configuration.isFile) throw GradleException("There is no release app image in $image to train.")
        // JBR 21's AWT speaks X11 alone, so without a display the run would only wait for its timeout.
        if (!targetsWindows.get() && System.getenv("DISPLAY").isNullOrEmpty()) {
            throw GradleException("Recording the class data sharing archive starts the app, which needs a display: run the packaging under xvfb-run.")
        }
        // The image may be up to date from an earlier run, and a JVM given a dynamic archive cannot record another on
        // top of it. A run that failed after the dump leaves the archive read-only, which on Windows refuses the delete.
        val lines = configuration.readLines().filterNot { it.startsWith(ARCHIVE_OPTION) }
        configuration.writeText(lines.joinToString(System.lineSeparator(), postfix = System.lineSeparator()))
        if (archive.exists()) {
            archive.setWritable(true)
            if (!archive.delete()) throw GradleException("Could not delete the previous $archive.")
        }
        val home = temporaryDir.resolve("home")
        home.deleteRecursively()
        home.mkdirs()
        val output = temporaryDir.resolve("training.log")
        try {
            val process = ProcessBuilder(launcher.absolutePath).apply {
                val environment = environment()
                // Quoted, which HotSpot's reading of the variable honours, so a checkout under a folder with a space in
                // its name still trains.
                environment["JAVA_TOOL_OPTIONS"] =
                    "-XX:ArchiveClassesAtExit=\"${archive.absolutePath}\" -Dcampfire.trainingRun=true -Duser.home=\"${home.absolutePath}\""
                if (targetsWindows.get()) {
                    // The data directory comes from %APPDATA% on Windows, not from user.home.
                    environment["APPDATA"] = home.resolve("AppData/Roaming").absolutePath
                    environment["LOCALAPPDATA"] = home.resolve("AppData/Local").absolutePath
                } else {
                    environment.remove("XDG_DATA_HOME")
                }
                redirectErrorStream(true)
                redirectOutput(output)
            }.start()
            if (!process.waitFor(3, TimeUnit.MINUTES)) {
                process.destroyForcibly()
                throw GradleException("The training run did not end in three minutes:\n${output.readTextOrEmpty()}")
            }
            if (process.exitValue() != 0) {
                throw GradleException("The training run exited with ${process.exitValue()}:\n${output.readTextOrEmpty()}")
            }
            // The dump also prints a few dozen warnings about JFR and proxy classes it skips, which are expected.
            if (!archive.isFile || archive.length() < 10L * 1024 * 1024) {
                throw GradleException("The training run wrote no class data sharing archive:\n${output.readTextOrEmpty()}")
            }
        } finally {
            home.deleteRecursively()
        }
        // The JVM writes it read-only, which on Windows stops the next jlink or jpackage run from clearing the image.
        archive.setWritable(true)
        val section = lines.indexOf("[JavaOptions]")
        if (section < 0) throw GradleException("There is no [JavaOptions] section in $configuration to add the archive to.")
        val option = "$ARCHIVE_OPTION\$APPDIR/${archive.name}"
        configuration.writeText((lines.take(section + 1) + option + lines.drop(section + 1)).joinToString(System.lineSeparator(), postfix = System.lineSeparator()))
        logger.lifecycle("Recorded ${archive.length() / (1024 * 1024)} MB of class data sharing archive into $archive")
    }

    private fun File.readTextOrEmpty() = takeIf { it.isFile }?.readText().orEmpty()

    private companion object {
        const val ARCHIVE_OPTION = "java-options=-XX:SharedArchiveFile="
    }
}

/** Runs `add-launch-after-install.ps1` over each .msi, which fails the build rather than leave an installer without it. */
abstract class AddLaunchAfterInstallToMsi : DefaultTask() {

    @get:Inject
    abstract val execOperations: ExecOperations

    @get:Internal
    abstract val packages: ConfigurableFileCollection

    @get:InputFile
    abstract val script: RegularFileProperty

    @get:Input
    abstract val launcherName: Property<String>

    @get:Input
    abstract val checkboxText: Property<String>

    @TaskAction
    fun addLaunchAfterInstall() {
        val msis = packages.files.filter { it.isFile }
        if (msis.isEmpty()) throw GradleException("There is no .msi to add the launch checkbox to.")
        msis.forEach { msi ->
            execOperations.exec {
                commandLine(
                    "powershell", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                    "-File", script.get().asFile.absolutePath,
                    "-Path", msi.absolutePath,
                    "-Launcher", launcherName.get(),
                    "-Text", checkboxText.get(),
                )
            }
        }
    }
}

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
            .replace("@DISPLAY_NAME@", displayName.get().escapedForXml())
            .replace("@IDENTITY_NAME@", identityName.get().escapedForXml())
            .replace("@PUBLISHER@", publisher.get().escapedForXml())
            .replace("@PUBLISHER_DISPLAY_NAME@", publisherDisplayName.get().escapedForXml())
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
        val section = lines.indexOf("[JavaOptions]")
        if (section < 0) throw GradleException("There is no [JavaOptions] section in $configuration to add the package family name to.")
        val option = "java-options=-Dcampfire.packageFamilyName=${identityName.get()}_${publisherId(publisher.get())}"
        configuration.writeText((lines.take(section + 1) + option + lines.drop(section + 1)).joinToString(System.lineSeparator(), postfix = System.lineSeparator()))
    }

    /**
     * The half of the package family name Windows derives from the publisher: the first eight bytes of the SHA-256 of
     * its UTF-16LE text, as thirteen characters of Crockford's base32. Partner Center shows the result on the Product
     * identity page, as "Package/Identity/PublisherId" and as the end of the package family name.
     */
    private fun publisherId(publisher: String): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(publisher.toByteArray(Charsets.UTF_16LE)).take(8)
        val bits = hash.joinToString("") { (it.toInt() and 0xFF).toString(2).padStart(8, '0') } + "0"
        return bits.chunked(5).map { "0123456789abcdefghjkmnpqrstvwxyz"[it.toInt(2)] }.joinToString("")
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
            .maxByOrNull { directory -> directory.parentFile.name.split('.').map { it.toIntOrNull() ?: 0 }.fold(0L) { total, part -> total * 100_000 + part } }
            ?: kits
    }

    private fun String.escapedForXml() = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}

/**
 * Unpacks each .deb, adds the StartupWMClass key to its one desktop entry and builds it again. It fails rather than
 * doing nothing when there is no entry to add the key to, since a package without it is wrong in a way only an
 * installation shows. `fakeroot` keeps the repacked files owned by root, as they were; the entry's line in the
 * package's md5sums is brought up to date with it, so that a check of the installed files does not report it.
 */
abstract class AddStartupWmClassToDeb : DefaultTask() {

    @get:Inject
    abstract val execOperations: ExecOperations

    @get:Internal
    abstract val packages: ConfigurableFileCollection

    @get:Input
    abstract val windowClassName: Property<String>

    @get:Internal
    abstract val workingDirectory: DirectoryProperty

    @TaskAction
    fun addStartupWmClass() {
        val debs = packages.files.filter { it.isFile }
        if (debs.isEmpty()) throw GradleException("There is no .deb to add StartupWMClass to.")
        debs.forEach { deb ->
            val extracted = workingDirectory.get().asFile.resolve(deb.nameWithoutExtension)
            extracted.deleteRecursively()
            // dpkg-deb creates the extraction target itself, but not its parent, which a clean checkout does not have yet.
            workingDirectory.get().asFile.mkdirs()
            execOperations.exec { commandLine("dpkg-deb", "--raw-extract", deb.absolutePath, extracted.absolutePath) }
            val entries = extracted.walkTopDown()
                .filter { it.isFile && it.extension == "desktop" && !it.relativeTo(extracted).startsWith("DEBIAN") }
                .toList()
            val entry = entries.singleOrNull()
                ?: throw GradleException("Expected one desktop entry in ${deb.name}, found ${entries.size}.")
            val lines = entry.readLines().filterNot { it.startsWith("StartupWMClass=") }
            entry.writeText((lines + "StartupWMClass=${windowClassName.get()}").joinToString(separator = "\n", postfix = "\n"))
            val md5sums = extracted.resolve("DEBIAN/md5sums")
            if (md5sums.isFile) {
                val entryPath = entry.relativeTo(extracted).invariantSeparatorsPath
                val entryHash = MessageDigest.getInstance("MD5").digest(entry.readBytes()).joinToString("") { "%02x".format(it) }
                md5sums.writeText(
                    md5sums.readLines().joinToString(separator = "\n", postfix = "\n") { line ->
                        if (line.substringAfter("  ") == entryPath) "$entryHash  $entryPath" else line
                    }
                )
            }
            execOperations.exec { commandLine("fakeroot", "dpkg-deb", "--build", extracted.absolutePath, deb.absolutePath) }
            extracted.deleteRecursively()
        }
    }
}

/**
 * The ChordPro extensions the Windows installer claims: the ones no other application uses. An MSI can only make an
 * application an extension's handler outright - there is no Alternate rank to register the shared ones with, the
 * way iOS and macOS do - so ".crd", ".chord" and ".pro" (Qt's project files among others) are left to whoever has
 * them, and are imported from inside the app. They are all registered as plain text, which is what they are; zip and
 * .txt are deliberately not here, since an app that claims those would be answering for every archive and note on
 * the machine.
 *
 * A subset of `LibraryFiles.SONG_EXTENSIONS` in `:data:model`, which the build file cannot see.
 */
fun org.jetbrains.compose.desktop.application.dsl.AbstractPlatformSettings.chordProFileAssociations() =
    listOf("cho", "chopro", "chordpro").forEach { extension ->
        fileAssociation(mimeType = "text/plain", extension = extension, description = "ChordPro song")
    }

/**
 * Tells App Store Connect that the app's only cryptography is the system's HTTPS and hashing, which is exempt, so that
 * no upload stops to ask the export compliance questions. The iOS app's `Info.plist` gives the same answer; anything
 * that adds encryption of its own has to change both.
 */
fun nonExemptEncryptionKey() = """
    <key>ITSAppUsesNonExemptEncryption</key>
    <false/>
""".trimIndent()

/**
 * The macOS document types, the same as the iOS app's (`app/ios/iosApp/iosApp/Info.plist`): ".cho" claimed as the
 * default, the rest of the family as an alternate, since those extensions are shared with other kinds of file, all
 * of them imported as types that conform to plain text. Kept in step with `LibraryFiles.SONG_EXTENSIONS` in
 * `:data:model`, which the build file cannot see.
 */
fun macChordProDocumentTypes() = """
    <key>UTImportedTypeDeclarations</key>
    <array>
        <dict>
            <key>UTTypeIdentifier</key>
            <string>org.chordpro.cho</string>
            <key>UTTypeDescription</key>
            <string>ChordPro song</string>
            <key>UTTypeConformsTo</key>
            <array>
                <string>public.plain-text</string>
            </array>
            <key>UTTypeTagSpecification</key>
            <dict>
                <key>public.filename-extension</key>
                <array>
                    <string>cho</string>
                </array>
            </dict>
        </dict>
        <dict>
            <key>UTTypeIdentifier</key>
            <string>org.chordpro.chordpro</string>
            <key>UTTypeDescription</key>
            <string>ChordPro song</string>
            <key>UTTypeConformsTo</key>
            <array>
                <string>public.plain-text</string>
            </array>
            <key>UTTypeTagSpecification</key>
            <dict>
                <key>public.filename-extension</key>
                <array>
                    <string>chopro</string>
                    <string>chordpro</string>
                    <string>crd</string>
                    <string>chord</string>
                    <string>pro</string>
                </array>
            </dict>
        </dict>
    </array>
    <key>CFBundleDocumentTypes</key>
    <array>
        <dict>
            <key>CFBundleTypeName</key>
            <string>ChordPro song</string>
            <key>CFBundleTypeRole</key>
            <string>Editor</string>
            <key>CFBundleTypeIconFile</key>
            <string>Campfire.icns</string>
            <key>LSHandlerRank</key>
            <string>Default</string>
            <key>LSItemContentTypes</key>
            <array>
                <string>org.chordpro.cho</string>
            </array>
        </dict>
        <dict>
            <key>CFBundleTypeName</key>
            <string>ChordPro song</string>
            <key>CFBundleTypeRole</key>
            <string>Editor</string>
            <key>CFBundleTypeIconFile</key>
            <string>Campfire.icns</string>
            <key>LSHandlerRank</key>
            <string>Alternate</string>
            <key>LSItemContentTypes</key>
            <array>
                <string>org.chordpro.chordpro</string>
            </array>
        </dict>
    </array>
""".trimIndent()
