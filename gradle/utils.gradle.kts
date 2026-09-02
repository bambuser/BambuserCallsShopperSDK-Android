// Shared version helper. Mirrors the pattern used by
// bambuser-commerce-sdk-android — the SDK's build.gradle.kts reads
// `currentBuildVersion` and `currentGitBranch` from the extra
// properties block set here.
//
// Bump the version by editing `getBuildVersion()` below.

import java.util.concurrent.TimeUnit

extra.apply {
    set("currentGitBranch", getGitBranch())
    set("currentBuildVersion", getBuildVersion())
}

fun getGitBranch(): String {
    return try {
        "git rev-parse --abbrev-ref HEAD".runCommand(workingDir = rootDir)
    } catch (t: Throwable) {
        "unknown"
    }
}

fun getBuildVersion(): String {
    val versionMajor = 0
    val versionMinor = 3
    val versionPatch = 0
    return "$versionMajor.$versionMinor.$versionPatch"
}

fun String.runCommand(
    workingDir: File = File("."),
    timeoutAmount: Long = 60,
    timeoutUnit: TimeUnit = TimeUnit.SECONDS
): String = ProcessBuilder(split("\\s(?=(?:[^'\"`]*(['\"`])[^'\"`]*\\1)*[^'\"`]*$)".toRegex()))
    .directory(workingDir)
    .redirectOutput(ProcessBuilder.Redirect.PIPE)
    .redirectError(ProcessBuilder.Redirect.PIPE)
    .start()
    .apply { waitFor(timeoutAmount, timeoutUnit) }
    .run {
        val err = errorStream.bufferedReader().readText().trim()
        if (err.isNotEmpty()) {
            throw java.io.IOException(err)
        }
        inputStream.bufferedReader().readText().trim()
    }
