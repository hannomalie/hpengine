plugins {
    kotlin("jvm")
    id("com.google.devtools.ksp") version "1.7.21-1.0.8"
}

group = "de.hanno.hpengine"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    ksp("de.hanno.struktgen:processor:1.0.0-SNAPSHOT")

    api("io.insert-koin:koin-annotations:1.2.2")
    ksp("io.insert-koin:koin-ksp-compiler:1.2.2")
    api(project(":api"))
    implementation(project(":editor"))
    implementation(project(":engine"))

    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    sourceSets["main"].apply {
        kotlin.srcDir("build/generated/ksp/main/kotlin/")
    }
}