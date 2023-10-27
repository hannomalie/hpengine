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

    val imguiVersion = "1.86.1"

    api("io.github.spair:imgui-java-binding:$imguiVersion")
    api("io.github.spair:imgui-java-lwjgl3:$imguiVersion")
    api("io.github.spair:imgui-java-natives-windows:$imguiVersion") // TODO Make OS aware

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