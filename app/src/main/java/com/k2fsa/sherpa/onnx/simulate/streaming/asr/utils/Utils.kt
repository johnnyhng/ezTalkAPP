package com.k2fsa.sherpa.onnx.simulate.streaming.asr.utils

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * Computes the SHA-256 hash of a string.
 *
 * @param input The string to hash.
 * @return The SHA-256 hash as a hexadecimal string.
 */
fun sha256(input: String): String {
    val bytes = input.toByteArray()
    val md = MessageDigest.getInstance("SHA-256")
    val digest = md.digest(bytes)
    return digest.fold("") { str, it -> str + "%02x".format(it) }
}

/**
 * Computes the SHA-256 hash of a file.
 *
 * @param file The file to hash.
 * @return The SHA-256 hash as a hexadecimal string, or null on error.
 */
fun sha256(file: File): String? {
    return try {
        val md = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(1024)
            var a: Int
            while (fis.read(buffer).also { a = it } != -1) {
                md.update(buffer, 0, a)
            }
        }
        val digest = md.digest()
        digest.fold("") { str, it -> str + "%02x".format(it) }
    } catch (e: Exception) {
        null
    }
}
