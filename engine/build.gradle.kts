import de.hanno.hpengine.build.Dependencies
import de.hanno.hpengine.build.Dependencies.LWJGL
import de.hanno.hpengine.build.Dependencies.StruktGen

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("net.onedaybeard.artemis:artemis-odb-gradle-plugin:2.3.0")
    }
}

plugins {
    kotlin("jvm")
    id("com.google.devtools.ksp")
}

plugins.apply("artemis")

tasks.named("weave", net.onedaybeard.gradle.ArtemisWeavingTask::class) {
    classesDirs = sourceSets.main.get().output.classesDirs
    isEnableArtemisPlugin = true
    isEnablePooledWeaving = true
    isGenerateLinkMutators = true
    isOptimizeEntitySystems = true
}
tasks.build {
    finalizedBy(tasks.named("weave"))
}

tasks.classes {
    dependsOn(tasks.named("kspKotlin"))
}

dependencies {
    api(kotlin("stdlib"))
    api(kotlin("stdlib-jdk8"))
    api(kotlin("reflect"))
    api(kotlin("compiler-embeddable"))

    api("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.3.5")

    api(project(":api"))
    api(project(":glfw")) // TODO: Can I remove this somehow?
    implementation(project(":opengl"))

    api("javax.inject:javax.inject:1")

    api("com.github.s1monw1:KtsRunner:v0.0.7")

    api(fileTree(mapOf("dir" to "../libs", "include" to "*.jar")))
    api("", "PNGDecoder")
    api("", "dds", "1.0.1-SNAPSHOT")
    api("", "DDSUtils")

    api("org.lwjgl:lwjgl:${LWJGL.version}")
    api("org.lwjgl:lwjgl-glfw:${LWJGL.version}")
    api("org.lwjgl:lwjgl-jawt:${LWJGL.version}")
    api("org.lwjglx:lwjgl3-awt:0.1.8")
    api("org.lwjgl:lwjgl-jemalloc:${LWJGL.version}")
    api("org.lwjgl:lwjgl-nanovg:${LWJGL.version}")
    api("org.lwjgl:lwjgl-opencl:${LWJGL.version}")
    api("org.lwjgl:lwjgl-assimp:${LWJGL.version}")

    api("org.lwjgl", "lwjgl", LWJGL.version, classifier = LWJGL.natives)
    api("org.lwjgl", "lwjgl-jemalloc", LWJGL.version, classifier = LWJGL.natives)
    api("org.lwjgl", "lwjgl-nanovg", LWJGL.version, classifier = LWJGL.natives)
    api("org.lwjgl", "lwjgl-assimp", LWJGL.version, classifier = LWJGL.natives)

    api("org.joml:joml:1.9.3")
    api("", "lwjgl3-awt", "0.1.6")
    api("javax.vecmath:vecmath:1.5.2")
    api("jcommon:jcommon:0.9.5")
    api("commons-lang:commons-lang:2.3")
    api("set.sf.sociaal:jbullet:3.0.0.20130526")
    api("com.google.guava:guava:10.0.1")
    api("net.engio:mbassador:1.2.4")
    api("de.ruedigermoeller:fst:2.33")
    api("commons-beanutils:commons-beanutils:1.9.3")
    api("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.1")
    api("com.dreizak:miniball:1.0.3")
    api("org.apache.logging.log4j:log4j-api:2.13.0")
    api("org.apache.logging.log4j:log4j-core:2.13.0")

    api(StruktGen.api)
    ksp(StruktGen.processor)

    api("io.github.config4k:config4k:0.4.2")

    api(Dependencies.Koin.core)
    api(Dependencies.Koin.annotations)
    ksp(Dependencies.Koin.compiler)

    api("net.onedaybeard.artemis:artemis-odb:2.3.0")
    api("net.onedaybeard.artemis:artemis-odb-serializer-json:2.3.0")
    api("net.mostlyoriginal.artemis-odb:contrib-plugin-singleton:2.5.0")

    implementation("com.esotericsoftware:kryo:5.3.0")

    testImplementation(Dependencies.Koin.test)

    testImplementation("junit:junit:4.12")
}

kotlin {
    sourceSets["main"].apply {
        kotlin.srcDir("build/generated/ksp/main/kotlin/")
    }
}
