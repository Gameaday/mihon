package ephyra.buildlogic.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import java.io.File

abstract class LocalesConfigTask : DefaultTask() {

    @get:InputDirectory
    abstract val mokoResourcesDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    private val emptyResourcesElement = "<resources>\\s*</resources>|<resources\\s*/>".toRegex()

    @TaskAction
    fun generate() {
        val locales = mokoResourcesDir.get().asFileTree
            .matching { include("**/strings.xml") }
            .filterNot { it.readText().contains(emptyResourcesElement) }
            .map {
                it.parentFile.name
                    .replace("base", "en")
                    .replace("-r", "-")
                    .replace("+", "-")
            }
            .sorted()
            .joinToString("\n") { "|   <locale android:name=\"$it\"/>" }

        val content = """
        |<?xml version="1.0" encoding="utf-8"?>
        |<locale-config xmlns:android="http://schemas.android.com/apk/res/android">
        $locales
        |</locale-config>
        """.trimMargin()

        val outputFile = outputDir.file("xml/locales_config.xml").get().asFile
        outputFile.apply {
            parentFile.mkdirs()
            writeText(content)
        }
    }
}

fun Project.getLocalesConfigTask(outputResourceDir: File): TaskProvider<LocalesConfigTask> {
    return tasks.register("generateLocalesConfig", LocalesConfigTask::class.java) {
        mokoResourcesDir.set(file("$projectDir/src/commonMain/moko-resources/"))
        outputDir.set(outputResourceDir)
    }
}
