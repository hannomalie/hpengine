plugins {
    kotlin("jvm")
    application
}

group = "de.hanno.hpengine"

application {
    mainClassName = "MainKt"
}

dependencies {
    implementation(project(":engine"))
}