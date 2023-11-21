
import com.badlogicgames.packr.Packr
import com.badlogicgames.packr.PackrConfig
import com.github.jengelman.gradle.plugins.shadow.internal.JavaJarExec
import de.hanno.hpengine.build.Dependencies
import java.util.Arrays


buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("com.nimblygames.packr:packr:2.7.0")
    }
}

plugins {
    kotlin("jvm")
    application
    id("com.github.johnrengelman.shadow") version "6.0.0"
    id("com.google.devtools.ksp")
}

application {
    mainClass.set("scenes.DemoKt")
    mainClassName = "scenes.DemoKt" // Caution, don't remove despite deprecated, will break shadow jar tasks!
}
dependencies {
    api(project(":engine"))
    api(project(":editor"))
    api(project(":deferredrenderer"))
    api(project(":ocean"))

    api(Dependencies.Koin.annotations)
    ksp(Dependencies.Koin.compiler)
}

val bundleLinux by tasks.registering {
    dependsOn(tasks.shadowJar)
    doFirst {
        val config = PackrConfig().apply {
            platform = PackrConfig.Platform.Linux64
            val pathToJdk = "/home/tenter/Downloads/zulu11.41.23-ca-jdk11.0.8-linux_x64.zip"
            require(File(pathToJdk).exists()) { "You have to provide a jdk to bundle the application" }
            jdk = pathToJdk
            executable = "myapp"
            classpath = listOf(tasks.shadowJar.get().outputs.files.first().path)
            removePlatformLibs = classpath
            mainClass = "Game"
            vmArgs = listOf("Xmx4G")
            minimizeJre = "soft"
            outDir = buildDir.resolve("out-linux")
            useZgcIfSupportedOs = true
            iconResource = projectDir.resolve("hpengine.ico")
        }

        Packr().pack(config)
    }
}

kotlin {
    sourceSets["main"].apply {
        kotlin.srcDir("build/generated/ksp/main/kotlin/")
    }
}

tasks.getByName<JavaExec>("run") {
//    systemProperty("gameDir", rootDir.resolve("src/main/resources/game"))
//    systemProperty("engineDir", rootDir.resolve("../engine/src/main/resources/hp"))
//    systemProperty("demo", "Ocean")
//    environment("demo", "MultipleObjects")

//    classpath = sourceSets.main.get().runtimeClasspath
}
//tasks.create<JavaExec>("runDemo") {
//    group = "demo"
//    main = "scenes.DemoKt"
//    systemProperty("gameDir", rootDir.resolve("src/main/resources/game"))
//    systemProperty("engineDir", rootDir.resolve("../engine/src/main/resources/hp"))
//    systemProperty("demo", "Ocean")
//}
//tasks.create<JavaExec>("runOceanDemo") {
//    group = "demo"
//    main = "scenes.DemoKt"
//    systemProperty("gameDir", rootDir.resolve("src/main/resources/game"))
//    systemProperty("engineDir", rootDir.resolve("../engine/src/main/resources/hp"))
//    systemProperty("demo", "Ocean")
//}