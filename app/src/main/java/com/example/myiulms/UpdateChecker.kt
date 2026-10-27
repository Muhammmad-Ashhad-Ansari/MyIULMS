package com.example.myiulms

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

internal data class LatestAppRelease(
    val version: String,
    val releaseUrl: String,
    // Release ka markdown body — usi response se jo pehle se aata hai (koi extra
    // request nahi; shared-IP rate limit ke liye zaroori, AI_HANDOFF §8).
    val notes: String = ""
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
            val notes = release.optString("body").trim()

            if (version.isBlank() || !releaseUrl.startsWith(PUBLIC_RELEASE_PREFIX)) {
                throw IOException("GitHub returned invalid release details")
            }

            return LatestAppRelease(version = version, releaseUrl = releaseUrl, notes = notes)
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

// ---------------- Release notes preview (pure, unit-tested) ----------------

internal data class ReleaseNotesPreview(val items: List<String>, val remaining: Int)

// Release body (markdown) → chhoti "What's new" list. Hamare release notes ka
// format: `## What's new` ke neeche bullets, har bullet `**Title** — detail`.
// Sirf title dikhate hain (dialog mein jagah kam); "What's new" section na mile to
// poore body ke bullets — magar `Notes`/checksum jaise sections chhod kar.
internal fun releaseNotesPreview(body: String, max: Int = 5): ReleaseNotesPreview {
    val lines = body.lines()
    val headingIndexes = lines.indices.filter { lines[it].trimStart().startsWith("#") }
    val whatsNew = headingIndexes.firstOrNull { index ->
        val heading = lines[index].lowercase()
        "new" in heading || "change" in heading || "highlight" in heading
    }
    val bulletLines = if (whatsNew != null) {
        val end = headingIndexes.firstOrNull { it > whatsNew } ?: lines.size
        lines.subList(whatsNew + 1, end)
    } else {
        var skipping = false
        lines.filter { line ->
            val trimmed = line.trimStart()
            if (trimmed.startsWith("#")) {
                val heading = trimmed.lowercase()
                skipping = "note" in heading || "checksum" in heading || "sha" in heading
                false
            } else {
                !skipping
            }
        }
    }
    val items = bulletLines
        .map { it.trim() }
        .filter { it.startsWith("- ") || it.startsWith("* ") || it.matches(Regex("""\d+[.)] .*""")) }
        .map { cleanReleaseNoteItem(it) }
        .filter { it.isNotBlank() }
    return ReleaseNotesPreview(items.take(max), (items.size - max).coerceAtLeast(0))
}

private fun cleanReleaseNoteItem(line: String): String {
    val text = line.replaceFirst(Regex("""^([-*]|\d+[.)])\s+"""), "")
    // `**Title** — detail` → sirf Title.
    Regex("""^\*\*(.+?)\*\*""").find(text)?.let { return it.groupValues[1].trim() }
    val plain = text
        .replace(Regex("""\[(.+?)]\(.+?\)"""), "$1") // [text](url) → text
        .replace("**", "")
        .replace("`", "")
        .trim()
    return if (plain.length <= 80) plain else plain.take(77).trimEnd() + "…"
}
