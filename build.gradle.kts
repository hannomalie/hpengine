import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.3.21"
}
version = "1.0.0-SNAPSHOT"

buildscript {
    val kotlinVersion by rootProject.extra { "1.3.21" }

    repositories {
        mavenLocal()
        mavenCentral()
    }

    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.3.21")
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