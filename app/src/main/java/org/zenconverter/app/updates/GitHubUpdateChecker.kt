package org.zenconverter.app.updates

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

object GitHubUpdateChecker {
    private const val RELEASES_BASE_URL =
        "https://api.github.com/repos/Jasonzhu1207/ZenConverter/releases"
    private const val STABLE_RELEASE_URL = "$RELEASES_BASE_URL/latest"
    private const val PREVIEW_RELEASE_URL = "$RELEASES_BASE_URL/tags/pre-release"
    private const val CONNECT_TIMEOUT_MILLIS = 15_000
    private const val READ_TIMEOUT_MILLIS = 20_000

    suspend fun check(
        channel: UpdateChannel,
        installedVersion: InstalledAppVersion
    ): UpdateCheckResult {
        return withContext(Dispatchers.IO) {
            try {
                val releaseJson = readReleaseJson(channel.releaseUrl)
                val release = parseRelease(releaseJson, channel)
                if (release.versionCode > installedVersion.versionCode) {
                    UpdateCheckResult.Available(release)
                } else {
                    UpdateCheckResult.UpToDate(release)
                }
            } catch (exception: UpdateCheckException) {
                UpdateCheckResult.Failed(exception.reason, exception.message)
            } catch (exception: IOException) {
                UpdateCheckResult.Failed(UpdateFailureReason.Network, exception.message)
            } catch (exception: JSONException) {
                UpdateCheckResult.Failed(UpdateFailureReason.InvalidResponse, exception.message)
            }
        }
    }

    private val UpdateChannel.releaseUrl: String
        get() = when (this) {
            UpdateChannel.Stable -> STABLE_RELEASE_URL
            UpdateChannel.Preview -> PREVIEW_RELEASE_URL
        }

    private fun readReleaseJson(url: String): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MILLIS
            readTimeout = READ_TIMEOUT_MILLIS
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "ZenConverter-Android")
            instanceFollowRedirects = true
        }

        return try {
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                throw UpdateCheckException(UpdateFailureReason.NoRelease)
            }
            if (responseCode !in 200..299) {
                throw IOException("GitHub returned HTTP $responseCode")
            }
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseRelease(json: JSONObject, channel: UpdateChannel): UpdateRelease {
        if (json.optBoolean("draft", false)) {
            throw UpdateCheckException(UpdateFailureReason.NoRelease)
        }

        val tagName = json.optString("tag_name").takeIf { it.isNotBlank() }
            ?: throw UpdateCheckException(UpdateFailureReason.InvalidResponse)
        val releasePageUrl = json.optString("html_url").takeIf { it.isNotBlank() }
            ?: throw UpdateCheckException(UpdateFailureReason.InvalidResponse)
        val body = json.optString("body").takeIf { it.isNotBlank() }
        val version = parseVersionFromNotes(body)
            ?: parseStableVersionFromTag(channel, tagName)
            ?: throw UpdateCheckException(UpdateFailureReason.MissingVersionMetadata)
        val asset = findApkAsset(json.optJSONArray("assets"))
            ?: throw UpdateCheckException(UpdateFailureReason.NoApkAsset)

        return UpdateRelease(
            channel = channel,
            versionName = version.first,
            versionCode = version.second,
            tagName = tagName,
            releasePageUrl = releasePageUrl,
            downloadUrl = asset.downloadUrl,
            assetName = asset.name,
            sizeBytes = asset.sizeBytes,
            sha256 = parseSha256FromNotes(body),
            publishedAt = json.optString("published_at").takeIf { it.isNotBlank() },
            notes = body
        )
    }

    private fun findApkAsset(assets: JSONArray?): ReleaseAsset? {
        if (assets == null) return null
        val parsedAssets = buildList {
            for (index in 0 until assets.length()) {
                assets.optJSONObject(index)?.let { asset ->
                    val name = asset.optString("name").takeIf { it.isNotBlank() } ?: return@let
                    val url = asset.optString("browser_download_url").takeIf { it.isNotBlank() }
                        ?: return@let
                    val size = asset.optLong("size", -1L).takeIf { it > 0L }
                    add(ReleaseAsset(name = name, downloadUrl = url, sizeBytes = size))
                }
            }
        }

        return parsedAssets.firstOrNull { asset ->
            asset.name.endsWith(".apk", ignoreCase = true) &&
                asset.name.contains("arm64-v8a", ignoreCase = true)
        } ?: parsedAssets.firstOrNull { asset ->
            asset.name.endsWith(".apk", ignoreCase = true)
        }
    }

    private fun parseVersionFromNotes(notes: String?): Pair<String, Long>? {
        if (notes == null) return null
        val match = ANDROID_VERSION_REGEX.find(notes) ?: return null
        val versionName = match.groupValues[1]
        val versionCode = match.groupValues[2].toLongOrNull() ?: return null
        return versionName to versionCode
    }

    private fun parseStableVersionFromTag(
        channel: UpdateChannel,
        tagName: String
    ): Pair<String, Long>? {
        if (channel != UpdateChannel.Stable) return null
        val match = SEMVER_TAG_REGEX.matchEntire(tagName) ?: return null
        val major = match.groupValues[1].toLongOrNull() ?: return null
        val minor = match.groupValues[2].toLongOrNull() ?: return null
        val patch = match.groupValues[3].toLongOrNull() ?: return null
        val versionName = "$major.$minor.$patch"
        val versionBase = major * 100_000_000L + minor * 1_000_000L + patch * 10_000L
        return versionName to (versionBase + 1L)
    }

    private fun parseSha256FromNotes(notes: String?): String? {
        if (notes == null) return null
        return SHA256_REGEX.find(notes)
            ?.groupValues
            ?.getOrNull(1)
            ?.lowercase(Locale.US)
    }

    private data class ReleaseAsset(
        val name: String,
        val downloadUrl: String,
        val sizeBytes: Long?
    )

    private class UpdateCheckException(
        val reason: UpdateFailureReason,
        message: String? = null
    ) : Exception(message)

    private val ANDROID_VERSION_REGEX =
        Regex("""Android version:\s*([0-9]+(?:\.[0-9]+){2})\s*\((\d+)\)""")
    private val SEMVER_TAG_REGEX = Regex("""v([0-9]+)\.([0-9]+)\.([0-9]+)""")
    private val SHA256_REGEX =
        Regex("""(?:APK SHA-256|SHA-256):\s*([A-Fa-f0-9]{64})""", RegexOption.IGNORE_CASE)
}
