import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `kotlin-dsl`
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.gradle)
    implementation(libs.kotlin)
}

gradlePlugin {
    plugins {
        register("library") {
            id = "campfire-library"
            implementationClass = "com.pandulapeter.campfire.buildLogic.plugins.LibraryPlugin"
        }
        register("compose-library") {
            id = "campfire-compose-library"
            implementationClass = "com.pandulapeter.campfire.buildLogic.plugins.ComposeLibraryPlugin"
        }
    }
}
