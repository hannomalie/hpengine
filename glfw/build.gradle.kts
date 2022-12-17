plugins {
    kotlin("jvm")
}

repositories {
    mavenCentral()
}

val lwjgl_natives = when (org.gradle.internal.os.OperatingSystem.current()) {
    org.gradle.internal.os.OperatingSystem.LINUX   -> "natives-linux"
    org.gradle.internal.os.OperatingSystem.MAC_OS  -> "natives-macos"
    org.gradle.internal.os.OperatingSystem.WINDOWS -> "natives-windows"
    else -> throw Error("Unrecognized or unsupported Operating system. Please set \"lwjglNatives\" manually")
}

dependencies {
    // TODO: Move this up, so it can be shared between subprojects
    val lwjgl_version = "3.2.3"

    api(kotlin("stdlib"))
    api(project(":api"))

    api("org.lwjgl", "lwjgl-glfw", lwjgl_version, classifier = lwjgl_natives)

    api("org.lwjgl", "lwjgl", lwjgl_version, classifier = lwjgl_natives)
    implementation("org.lwjgl:lwjgl-opengl:$lwjgl_version")
    implementation(project(":opengl"))
}