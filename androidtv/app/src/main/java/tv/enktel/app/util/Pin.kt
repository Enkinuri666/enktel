package tv.enktel.app.util

import java.security.MessageDigest

/** Local-only PIN storage: SHA-256 with a fixed salt. Sufficient for a parental gate. */
object Pin {
    private const val SALT = "enktel-parental-v1"

    fun hash(raw: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest((SALT + raw).toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun matches(raw: String, storedHash: String): Boolean {
        if (storedHash.isBlank()) return true
        return hash(raw) == storedHash
    }
}

/** Once the PIN is entered correctly, locked categories stay open until the app restarts. */
object UnlockSession {
    var unlocked: Boolean = false
}
