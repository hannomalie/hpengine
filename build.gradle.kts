import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.7.21"
    application
}
version = "1.0.0-SNAPSHOT"

buildscript {
    repositories {
        mavenLocal()
        mavenCentral()
        google()
    }
}

allprojects {
    group = "de.hanno.hpengine"
    tasks.withType<KotlinCompile> {
        kotlinOptions {
            freeCompilerArgs = listOf("-Xjvm-default=all", "-Xcontext-receivers")
            jvmTarget = "1.8"
        }
    }


    repositories {

        flatDir {
            dir(project.rootDir.resolve("libs").absolutePath)
        }

        mavenCentral()
        mavenLocal()
        google()
        maven {
            name = "local-dir"
            setUrl(project.rootDir.resolve("libs").absolutePath)
        }
        maven {
            name = "java.net"
            setUrl("https://maven.java.net/content/repositories/public/")
        }
        maven {
            name = "snapshots-repo"
            setUrl("https://oss.sonatype.org/content/repositories/snapshots")
        }

        maven {
            setUrl("https://oss.sonatype.org")
        }

        jcenter()

        maven { setUrl("https://jitpack.io") }
    }
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(kotlin("stdlib-jdk8"))
    implementation(kotlin("reflect"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.5")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.3.5")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.3.5")
    implementation(kotlin("compiler-embeddable"))

    implementation(project("engine"))
}

application {
    mainClassName = "de.hanno.hpengine.engine.Engine"
}
application.applicationDistribution.into("bin/hp") {
    from("./hp")
    include("**/*")
}
application.applicationDistribution.into("bin/game") {
    from("./simplegame/game")
    include("**/*")
}
