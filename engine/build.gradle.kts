import org.gradle.internal.os.OperatingSystem
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
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

val kotlinVersion: String by rootProject.extra
val lwjgl_version = "3.2.3"
val lwjgl_natives = when (OperatingSystem.current()) {
    OperatingSystem.LINUX   -> "natives-linux"
    OperatingSystem.MAC_OS  -> "natives-macos"
    OperatingSystem.WINDOWS -> "natives-windows"
    else -> throw Error("Unrecognized or unsupported Operating system. Please set \"lwjglNatives\" manually")
}
dependencies {

    api(kotlin("stdlib"))
    api(kotlin("stdlib-jdk8"))
    api(kotlin("reflect"))
    api(kotlin("script-runtime"))
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.5")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.3.5")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.3.5")
    api(kotlin("compiler-embeddable"))

    api("javax.inject:javax.inject:1")

    api("de.swirtz:ktsRunner:0.0.7")

    api("", "PNGDecoder")
    api("", "dds", "1.0.1-SNAPSHOT")

    api("org.lwjgl:lwjgl:$lwjgl_version")
    api("org.lwjgl:lwjgl-glfw:$lwjgl_version")
    api("org.lwjgl:lwjgl-jawt:$lwjgl_version")
    api("org.lwjgl:lwjgl-jemalloc:$lwjgl_version")
    api("org.lwjgl:lwjgl-nanovg:$lwjgl_version")
    api("org.lwjgl:lwjgl-opencl:$lwjgl_version")
    api("org.lwjgl:lwjgl-opengl:$lwjgl_version")
    api("org.lwjgl:lwjgl-assimp:$lwjgl_version")

    api("org.lwjgl", "lwjgl", lwjgl_version, classifier = lwjgl_natives)
    api("org.lwjgl", "lwjgl-glfw", lwjgl_version, classifier = lwjgl_natives)
    api("org.lwjgl", "lwjgl-jemalloc", lwjgl_version, classifier = lwjgl_natives)
    api("org.lwjgl", "lwjgl-nanovg", lwjgl_version, classifier = lwjgl_natives)
    api("org.lwjgl", "lwjgl-opengl", lwjgl_version, classifier = lwjgl_natives)
    api("org.lwjgl", "lwjgl-assimp", lwjgl_version, classifier = lwjgl_natives)

    api("org.joml:joml:1.9.3")
    api("", "lwjgl3-awt", "0.1.6")
    api("", "vecmath")
    api("", "rsyntaxtextarea")
    api("jfree:jfreechart:1.0.13")
    api("jcommon:jcommon:0.9.5")
    api("commons-lang:commons-lang:2.3")
    api("set.sf.sociaal:jbullet:3.0.0.20130526")
    api("com.google.guava:guava:10.0.1")
    api("net.engio:mbassador:1.2.4")
    api("", "dahie-dds", "1")
    api("commons-io:commons-io:2.4")
    api("de.ruedigermoeller:fst:2.33")
    api("com.carrotsearch:hppc:0.7.2")
    api("de.hanno.compiler:java-compiler:1.4")
    api("commons-beanutils:commons-beanutils:1.9.3")
    api("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.1")
    api("de.hanno.structs:structs:1.0.1-SNAPSHOT")
//    api("com.github.hannespernpeintner:kotlin-structs:a1692d5a8d")
    val radianceVersion =  "3.0-SNAPSHOT"
    api("org.pushing-pixels:radiance-ember:$radianceVersion")
    api("org.pushing-pixels:radiance-substance-extras:$radianceVersion")
    api("org.pushing-pixels:radiance-flamingo:$radianceVersion")
    api("org.pushing-pixels:radiance-photon:$radianceVersion")
    api("org.pushing-pixels:radiance-meteor:$radianceVersion")
    api("com.miglayout:miglayout:3.7.4")
    api("com.dreizak:miniball:1.0.3")
    api("org.apache.logging.log4j:log4j-api:2.13.0")
    api("org.apache.logging.log4j:log4j-core:2.13.0")

    testImplementation("junit:junit:4.12")
}
