plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.spring") version "2.3.21"
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.jlleitschuh.gradle.ktlint") version "13.1.0"
    jacoco
}

group = "com.ingsis"
version = "0.0.1-SNAPSHOT"
description = "Valida, ejecuta, formatea y lintea código; no sabe de snippets ni de usuarios"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
    // La librería del lenguaje se publica en GitHub Packages, que pide autenticarse aunque el repo sea público.
    // En CI alcanza con el GITHUB_TOKEN; en local, gpr.user y gpr.key en ~/.gradle/gradle.properties.
    maven {
        name = "Printscript2026"
        url = uri("https://maven.pkg.github.com/Ingsis-2026/Printscript2026")
        credentials {
            username = System.getenv("GITHUB_ACTOR") ?: project.findProperty("gpr.user") as String?
            password = System.getenv("GITHUB_TOKEN") ?: project.findProperty("gpr.key") as String?
        }
    }
}

val printscriptVersion = "1.1.0"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("tools.jackson.module:jackson-module-kotlin")
    listOf("commons", "lexer", "parser", "interpreter", "formatter", "linter").forEach {
        implementation("com.github.ingsis-2026:$it:$printscriptVersion")
    }

    testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
    testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

ktlint {
    version.set("1.5.0")
    verbose.set(true)
    outputToConsole.set(true)
}

// Un solo jar con nombre fijo: el Dockerfile lo copia sin adivinar la versión.
tasks.bootJar {
    archiveFileName.set("app.jar")
}

tasks.jar {
    enabled = false
}

tasks.withType<Test> {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

// Solo se excluye el main() que genera Spring. Todo lo demás cuenta para la cobertura:
// excluir controllers, services o connectors deja al 80% midiendo casi nada.
val coverageExclusions = listOf("**/*ApplicationKt.class")

tasks.jacocoTestReport {
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
    classDirectories.setFrom(classDirectories.files.map { fileTree(it) { exclude(coverageExclusions) } })
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.jacocoTestReport)
    violationRules {
        rule {
            limit {
                minimum = "0.80".toBigDecimal()
            }
        }
    }
    classDirectories.setFrom(classDirectories.files.map { fileTree(it) { exclude(coverageExclusions) } })
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}
