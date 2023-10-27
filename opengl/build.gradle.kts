import de.hanno.hpengine.build.Dependencies
import de.hanno.hpengine.build.Dependencies.LWJGL
import de.hanno.hpengine.build.Dependencies.StruktGen
import de.hanno.hpengine.build.Dependencies.configureCommonTestDependencies

plugins {
    kotlin("jvm")
    id("com.google.devtools.ksp")
}

dependencies {

    api(kotlin("stdlib"))
    api(project(":api"))
    implementation("org.lwjgl:lwjgl-opengl:${LWJGL.version}")
    api("org.lwjgl", "lwjgl", LWJGL.version, classifier = LWJGL.natives)
    api("org.lwjgl", "lwjgl-opengl", LWJGL.version, classifier = LWJGL.natives)

    implementation("com.twelvemonkeys.imageio:imageio-tga:3.9.4")

    api(StruktGen.api)
    ksp(StruktGen.processor)

    ksp(Dependencies.Koin.compiler)

    testImplementation(project(":glfw"))

    configureCommonTestDependencies()
}

kotlin {
    sourceSets["main"].apply {
        kotlin.srcDir("build/generated/ksp/main/kotlin/")
    }
}