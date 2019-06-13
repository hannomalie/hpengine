import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java
    kotlin("jvm")
}

group = "de.hanno.hpengine"
//apply(plugin = "java")

repositories {

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
        name = "local-dir"
        setUrl("engine/libs")
    }
    flatDir {
        dir("../engine/libs")
    }
    mavenCentral()
    mavenLocal()
    jcenter()
}

dependencies {
    compileOnly(project(":engine"))
}

java.sourceSets["main"].java {
    srcDir("game")
}
kotlin.sourceSets["main"].kotlin.srcDirs("game")

tasks.withType<KotlinCompile>().configureEachLater {
    println("Configuring $name in project ${project.name}...")
    kotlinOptions {
        suppressWarnings = true
        freeCompilerArgs = listOf("-Xjvm-default=enable")
        jvmTarget = JavaVersion.VERSION_1_8.toString()
    }
}


//TODO: Rework this concept completely
val copyJarToDistribution = tasks.create<Copy>("copyJarToDistribution"){
    doLast {
        from((tasks.getByName("jar") as Jar).archivePath)
        into(project.buildDir.toPath().resolve("distribution"))
    }
}

tasks.getByName("build").dependsOn("copyJarToDistribution")