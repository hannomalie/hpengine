import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.3.50"
}
version = "1.0.0-SNAPSHOT"

buildscript {
    repositories {
        mavenLocal()
        mavenCentral()
    }
}

allprojects {
    group = "de.hanno.hpengine"
    tasks.withType<KotlinCompile> {
        kotlinOptions {
            freeCompilerArgs = listOf("-Xjvm-default=enable")
            jvmTarget = JavaVersion.VERSION_1_8.toString()
        }
    }
}
dependencies {
    implementation(kotlin("stdlib"))
}
repositories {
    mavenCentral()
}