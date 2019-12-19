plugins {
    kotlin("jvm")
}

group = "de.hanno.hpengine"

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(project(path = ":engine", configuration = "default"))
    implementation("org.drjekyll:colorpicker:1.3")
}

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_1_8
}
