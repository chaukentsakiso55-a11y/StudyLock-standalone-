package com.cyberpulse.studylock

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object ParentPasswordStore {
    private const val PREFERENCES = "studylock_parent_security"
    private const val PASSWORD_RECORD = "password_record"

    fun hasPassword(context: Context): Boolean =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(PASSWORD_RECORD, null)
            ?.isNotBlank() == true

    fun save(context: Context, password: String): Boolean {
        if (password.length !in 4..128) return false
        val record = runCatching { PasswordHasher.createRecord(password) }.getOrNull()
            ?: return false
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(PASSWORD_RECORD, record)
            .commit()
    }

    fun verify(context: Context, password: String): Boolean {
        val record = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(PASSWORD_RECORD, null)
            ?: return false
        return PasswordHasher.verify(password, record)
    }
}

object PasswordHasher {
    private const val VERSION = "v1"
    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16

    fun createRecord(password: String): String {
        val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
        val hash = derive(password, salt, ITERATIONS)
        return listOf(
            VERSION,
            ITERATIONS.toString(),
            Base64.getEncoder().encodeToString(salt),
            Base64.getEncoder().encodeToString(hash)
        ).joinToString(":")
    }

    fun verify(password: String, record: String): Boolean {
        val parts = record.split(':')
        if (parts.size != 4 || parts[0] != VERSION) return false
        val iterations = parts[1].toIntOrNull() ?: return false
        if (iterations !in 10_000..1_000_000) return false
        return runCatching {
            val salt = Base64.getDecoder().decode(parts[2])
            val expected = Base64.getDecoder().decode(parts[3])
            val actual = derive(password, salt, iterations)
            MessageDigest.isEqual(expected, actual)
        }.getOrDefault(false)
    }

    private fun derive(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded
        } finally {
            spec.clearPassword()
        }
    }
}
