plugins {
    kotlin("jvm")
    application
}

group = "de.hanno.hpengine"

application {
    mainClassName = "Game"
}

dependencies {
    implementation(project(":engine"))
    implementation(project(":editor"))
}