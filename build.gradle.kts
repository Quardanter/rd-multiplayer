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
    val nativesDir = project.layout.projectDirectory.dir("run/natives")
    jvmArgs = listOf("-Dorg.lwjgl.librarypath=${nativesDir.asFile.absolutePath}")
    mainClass.set("client.Launcher")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = project.layout.projectDirectory.dir("run").asFile
    dependsOn("extractNatives")
}

tasks.register<JavaExec>("runServer") {
    mainClass.set("server.Server")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = project.layout.projectDirectory.dir("run").asFile
}

task("extractNatives", Copy::class) {
    dependsOn(natives)
    from(natives.map { zipTree(it) })
    into(project.layout.projectDirectory.dir("run/natives").asFile)
}

tasks.register<Jar>("buildServer") {
    group = "build"
    dependsOn("classes", "generateGitHash")
    archiveBaseName.set("rd-server")
    archiveVersion.set(version.toString())
    archiveClassifier.set("")
    destinationDirectory.set(project.layout.projectDirectory.dir("build/dist").asFile)
    manifest {
        attributes("Main-Class" to "server.Server", "Implementation-Version" to gitCommitHash)
    }
    from(sourceSets["main"].output)
    from(
        configurations.runtimeClasspath.get()
            .filter { !it.name.contains("lwjgl") }
            .map { if (it.isDirectory) it else zipTree(it) }
    ) {
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/MANIFEST.MF")
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.register<Jar>("buildClient") {
    group = "build"
    dependsOn("classes", "generateGitHash")
    archiveBaseName.set("rd-client")
    archiveVersion.set(version.toString())
    archiveClassifier.set("")
    destinationDirectory.set(project.layout.projectDirectory.dir("build/dist").asFile)
    manifest {
        attributes("Main-Class" to "client.Launcher", "Implementation-Version" to gitCommitHash)
    }
    from(sourceSets["main"].output)
    from(
        configurations.runtimeClasspath.get()
            .filter { !it.name.contains("lwjgl-platform") }
            .map { if (it.isDirectory) it else zipTree(it) }
    ) {
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/MANIFEST.MF")
    }
    from(natives.map { zipTree(it) }) {
        into("natives")
        include("*.dll", "*.so", "*.jnilib", "*.dylib")
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
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