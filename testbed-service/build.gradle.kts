plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(platform(libs.spring.boot.dependencies))
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.aop)
    implementation(libs.jackson.module.kotlin.managed)
    implementation(libs.kotlin.reflect)
    implementation(libs.resilience4j.spring.boot3)

    testImplementation(kotlin("test"))
    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly(libs.junit.platform.launcher)
    // The Spring contract test compares RetryLint's resolver with the real Resilience4j registry.
    testImplementation(project(":retrylint-cli"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    enabled = false
}
