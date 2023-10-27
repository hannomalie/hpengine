import de.hanno.hpengine.build.Dependencies.LWJGL

plugins {
    kotlin("jvm")
}

repositories {
    mavenCentral()
}

dependencies {
    api(kotlin("stdlib"))
    api(project(":api"))

    api("org.lwjgl", "lwjgl-glfw", LWJGL.version, classifier = LWJGL.natives)
    api("org.lwjgl", "lwjgl", LWJGL.version, classifier = LWJGL.natives)

    implementation("org.lwjgl:lwjgl-opengl:${LWJGL.version}")
    implementation(project(":opengl"))
}