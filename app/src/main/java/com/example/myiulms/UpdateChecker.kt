package com.example.myiulms

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

internal data class LatestAppRelease(
    val version: String,
    val releaseUrl: String
)

internal sealed interface UpdateCheckState {
    data object Checking : UpdateCheckState
    data class Available(val currentVersion: String, val latest: LatestAppRelease) : UpdateCheckState
    data class UpToDate(val currentVersion: String) : UpdateCheckState
    data class Failed(val message: String) : UpdateCheckState
}

private object GitHubReleaseClient {
    private const val RELEASE_URL =
        "https://api.github.com/repos/Muhammmad-Ashhad-Ansari/MyIULMS/releases/latest"
    private const val PUBLIC_RELEASE_PREFIX =
        "https://github.com/Muhammmad-Ashhad-Ansari/MyIULMS/releases/"

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS)
        .build()

    fun fetchLatest(): LatestAppRelease {
        val request = Request.Builder()
            .url(RELEASE_URL)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "MyIULMS-Android")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("GitHub returned HTTP ${response.code}")
            }

            val body = response.body?.string()
                ?: throw IOException("GitHub returned an empty response")
            val release = JSONObject(body)
            val version = release.optString("tag_name").trim()
            val releaseUrl = release.optString("html_url").trim()

            if (version.isBlank() || !releaseUrl.startsWith(PUBLIC_RELEASE_PREFIX)) {
                throw IOException("GitHub returned invalid release details")
            }

            return LatestAppRelease(version = version, releaseUrl = releaseUrl)
        }
    }
}

internal fun fetchLatestAppRelease(): LatestAppRelease = GitHubReleaseClient.fetchLatest()

internal fun isVersionNewer(latest: String, current: String): Boolean {
    val latestParts = versionParts(latest)
    val currentParts = versionParts(current)
    val size = maxOf(latestParts.size, currentParts.size)

    for (index in 0 until size) {
        val latestPart = latestParts.getOrElse(index) { 0 }
        val currentPart = currentParts.getOrElse(index) { 0 }
        if (latestPart != currentPart) return latestPart > currentPart
    }
    return false
}

private fun versionParts(version: String): List<Int> = version
    .trim()
    .removePrefix("v")
    .removePrefix("V")
    .split('.')
    .map { part -> part.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
