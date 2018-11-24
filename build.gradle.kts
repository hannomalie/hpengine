version = "1.0.0-SNAPSHOT"

buildscript {

    val kotlin_version by extra { "1.3.10" }//{ "1.1.3-2" }

    repositories {
        mavenLocal()
        mavenCentral()
    }

    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin_version")
    }
}

allprojects {
    group = "de.hanno.hpengine"
}