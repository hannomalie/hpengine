pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
    }
}

include("engine")
include("simplegame")
include("newsimplegame")
include("editor")

includeBuild("../kotlin-structs")
includeBuild("../StruktGen")