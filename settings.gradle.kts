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
include(":feature:traceroute")
include(":feature:wol")
include(":feature:tls")
include(":feature:whois")
include(":feature:netlog")
include(":feature:mdns")
include(":feature:httptester")
include(":feature:monitor")
include(":feature:history")
include(":feature:widgetsettings")
include(":feature:posture")
include(":feature:ipcalc")
include(":feature:speedtest")
include(":widget")
