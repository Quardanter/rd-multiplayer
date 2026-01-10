plugins {
    id("java-library")
}

group = "net.minecraft"
version = "rd-132211"

repositories {
    mavenCentral()
}

val natives: Configuration by configurations.creating
natives.isTransitive = true

dependencies {
    implementation(group = "org.lwjgl.lwjgl", name = "lwjgl", version = "2.9.3")
    implementation(group = "org.lwjgl.lwjgl", name = "lwjgl_util", version = "2.9.3")
    natives(group = "org.lwjgl.lwjgl", name = "lwjgl-platform", version = "2.9.3", classifier = "natives-windows")
    natives(group = "org.lwjgl.lwjgl", name = "lwjgl-platform", version = "2.9.3", classifier = "natives-linux")
    natives(group = "org.lwjgl.lwjgl", name = "lwjgl-platform", version = "2.9.3", classifier = "natives-osx")
}


tasks.register<JavaExec>("runClient") {
    jvmArgs = listOf("-Dorg.lwjgl.librarypath=${project.projectDir.toPath()}\\run\\natives")
    mainClass.set("client.Minecraft")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = file("${project.projectDir.toPath()}\\run")
    dependsOn("extractNatives")
}

tasks.register<JavaExec>("runServer") {
    jvmArgs = listOf("-Dorg.lwjgl.librarypath=${project.projectDir.toPath()}\\run\\natives")
    mainClass.set("server.Server")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = file("${project.projectDir.toPath()}\\run")
    dependsOn("extractNatives")
}

task("extractNatives", Copy::class) {
    dependsOn(natives)
    from(natives.map { zipTree(it) })
    into("${project.projectDir.toPath()}\\run\\natives")
}

val gitCommitHash: String by extra {
    val (hashOutput, _) = "git rev-parse HEAD".runCommand()
    val hash = hashOutput.trim().take(10)

    val isDirty = listOf(
        "git diff --quiet --ignore-submodules".runCommand(),
        "git diff --cached --quiet".runCommand()
    ).any { (_, code) -> code != 0 }

    if (isDirty) "$hash+" else hash
}

fun String.runCommand(): Pair<String, Int> {
    return try {
        val parts = this.split("\\s".toRegex())
        val proc = ProcessBuilder(*parts.toTypedArray())
            .directory(rootDir)
            .redirectErrorStream(true)
            .start()
        val output = proc.inputStream.bufferedReader().readText()
        val exitCode = proc.waitFor()
        output to exitCode
    } catch (e: Exception) {
        "" to -1
    }
}


tasks.register("generateGitHash") {
    doLast {
        val file = file("src/main/resources/git.properties")
        file.writeText("git.commit=$gitCommitHash")
    }
}

tasks.named("processResources") {
    dependsOn("generateGitHash")
}
