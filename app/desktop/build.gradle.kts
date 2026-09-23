/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
import java.nio.file.FileSystems
import java.nio.file.Files
import java.security.MessageDigest
import javax.inject.Inject
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.desktop.application.tasks.AbstractJLinkTask

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
}

dependencies {
    implementation(project(":app:di"))
    implementation(project(":data:model"))
    implementation(project(":presentation"))
    implementation(libs.compose.components.resources)
    implementation(compose.desktop.currentOs)
    implementation(libs.koin.compose.viewmodel)
    runtimeOnly(libs.kotlin.coroutines.swing) // Provides Dispatchers.Main for viewModelScope.
}

val versionName = project.property("campfire.versionName").toString()
group = "com.pandulapeter.campfire"
version = versionName

/** Whether this is the build that goes to the Mac App Store, which is sandboxed and packaged as a signed `.pkg`. */
val isMacAppStoreBuild = project.property("campfire.desktop.distribution").toString() == "mac-app-store"

/** Empty for an unsigned store build, which on Apple silicon still gets the ad hoc signature it needs to start at all. */
val macSigningIdentity = project.property("campfire.mac.signingIdentity").toString()

/** The JDK the app is compiled with, and the one whose runtime image, `jpackage` and `java` the packaging uses. */
val toolchainLauncher = javaToolchains.launcherFor {
    languageVersion = JavaLanguageVersion.of(libs.versions.jvmTarget.get().toInt())
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
        // The window's minimum size reaches Windows unscaled through a private setter of the peer, which these two
        // packages hold; see scaleNativeMinimumSize.
        if (isWindowsHost) {
            jvmArgs("--add-opens=java.desktop/java.awt=ALL-UNNAMED", "--add-opens=java.desktop/sun.awt.windows=ALL-UNNAMED")
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
                packageBuildVersion = project.property("campfire.mac.buildNumber").toString()
                // Not fileAssociation(): the plugin writes its own document type with the "****" OS type, which claims
                // every kind of file, and with no rank or content type. These are the iOS app's document types.
                infoPlist {
                    extraKeysRawXml = macChordProDocumentTypes() + "\n" + nonExemptEncryptionKey()
                }
                if (isMacAppStoreBuild) {
                    // Only the store build is signed. A real signature enforces the hardened runtime, under which the
                    // JVM needs the entitlements that only this build is given; the .dmg keeps its ad hoc signature,
                    // which does not, until it gets entitlements and notarization of its own.
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
 * The app's own classes are not archived. Only a JVM that runs the app can write that archive
 * (`-XX:+AutoCreateSharedArchive`), and on JDK 21 the start that records it takes about six times as long, the file
 * would be written into the install folder, where no uninstaller knows about it, and a JVM that finds one written for
 * other jars neither uses it nor replaces it - which after an update is every one of them.
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
 * a finalizer of the packaging task, so whatever builds the .msi - `desktop-publish.yml` included - gets it without
 * asking, and a script that cannot find what it edits fails that build rather than letting an installer out without it.
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

/** Whether this build runs on Windows, which is the only host jpackage builds the .msi on. */
val isWindowsHost get() = System.getProperty("os.name").orEmpty().lowercase().contains("windows")

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
