package ephyra.data.release

import android.os.Build
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.json.Json
import ephyra.domain.release.interactor.GetApplicationRelease
import ephyra.domain.release.model.Release
import ephyra.domain.release.service.ReleaseService

class ReleaseServiceImpl(
    private val networkService: NetworkHelper,
    private val json: Json,
) : ReleaseService {

    override suspend fun latest(arguments: GetApplicationRelease.Arguments): Release? {
        val url = if (arguments.isNightly) {
            "https://api.github.com/repos/${arguments.repository}/releases/tags/nightly"
        } else {
            "https://api.github.com/repos/${arguments.repository}/releases/latest"
        }

        val release = with(json) {
            networkService.client
                .newCall(GET(url))
                .awaitSuccess()
                .parseAs<GithubRelease>()
        }

        val downloadLink = getDownloadLink(release = release, isFoss = arguments.isFoss) ?: return null

        // For nightly builds the tag is always "nightly", so use the short SHA extracted from
        // the asset filename as the version identifier for comparison.
        val version = if (arguments.isNightly) {
            extractNightlySha(release) ?: release.version
        } else {
            release.version
        }

        return Release(
            version = version,
            info = release.info.substringBeforeLast("<!-->").replace(gitHubUsernameMentionRegex) { mention ->
                "[${mention.value}](https://github.com/${mention.value.substring(1)})"
            },
            releaseLink = release.releaseLink,
            downloadLink = downloadLink,
        )
    }

    /**
     * Extracts the short git SHA from a nightly release asset filename.
     * Asset names follow the pattern: `app-<abi>-nightly-<sha>.apk`
     */
    private fun extractNightlySha(release: GithubRelease): String? {
        return release.assets
            .firstOrNull { it.name.endsWith(".apk") }
            ?.name
            ?.let { name -> Regex("nightly-([a-f0-9]+)\\.apk").find(name)?.groupValues?.get(1) }
    }

    private fun getDownloadLink(release: GithubRelease, isFoss: Boolean): String? {
        // Sort so that unsigned APKs are processed first; since associate keeps the last
        // value for duplicate keys, signed APKs will take priority over unsigned ones.
        val sortedAssets = release.assets.sortedBy { if ("unsigned" in it.name) 0 else 1 }
        val map = sortedAssets.associate { asset ->
            BUILD_TYPES.find { "-$it" in asset.name } to asset.downloadLink
        }

        return if (!isFoss) {
            map[Build.SUPPORTED_ABIS[0]] ?: map[null]
        } else {
            map[FOSS]
        }
    }

    companion object {
        private const val FOSS = "foss"
        private val BUILD_TYPES = listOf(FOSS, "arm64-v8a", "armeabi-v7a", "x86_64", "x86")

        /**
         * Regular expression that matches a mention to a valid GitHub username, like it's
         * done in GitHub Flavored Markdown. It follows these constraints:
         *
         * - Alphanumeric with single hyphens (no consecutive hyphens)
         * - Cannot begin or end with a hyphen
         * - Max length of 39 characters
         *
         * Reference: https://stackoverflow.com/a/30281147
         */
        private val gitHubUsernameMentionRegex = """\B@([a-z0-9](?:-(?=[a-z0-9])|[a-z0-9]){0,38}(?<=[a-z0-9]))"""
            .toRegex(RegexOption.IGNORE_CASE)
    }
}
