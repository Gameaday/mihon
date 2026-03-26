package ephyra.buildlogic

import org.gradle.api.Project
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.process.ExecOperations
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject

interface CommandValueSourceParameters : ValueSourceParameters {
    val command: org.gradle.api.provider.ListProperty<String>
}

abstract class CommandValueSource : ValueSource<String, CommandValueSourceParameters> {
    @get:Inject
    abstract val execOperations: ExecOperations

    override fun obtain(): String {
        val output = java.io.ByteArrayOutputStream()
        execOperations.exec {
            commandLine = parameters.command.get()
            standardOutput = output
        }
        return output.toString().trim()
    }
}

// Git is needed in your system PATH for these commands to work.
// If it's not installed, you can return a random value as a workaround
fun Project.getCommitCount(): String {
    return runCommand("git rev-list --count HEAD")
}

fun Project.getGitSha(): String {
    return runCommand("git rev-parse --short HEAD")
}

private val BUILD_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")

/**
 * @param useLastCommitTime If `true`, the build time is based on the timestamp of the last Git commit;
 *                          otherwise, the current time is used. Both are in UTC.
 * @return A formatted string representing the build time. The format used is defined by [BUILD_TIME_FORMATTER].
 */
fun Project.getBuildTime(useLastCommitTime: Boolean): String {
    return if (useLastCommitTime) {
        val epoch = runCommand("git log -1 --format=%ct").toLong()
        Instant.ofEpochSecond(epoch).atOffset(ZoneOffset.UTC).format(BUILD_TIME_FORMATTER)
    } else {
        Instant.now().atOffset(ZoneOffset.UTC).format(BUILD_TIME_FORMATTER)
    }
}

private fun Project.runCommand(command: String): String {
    return providers.of(CommandValueSource::class.java) {
        parameters.command.set(command.split(" "))
    }.get()
}
