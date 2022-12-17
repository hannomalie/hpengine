pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
    }
}

include("engine")
include("newsimplegame")

includeBuild("../StruktGen")
include("api")
include("opengl")
include("glfw")
