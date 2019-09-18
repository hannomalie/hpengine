import org.gradle.internal.os.OperatingSystem
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    application
    kotlin("jvm")
}

application {
    mainClassName = "de.hanno.hpengine.engine.EngineImpl"
}
application.applicationDistribution.into("bin/hp") {
    from("../hp")
    include("**/*")
}
//
//java.sourceSets {
//    getByName("main").java.srcDirs("src/main/java")
//    getByName("test").java.srcDirs("src/test/java")
//}
kotlin.sourceSets {
    getByName("main").kotlin.srcDirs("src/main/java")
    getByName("test").kotlin.srcDirs("src/test/java")
}

repositories {

    maven {
        name = "local-dir"
        setUrl("libs")
    }
    flatDir {
        dir("libs")
    }
    mavenLocal()
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
    
    mavenCentral()
    jcenter()
}
val kotlinVersion: String by rootProject.extra
val lwjgl_version = "3.2.0"
val lwjgl_natives = when (OperatingSystem.current()) {
    OperatingSystem.LINUX   -> "natives-linux"
    OperatingSystem.MAC_OS  -> "natives-macos"
    OperatingSystem.WINDOWS -> "natives-windows"
    else -> throw Error("Unrecognized or unsupported Operating system. Please set \"lwjglNatives\" manually")
}
dependencies {
    compile(kotlin("stdlib-jdk8"))
    compile(kotlin("kotlin-reflect"))
    compile("javax.inject:javax.inject:1")
    compile("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.2.0-alpha-2")
    compile("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.2.0-alpha-2")
    compile(kotlin("compiler-embeddable"))


    compile("de.swirtz:ktsRunner:0.0.7")


    compile("", "PNGDecoder")
    compile("", "dds", "1.0.1-SNAPSHOT")

    compile("org.lwjgl:lwjgl:$lwjgl_version")
    compile("org.lwjgl:lwjgl-glfw:$lwjgl_version")
    compile("org.lwjgl:lwjgl-jawt:$lwjgl_version")
    compile("org.lwjgl:lwjgl-jemalloc:$lwjgl_version")
    compile("org.lwjgl:lwjgl-nanovg:$lwjgl_version")
    compile("org.lwjgl:lwjgl-opencl:$lwjgl_version")
    compile("org.lwjgl:lwjgl-opengl:$lwjgl_version")

    runtime("org.lwjgl", "lwjgl", lwjgl_version, classifier = lwjgl_natives)
    runtime("org.lwjgl", "lwjgl-glfw", lwjgl_version, classifier = lwjgl_natives)
    runtime("org.lwjgl", "lwjgl-jemalloc", lwjgl_version, classifier = lwjgl_natives)
    runtime("org.lwjgl", "lwjgl-nanovg", lwjgl_version, classifier = lwjgl_natives)
    runtime("org.lwjgl", "lwjgl-opengl", lwjgl_version, classifier = lwjgl_natives)

    compile("org.joml:joml:1.9.3")
//    TODO: Use ether of them
    compile("", "weblaf-complete", "1.28")
//    compile("org.pushing-pixels:radiance-substance:2.5.1")
    compile("", "vecmath")
    compile("", "rsyntaxtextarea")
    compile("jfree:jfreechart:1.0.13")
    compile("jcommon:jcommon:0.9.5")
    compile("commons-lang:commons-lang:2.3")
    compile("set.sf.sociaal:jbullet:3.0.0.20130526")
    compile("com.google.guava:guava:10.0.1")
    compile("net.engio:mbassador:1.2.4")
    compile("", "dahie-dds", "1")
    compile("commons-io:commons-io:2.4")
    compile("de.ruedigermoeller:fst:2.33")
    compile("com.carrotsearch:hppc:0.7.2")
    compile("de.hanno.compiler:java-compiler:1.4")
    compile("commons-beanutils:commons-beanutils:1.9.3")
    compile("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.1")
    compile("de.hanno.kotlin-structs:structs:1.0-SNAPSHOT")

    testCompile("junit:junit:4.12")
//    testCompile("org.jetbrains.kotlin:kotlin-test:${rootProject.extra["kotlin_version"]}")
}
