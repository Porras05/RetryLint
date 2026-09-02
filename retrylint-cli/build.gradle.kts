plugins {
    kotlin("jvm")
    application
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation("info.picocli:picocli:4.7.7")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.3")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.18.3")

    testImplementation(kotlin("test"))
    testImplementation("io.github.resilience4j:resilience4j-retry:2.3.0")
    testImplementation("io.github.resilience4j:resilience4j-timelimiter:2.3.0")
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("retrylint.cli.MainKt")
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}
