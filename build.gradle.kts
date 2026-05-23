plugins {
    java
    id("org.springframework.boot") version "3.3.5"
    id("io.spring.dependency-management") version "1.1.6"
    jacoco
}

group = "io.github.baekchangjoon"
version = "0.1.0-SNAPSHOT"
description = "Hotspot analysis CLI for Java codebases"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

val picocliVersion = "4.7.6"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // CLI
    implementation("info.picocli:picocli-spring-boot-starter:$picocliVersion")
    annotationProcessor("info.picocli:picocli-codegen:$picocliVersion")

    // YAML
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // Git
    implementation("org.eclipse.jgit:org.eclipse.jgit:6.10.0.202406032230-r")

    // GitHub
    implementation("org.kohsuke:github-api:1.326")

    // Java AST
    implementation("com.github.javaparser:javaparser-symbol-solver-core:3.26.2")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.assertj:assertj-core")
    testImplementation("org.wiremock:wiremock-standalone:3.10.0")
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-Aproject=${project.group}/${project.name}")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
    }
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        csv.required.set(true)
        html.required.set(true)
    }
}

tasks.bootJar {
    archiveBaseName.set("hotspot")
    archiveClassifier.set("")
    mainClass.set("io.github.baekchangjoon.hotspotanalysis.HotspotApplication")
}

tasks.jar {
    enabled = false
}
