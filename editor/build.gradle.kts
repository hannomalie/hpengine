import de.hanno.hpengine.build.Dependencies
import de.hanno.hpengine.build.Dependencies.Imgui
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
    api(project(":opengl"))

    api("io.github.spair:imgui-java-binding:${Imgui.version}")
    api("io.github.spair:imgui-java-lwjgl3:${Imgui.version}")
    api("io.github.spair:imgui-java-natives-${Imgui.osIdentifier}:${Imgui.version}")

    api(StruktGen.api)
    ksp(StruktGen.processor)

    api(Dependencies.Koin.annotations)
    ksp(Dependencies.Koin.compiler)

    testImplementation(project(":glfw"))

    configureCommonTestDependencies()
}

kotlin {
    sourceSets["main"].apply {
        kotlin.srcDir("build/generated/ksp/main/kotlin/")
    }
}