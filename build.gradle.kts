plugins {
    kotlin("jvm") version "2.1.21" apply false
    kotlin("plugin.spring") version "2.1.21" apply false
    id("org.springframework.boot") version "3.5.0" apply false
}

allprojects {
    group = "io.retrylint"
    version = "0.1.0"

    repositories {
        mavenCentral()
    }
}
