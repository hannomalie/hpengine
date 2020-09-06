plugins {
    kotlin("jvm")
}

group = "de.hanno.hpengine"

dependencies {
    compileOnly(project(":engine"))
}

kotlin.sourceSets["main"].kotlin.srcDirs("game")

//TODO: Rework this concept completely
val copyJarToDistribution = tasks.create<Copy>("copyJarToDistribution"){
    doLast {
        from((tasks.getByName("jar") as Jar).archivePath)
        into(project.buildDir.toPath().resolve("distribution"))
    }
}

tasks.getByName("build").dependsOn("copyJarToDistribution")