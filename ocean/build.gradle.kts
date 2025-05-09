import de.hanno.hpengine.build.Dependencies
import de.hanno.hpengine.build.Dependencies.StruktGen
import de.hanno.hpengine.build.Dependencies.configureCommonTestDependencies

plugins {
    kotlin("jvm")
    id("com.google.devtools.ksp")
}

dependencies {
    api(kotlin("stdlib"))
    api(project(":api"))
    api(project(":engine"))
    api(project(":editor"))

    api(StruktGen.api)
    ksp(StruktGen.processor)

    api(Dependencies.Koin.annotations)
    ksp(Dependencies.Koin.compiler)


    configureCommonTestDependencies()
}

kotlin {
    sourceSets["main"].apply {
        kotlin.srcDir("build/generated/ksp/main/kotlin/")
    }
}