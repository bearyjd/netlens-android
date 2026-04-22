pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "NetLens"

include(":app")
include(":core:network")
include(":core:data")
include(":core:oui")
include(":feature:ipinfo")
include(":feature:lanscan")
include(":feature:portscan")
include(":feature:dns")
include(":feature:ping")
include(":feature:wol")
include(":widget")
