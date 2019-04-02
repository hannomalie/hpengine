group = "de.hanno.hpengine"

plugins {
    id("java")
}

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

//TODO: Rework this concept completely
val copyJarToDistribution = tasks.create<Copy>("copyJarToDistribution"){
    doLast {
        from((tasks.getByName("jar") as Jar).archivePath)
        into(project.buildDir.toPath().resolve("distribution"))
    }
}

tasks.getByName("build").dependsOn("copyJarToDistribution")