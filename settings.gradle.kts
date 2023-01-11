include(
    ":app:android",
    ":app:desktop",
    ":data:model",
    ":data:repository:api",
    ":data:repository:implementation",
    ":data:source:local:api",
    ":data:source:local:implementation-android",
    ":data:source:local:implementation-desktop",
    ":data:source:remote:api",
    ":data:source:remote:implementation-jvm",
    ":domain:api",
    ":domain:implementation",
    ":presentation:android",
    ":presentation:android-debug-menu",
    ":presentation:desktop",
    ":presentation:shared"
)
rootProject.name = "Campfire"
enableFeaturePreview("VERSION_CATALOGS")
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io/") }
    }
}