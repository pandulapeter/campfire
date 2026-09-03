"VERSION_NAME" set "2.0.2"
"VERSION_CODE" set 24
"KEY_ALIAS" set "androiddebugkey"
"KEY_PASSWORD" set "android"
"STORE_FILE" set "internal.keystore"
"STORE_PASSWORD" set "android"
"TARGET_SDK_VERSION" set 37
"MIN_SDK_VERSION" set 28

infix fun String.set(value: Any) = System.setProperty(this, value.toString())

buildscript {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://plugins.gradle.org/m2/") }
    }
    dependencies {
        classpath(libs.gradle)
        classpath(libs.kotlin)
        classpath(libs.kotlin.compose.compiler)
    }
}
