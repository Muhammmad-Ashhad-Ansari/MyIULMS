package com.example.myiulms.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypted file store for cached portal data.
 *
 * - Lives in noBackupFilesDir, which Android never puts in cloud backup or
 *   device transfer.
 * - Each file is AES-256-GCM under a non-exportable Android Keystore key, so a
 *   copied file is unreadable anywhere else.
 * - Writes go through AtomicFile: a crash mid-write leaves the old file intact.
 * - Any file that will not decrypt (corrupt, restored, key gone) is deleted and
 *   treated as a miss, never a crash.
 *
 * Blocking I/O: call off the main thread except for [wipe], which is cheap.
 */
class PortalCache(context: Context) {
    private val dir = File(context.applicationContext.noBackupFilesDir, "portal-cache")
    private val lock = Any()

    fun read(name: String): ByteArray? = synchronized(lock) {
        val file = File(dir, name)
        if (!file.exists()) return null
        try {
            decrypt(AtomicFile(file).readFully())
        } catch (_: Exception) {
            AtomicFile(file).delete()
            null
        }
    }

    fun write(name: String, plain: ByteArray) = synchronized(lock) {
        dir.mkdirs()
        val file = AtomicFile(File(dir, name))
        val out = file.startWrite()
        try {
            out.write(encrypt(plain))
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
            throw e
        }
    }

    /** Deletes every cached file and the key, so anything missed is unreadable. */
    fun wipe() = synchronized(lock) {
        dir.deleteRecursively()
        runCatching { keyStore().deleteEntry(KEY_ALIAS) }
        Unit
    }

    // Layout: [format version][12-byte IV][ciphertext + 16-byte GCM tag].
    private fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, existingKey() ?: generateKey())
        val iv = cipher.iv
        check(iv.size == IV_BYTES)
        return byteArrayOf(FORMAT_VERSION) + iv + cipher.doFinal(plain)
    }

    private fun decrypt(data: ByteArray): ByteArray {
        require(data.size > HEADER_BYTES && data[0] == FORMAT_VERSION)
        val key = existingKey() ?: error("Cache key missing")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, data, 1, IV_BYTES))
        return cipher.doFinal(data, HEADER_BYTES, data.size - HEADER_BYTES)
    }

    private fun existingKey(): SecretKey? = keyStore().getKey(KEY_ALIAS, null) as? SecretKey

    private fun generateKey(): SecretKey =
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            generateKey()
        }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "portal_cache_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val FORMAT_VERSION: Byte = 1
        const val IV_BYTES = 12
        const val TAG_BITS = 128
        const val HEADER_BYTES = 1 + IV_BYTES
    }
}
