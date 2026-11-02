package com.example.myiulms.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.util.Locale

/** Bump when a cached model changes shape; older files then read as a miss. */
const val CACHE_SCHEMA_VERSION = 1

/**
 * Wrapper stored around every cached payload. [ownerHash] ties the data to one
 * account, so a different login can detect and discard it; the raw username is
 * never stored.
 */
@Serializable
data class CacheEnvelope<T>(
    val schemaVersion: Int,
    val ownerHash: String,
    val fetchedAt: Long,
    val payload: T
)

// Registration numbers: case and surrounding spaces must not split one account in two.
fun ownerHashOf(username: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(username.trim().uppercase(Locale.ROOT).toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

private val CacheJson = Json { ignoreUnknownKeys = true }

fun <T> encodeEnvelope(payload: KSerializer<T>, envelope: CacheEnvelope<T>): ByteArray =
    CacheJson.encodeToString(CacheEnvelope.serializer(payload), envelope).toByteArray(Charsets.UTF_8)

/** Null for anything unreadable or written by a different schema version. */
fun <T> decodeEnvelope(payload: KSerializer<T>, bytes: ByteArray): CacheEnvelope<T>? =
    runCatching {
        CacheJson.decodeFromString(CacheEnvelope.serializer(payload), bytes.toString(Charsets.UTF_8))
    }.getOrNull()?.takeIf { it.schemaVersion == CACHE_SCHEMA_VERSION }
