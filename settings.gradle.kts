pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
    plugins {
        id("com.google.devtools.ksp") version "1.9.10-1.0.13"
    }
}

include("engine")
include("demos")

//includeBuild("../StruktGen")
include("api")
include("opengl")
include("glfw")
include("editor")
include("ocean")
include("deferredrenderer")
