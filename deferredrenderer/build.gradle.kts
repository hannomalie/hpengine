import de.hanno.hpengine.build.Dependencies.Koin
import de.hanno.hpengine.build.Dependencies.StruktGen

plugins {
    kotlin("jvm")
    id("com.google.devtools.ksp")
}

dependencies {
    api(StruktGen.api)
    ksp(StruktGen.processor)

    api(Koin.annotations)
    ksp(Koin.compiler)

    api(project(":api"))
    implementation(project(":editor"))

    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

kotlin {
    sourceSets["main"].apply {
        kotlin.srcDir("build/generated/ksp/main/kotlin/")
    }
}