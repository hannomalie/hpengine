import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.3.71"
    application
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


    repositories {

        mavenCentral()
        mavenLocal()
        maven {
            name = "local-dir"
            setUrl("${project.rootDir.resolve("libs").absolutePath}")
        }
        flatDir {
            dir("${project.rootDir.resolve("libs").absolutePath}")
        }
        maven {
            name = "java.net"
            setUrl("https://maven.java.net/content/repositories/public/")
        }
        maven {
            name = "my-bintray-repo"
            setUrl("https://dl.bintray.com/h-pernpeintner/maven-repo")
        }
        maven {
            name = "snapshots-repo"
            setUrl("https://oss.sonatype.org/content/repositories/snapshots")
        }
        maven {
            name = "kotlinx"
            setUrl("http://dl.bintray.com/kotlin/kotlinx")
        }

        maven {
            setUrl("https://dl.bintray.com/s1m0nw1/KtsRunner")
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
    implementation(project("editor"))
}

application {
    mainClassName = "de.hanno.hpengine.engine.EngineImpl"
}
val editorStartScript by tasks.registering(CreateStartScripts::class) {
    description = "Creates editor start script"
    classpath = tasks.startScripts.get().classpath
    outputDir = tasks.startScripts.get().outputDir
    mainClassName = "de.hanno.hpengine.editor.RibbonEditor"
    applicationName = "editor"
}
application.applicationDistribution.into("bin/hp") {
    from("./hp")
    include("**/*")
}
application.applicationDistribution.into("bin/game") {
    from("./simplegame/game")
    include("**/*")
}
application.applicationDistribution.into("bin") {
    from(editorStartScript)
    include("**/*")
}