package io.github.teamomuito.colony.sim

/**
 * Decides whether a published release is newer than the installed build. Releases are tagged `build-<number>`, where the
 * number is the CI run number, which is also the app's version code.
 */
object UpdatePolicy {
    private val TAG = Regex("build-(\\d+)")

    /** The build number a release tag names, or null when the tag is not one of ours. */
    fun buildOf(tag: String): Int? = TAG.matchEntire(tag.trim())?.groupValues?.get(1)?.toIntOrNull()

    /** True only for a release with a higher build number than the installed one. Never offers a downgrade. */
    fun isNewer(tag: String, installedBuild: Int): Boolean = buildOf(tag)?.let { it > installedBuild } ?: false
}
