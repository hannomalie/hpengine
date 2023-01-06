plugins {
    kotlin("jvm")
    id("com.google.devtools.ksp") version "1.7.21-1.0.8"
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
    implementation("org.lwjgl:lwjgl-opengl:$lwjgl_version")
    api("org.lwjgl", "lwjgl", lwjgl_version, classifier = lwjgl_natives)
    api("org.lwjgl", "lwjgl-opengl", lwjgl_version, classifier = lwjgl_natives)


    implementation("com.twelvemonkeys.imageio:imageio-tga:3.9.4")

    ksp("de.hanno.struktgen:processor:1.0.0-SNAPSHOT")

    testImplementation(project(":glfw"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.1")
    testImplementation("io.kotest:kotest-runner-junit5:5.5.4")
    testImplementation("io.kotest:kotest-assertions-core:5.5.4")
    testImplementation("io.mockk:mockk:1.13.3")

    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.1")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

kotlin {
    sourceSets["main"].apply {
        kotlin.srcDir("build/generated/ksp/main/kotlin/")
    }
}