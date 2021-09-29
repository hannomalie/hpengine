pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
    }
}

include("engine")
include("newsimplegame")
include("editor")

includeBuild("../kotlin-structs")
includeBuild("../StruktGen")