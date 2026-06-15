plugins {
    base
    id("org.springframework.boot") version "4.0.6" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

allprojects {
    group = "com.scrumpokinggame"
    version = "0.1.0"
}

val npmExecutable = if (file("/opt/homebrew/bin/npm").exists()) "/opt/homebrew/bin/npm" else "npm"

val frontendInstall by tasks.registering(Exec::class) {
    workingDir = file("frontend")
    commandLine(npmExecutable, "install")
}

val frontendBuild by tasks.registering(Exec::class) {
    dependsOn(frontendInstall)
    workingDir = file("frontend")
    commandLine(npmExecutable, "run", "build")
}

val frontendDev by tasks.registering(Exec::class) {
    workingDir = file("frontend")
    commandLine(npmExecutable, "run", "dev")
}

tasks.named("build") {
    dependsOn(":backend:build", frontendBuild)
}

tasks.register("bootRun") {
    dependsOn(":backend:bootRun")
}

tasks.register("frontendCheck") {
    dependsOn(frontendBuild)
}
