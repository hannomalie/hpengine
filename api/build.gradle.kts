import org.gradle.internal.os.OperatingSystem.*

plugins {
    kotlin("jvm")
    id("com.google.devtools.ksp") version "1.7.21-1.0.8"
}

repositories {
    mavenCentral()
}

val lwjgl_version = "3.2.3"
val lwjgl_natives = when (current()) {
    LINUX   -> "natives-linux"
    MAC_OS  -> "natives-macos"
    WINDOWS -> "natives-windows"
    else -> throw Error("""Unrecognized or unsupported Operating system. Please set "lwjglNatives" manually""")
}

dependencies {
    api(kotlin("stdlib"))
    api(kotlin("stdlib-jdk8"))
    api(kotlin("reflect"))
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.5")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.3.5")

    api("de.hanno.struktgen:api:1.0.0-SNAPSHOT")
    ksp("de.hanno.struktgen:processor:1.0.0-SNAPSHOT")

    api("org.lwjgl:lwjgl:$lwjgl_version")
    api("org.lwjgl:lwjgl-glfw:$lwjgl_version")

    api("org.joml:joml:1.9.3")

    api("commons-io:commons-io:2.4")

    api("io.github.classgraph:classgraph:4.8.89")

    api("com.carrotsearch:hppc:0.7.2")
}

kotlin {
    sourceSets["main"].apply {
        kotlin.srcDir("build/generated/ksp/main/kotlin/")
    }
}