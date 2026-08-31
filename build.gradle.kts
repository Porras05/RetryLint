plugins {
    kotlin("jvm") version "2.1.21" apply false
}

allprojects {
    group = "io.retrylint"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}