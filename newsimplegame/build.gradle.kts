
import com.badlogicgames.packr.Packr
import com.badlogicgames.packr.PackrConfig
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
}

group = "de.hanno.hpengine"

application {
    mainClassName = "scenes.InstancingDemoKt"
}
dependencies {
    api(project(":engine"))
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