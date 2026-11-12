plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.spring) apply false
    alias(libs.plugins.spring.boot) apply false
}

allprojects {
    group = "io.retrylint"
    version = "0.1.0"

    repositories {
        mavenCentral()
    }
}
