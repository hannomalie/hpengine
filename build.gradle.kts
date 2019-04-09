plugins {
    kotlin("jvm") version "1.3.21"
}
version = "1.0.0-SNAPSHOT"

buildscript {

    val kotlin_version by extra { "1.3.21" }

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
}
dependencies {
    implementation(kotlin("stdlib"))
}
repositories {
    mavenCentral()
}