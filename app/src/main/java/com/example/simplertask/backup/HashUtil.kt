package com.example.simplertask.backup

import java.security.MessageDigest

internal object HashUtil {
    private val digest = MessageDigest.getInstance("SHA-256")

    fun sha256(input: String): String {
        val bytes = synchronized(digest) { digest.digest(input.toByteArray()) }
        return bytes.joinToString("") { b -> (b.toInt() and 0xff).toString(16).padStart(2, '0') }
    }
}
