import de.hanno.hpengine.build.Dependencies.Koin
import de.hanno.hpengine.build.Dependencies.LWJGL
import de.hanno.hpengine.build.Dependencies.StruktGen
import de.hanno.hpengine.build.Dependencies.configureCommonTestDependencies

plugins {
    kotlin("jvm")
    id("com.google.devtools.ksp") version "1.9.10-1.0.13"
}

dependencies {
    api(kotlin("stdlib"))
    api(kotlin("stdlib-jdk8"))
    api(kotlin("reflect"))
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.5")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.3.5")

    api(StruktGen.api)
    ksp(StruktGen.processor)

    api("org.lwjgl:lwjgl:${LWJGL.version}")
    api("org.lwjgl:lwjgl-glfw:${LWJGL.version}")

    api("org.joml:joml:1.9.3")

    api("commons-io:commons-io:2.4")

    api("io.github.classgraph:classgraph:4.8.89")

    api("com.carrotsearch:hppc:0.7.2")

    api("", "dahie-dds", "1.0.0-SNAPSHOT")

    api(Koin.core)

    api(Koin.annotations)
    ksp(Koin.compiler)

    api("net.onedaybeard.artemis:artemis-odb:2.3.0")
    api("net.onedaybeard.artemis:artemis-odb-serializer-json:2.3.0")
    api("net.mostlyoriginal.artemis-odb:contrib-plugin-singleton:2.5.0")

    configureCommonTestDependencies()
    testImplementation(Koin.test)
}

kotlin {
    sourceSets["main"].apply {
        kotlin.srcDir("build/generated/ksp/main/kotlin/")
    }
}
