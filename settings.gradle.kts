pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "PDToscillo"

include(":app")

include(":core:common")
include(":core:model")
include(":core:network")
include(":core:scpi")
include(":core:waveform")
include(":core:database")
include(":core:ui")

include(":feature:connection")
include(":feature:oscilloscope")
include(":feature:waveform")
include(":feature:measurement")
include(":feature:automation")
include(":feature:files")
include(":feature:settings")
include(":feature:console")

include(":simulator")
