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
    implementation(kotlin("stdlib-jdk8"))
    implementation(kotlin("reflect"))
    implementation("javax.inject:javax.inject:1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.3.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.3.2")
    implementation(kotlin("compiler-embeddable"))


    implementation("de.swirtz:ktsRunner:0.0.7")


    implementation("", "PNGDecoder")
    implementation("", "dds", "1.0.1-SNAPSHOT")

    implementation("org.lwjgl:lwjgl:$lwjgl_version")
    implementation("org.lwjgl:lwjgl-glfw:$lwjgl_version")
    implementation("org.lwjgl:lwjgl-jawt:$lwjgl_version")
    implementation("org.lwjgl:lwjgl-jemalloc:$lwjgl_version")
    implementation("org.lwjgl:lwjgl-nanovg:$lwjgl_version")
    implementation("org.lwjgl:lwjgl-opencl:$lwjgl_version")
    implementation("org.lwjgl:lwjgl-opengl:$lwjgl_version")

    implementation("org.lwjgl", "lwjgl", lwjgl_version, classifier = lwjgl_natives)
    implementation("org.lwjgl", "lwjgl-glfw", lwjgl_version, classifier = lwjgl_natives)
    implementation("org.lwjgl", "lwjgl-jemalloc", lwjgl_version, classifier = lwjgl_natives)
    implementation("org.lwjgl", "lwjgl-nanovg", lwjgl_version, classifier = lwjgl_natives)
    implementation("org.lwjgl", "lwjgl-opengl", lwjgl_version, classifier = lwjgl_natives)

    implementation("org.joml:joml:1.9.3")
//    TODO: Remove weblaf stuff
    implementation("", "weblaf-complete", "1.28")
    implementation("", "lwjgl3-awt", "0.1.6")
    implementation("", "vecmath")
    implementation("", "rsyntaxtextarea")
    implementation("jfree:jfreechart:1.0.13")
    implementation("jcommon:jcommon:0.9.5")
    implementation("commons-lang:commons-lang:2.3")
    implementation("set.sf.sociaal:jbullet:3.0.0.20130526")
    implementation("com.google.guava:guava:10.0.1")
    implementation("net.engio:mbassador:1.2.4")
    implementation("", "dahie-dds", "1")
    implementation("commons-io:commons-io:2.4")
    implementation("de.ruedigermoeller:fst:2.33")
    implementation("com.carrotsearch:hppc:0.7.2")
    implementation("de.hanno.compiler:java-compiler:1.4")
    implementation("commons-beanutils:commons-beanutils:1.9.3")
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.1")
    implementation("de.hanno.structs:structs:1.0.1-SNAPSHOT")
//    implementation("com.github.hannespernpeintner:kotlin-structs:a1692d5a8d")
    val radianceVersion = "2.5.1" // "3.0-SNAPSHOT"
    implementation("org.pushing-pixels:radiance-ember:$radianceVersion")
    implementation("org.pushing-pixels:radiance-substance-extras:$radianceVersion")
    implementation("org.pushing-pixels:radiance-flamingo:$radianceVersion")
    implementation("org.pushing-pixels:radiance-photon:$radianceVersion")
    implementation("org.pushing-pixels:radiance-meteor:$radianceVersion")
    implementation("com.miglayout:miglayout:3.7.4")

    testImplementation("junit:junit:4.12")
}
