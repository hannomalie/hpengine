@file:Suppress("INACCESSIBLE_TYPE")

package de.hanno.hpengine.build

import org.gradle.internal.os.OperatingSystem
import org.gradle.kotlin.dsl.DependencyHandlerScope

object Dependencies {
    object StruktGen {
        // This points to the latest commit on the context-receivers branch for now,
        // until context receivers are more stable
        const val processor = "com.github.hannomalie.StruktGen:processor:context-receivers-SNAPSHOT"
        const val api = "com.github.hannomalie.StruktGen:api:context-receivers-SNAPSHOT"
    }
    object Koin {
        const val compiler = "io.insert-koin:koin-ksp-compiler:1.2.2"
        const val annotations = "io.insert-koin:koin-annotations:1.2.2"
        const val core = "io.insert-koin:koin-core:3.1.1"
        const val test = "io.insert-koin:koin-test:3.1.1"
    }
    object LWJGL {
        const val version = "3.3.6"

        val natives = when (OperatingSystem.current()) {
            OperatingSystem.LINUX   -> "natives-linux"
            OperatingSystem.MAC_OS  -> "natives-macos"
            OperatingSystem.WINDOWS -> "natives-windows"
            else -> throw Error("""Unrecognized or unsupported Operating system. Please set "lwjglNatives" manually""")
        }
    }

    object Imgui {
        const val version = "1.89.0"

        val osIdentifier = when (OperatingSystem.current()) {
            OperatingSystem.LINUX   -> "linux"
            OperatingSystem.MAC_OS  -> "macos"
            OperatingSystem.WINDOWS -> "windows"
            else -> throw Error("""Unrecognized or unsupported Operating system. Please set imgui native dependency manually""")
        }
    }

    fun DependencyHandlerScope.configureCommonTestDependencies() {
        "testImplementation"("org.junit.jupiter:junit-jupiter-api:5.8.1")
        "testImplementation"("io.kotest:kotest-runner-junit5:5.5.4")
        "testImplementation"("io.kotest:kotest-assertions-core:5.5.4")
        "testImplementation"("io.mockk:mockk:1.13.3")

        "testRuntimeOnly"("org.junit.jupiter:junit-jupiter-engine:5.8.1")
    }
}
