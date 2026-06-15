import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

dependencies {
    implementation("tools.jackson.core:jackson-databind")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-websocket")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

val copyFrontend by tasks.registering(Copy::class) {
    dependsOn(rootProject.tasks.named("frontendBuild"))
    dependsOn(tasks.named("processResources"))
    from(rootProject.layout.projectDirectory.dir("frontend/dist"))
    into(layout.buildDirectory.dir("resources/main/static"))
}

tasks.named("classes") {
    dependsOn(copyFrontend)
}

tasks.named<BootJar>("bootJar") {
    dependsOn(copyFrontend)
}
