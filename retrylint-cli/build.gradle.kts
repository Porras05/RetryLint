plugins {
    kotlin("jvm")
    application
    jacoco
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
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
    classDirectories.setFrom(
        files(classDirectories.files.map { directory ->
            fileTree(directory) {
                include("retrylint/config/**", "retrylint/graph/**", "retrylint/rules/**")
            }
        }),
    )
}

application {
    mainClass.set("retrylint.cli.MainKt")
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}
