plugins {
    kotlin("jvm")
    application
    jacoco
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.picocli)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.jackson.dataformat.yaml)

    testImplementation(kotlin("test"))
    testImplementation(platform(libs.junit.bom))
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.resilience4j.retry)
    testImplementation(libs.resilience4j.timelimiter)
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

/** Coverage is measured on the analysis core only (plan section 12.4); CLI wiring is excluded. */
val coverageClassDirectories = files(
    sourceSets["main"].output.classesDirs.files.map { directory ->
        fileTree(directory) {
            include("retrylint/config/**", "retrylint/graph/**", "retrylint/rules/**")
        }
    },
)

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(true)
    }
    classDirectories.setFrom(coverageClassDirectories)
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    classDirectories.setFrom(coverageClassDirectories)
    violationRules {
        rule {
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

tasks.register<Copy>("archiveCoverage") {
    group = "verification"
    description = "Copy the JaCoCo XML/HTML/CSV report into evaluation/coverage as checked-in evidence."
    dependsOn(tasks.jacocoTestReport)
    from(layout.buildDirectory.dir("reports/jacoco/test"))
    into(rootProject.layout.projectDirectory.dir("evaluation/coverage/retrylint-cli"))
}

application {
    mainClass.set("retrylint.cli.MainKt")
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}

tasks.register<JavaExec>("week9Benchmark") {
    group = "verification"
    description = "Run the checked-in Week 9 mutation benchmark."
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("retrylint.evaluation.Week9BenchmarkMainKt")
    workingDir = rootProject.projectDir
    args(rootProject.layout.projectDirectory.dir("evaluation/week9").asFile.absolutePath)
}

tasks.register<JavaExec>("week10Scalability") {
    group = "verification"
    description = "Run the Week 10 frozen-analyzer scalability evaluation."
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("retrylint.evaluation.Week10ScalabilityMainKt")
    javaLauncher.set(
        javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(21))
        },
    )
    workingDir = rootProject.projectDir
    args(rootProject.layout.projectDirectory.dir("evaluation/week10").asFile.absolutePath)
}
