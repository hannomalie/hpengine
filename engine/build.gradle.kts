import org.jetbrains.kotlin.gradle.dsl.KotlinCommonOptions
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.internal.os.OperatingSystem

plugins {
    application
    kotlin("jvm")
}

application {
    mainClassName = "de.hanno.hpengine.engine.Engine"
}
application.applicationDistribution.into("bin/hp") {
    from("../hp")
    include("**/*")
}

java.sourceSets {
    getByName("main").java.srcDirs("src/main/java")
    getByName("test").java.srcDirs("src/test/java")
}
kotlin.sourceSets {
    getByName("main").kotlin.srcDirs("src/main/java")
    getByName("test").kotlin.srcDirs("src/test/java")
}

tasks.withType<KotlinCompile>().configureEachLater {
    println("Configuring $name in project ${project.name}...")
    kotlinOptions {
        suppressWarnings = true
        freeCompilerArgs = listOf("-Xjvm-default=enable")
    }
}

repositories {

    maven {
        name = "local-dir"
        setUrl("libs")
    }
    flatDir {
        dir("libs")
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
    mavenCentral()
    mavenLocal()
    jcenter()
}
val lwjgl_version = "3.2.0"
val lwjgl_natives = when (OperatingSystem.current()) {
    OperatingSystem.LINUX   -> "natives-linux"
    OperatingSystem.MAC_OS  -> "natives-macos"
    OperatingSystem.WINDOWS -> "natives-windows"
    else -> throw Error("Unrecognized or unsupported Operating system. Please set \"lwjglNatives\" manually")
}

dependencies {
    compile(kotlin("stdlib-jdk8"))
    compile("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.1.1")

    compile("", "PNGDecoder")
    compile("", "dds", "1.0.1-SNAPSHOT")
//    compile("", "DDSUtils")

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
//    compile("org.slick2d:slick2d-core:1.0.1")
    compile("", "weblaf-complete", "1.28")
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
    compile("org.jetbrains.kotlinx:kotlinx-coroutines-core:0.23.4")
    compile("de.hanno.structs:structs:1.0-SNAPSHOT")

    testCompile("junit:junit:4.12")
//    testCompile("org.jetbrains.kotlin:kotlin-test:${rootProject.extra["kotlin_version"]}")
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
    jvmTarget = "1.8"
}
val compileTestKotlin: KotlinCompile by tasks
compileTestKotlin.kotlinOptions {
    jvmTarget = "1.8"
}