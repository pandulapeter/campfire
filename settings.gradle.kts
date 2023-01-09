include(
    ":app",
    ":data:model",
    ":data:repository",
    ":data:source:local",
    ":data:source:localImpl",
    ":data:source:remote",
    ":domain",
    ":presentation:debugMenu",
    ":presentation:utilities",
    ":presentation:shared",
    ":presentation:collections",
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