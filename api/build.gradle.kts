import org.gradle.internal.os.OperatingSystem.*

plugins {
    kotlin("jvm")
    id("com.google.devtools.ksp") version "1.9.10-1.0.13"
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

    api("", "dahie-dds", "1.0.0-SNAPSHOT")

    val koinVersion= "3.1.1"
    api("io.insert-koin:koin-core:$koinVersion")
    api("io.insert-koin:koin-annotations:1.2.2")
    ksp("io.insert-koin:koin-ksp-compiler:1.2.2")

    api("net.onedaybeard.artemis:artemis-odb:2.3.0")
    api("net.onedaybeard.artemis:artemis-odb-serializer-json:2.3.0")
    api("net.mostlyoriginal.artemis-odb:contrib-plugin-singleton:2.5.0")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.1")
    testImplementation("io.kotest:kotest-runner-junit5:5.5.4")
    testImplementation("io.kotest:kotest-assertions-core:5.5.4")
    testImplementation("io.insert-koin:koin-test:$koinVersion")

    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.1")
}

kotlin {
    sourceSets["main"].apply {
        kotlin.srcDir("build/generated/ksp/main/kotlin/")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}