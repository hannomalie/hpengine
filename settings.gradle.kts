pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
    }
}

include("engine")
include("newsimplegame")

includeBuild("../kotlin-structs")
includeBuild("../StruktGen")