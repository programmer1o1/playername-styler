plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "v1_21_4"

val versionProjects = listOf(
    "v1_21_1",
    "v1_21_4",
    "v1_21_6",
    "v1_21_8",
    "v1_21_9",
    "v1_21_10",
    "v1_21_11",
)

tasks.register("buildAllVersions") {
    group = "build"
    description = "Builds all supported Minecraft versions."
    dependsOn(versionProjects.map { ":$it:build" })
}

tasks.register("collectAllJars") {
    group = "build"
    description = "Builds all version jars and collects them into ./all-jars/"
    // Uses dynamic project/task inspection and file copying; keep builds green when configuration cache is enabled.
    notCompatibleWithConfigurationCache("Aggregates artifacts from subprojects into a single folder.")

    dependsOn(versionProjects.map { ":$it:jar" })
    dependsOn(versionProjects.map { ":$it:sourcesJar" })

    doLast {
        val modId = providers.gradleProperty("mod_id").orNull ?: "playernamestyler"
        val modVersion = providers.gradleProperty("mod_version").orNull ?: "0.0.0"

        val outDir = rootProject.file("all-jars")
        delete(outDir)
        outDir.mkdirs()

        versionProjects.forEach { projName ->
            val p = rootProject.project(":$projName")
            val mc = (p.findProperty("minecraft_version") ?: projName).toString()

            listOf("jar", "sourcesJar").forEach { taskName ->
                val t = p.tasks.findByName(taskName) as? org.gradle.jvm.tasks.Jar ?: return@forEach
                val classifier = t.archiveClassifier.orNull
                val ext = t.archiveExtension.orNull ?: "jar"
                val outName = buildString {
                    append(modId).append("-").append(modVersion).append("-mc").append(mc)
                    if (!classifier.isNullOrBlank()) append("-").append(classifier)
                    append(".").append(ext)
                }

                copy {
                    from(t.archiveFile)
                    into(outDir)
                    rename { outName }
                }
            }
        }
    }
}
