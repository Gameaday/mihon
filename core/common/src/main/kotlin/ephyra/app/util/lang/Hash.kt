package ephyra.core.common.util.lang

import java.io.InputStream
import java.security.MessageDigest

object Hash {

    private val chars = charArrayOf(
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        'a', 'b', 'c', 'd', 'e', 'f',
    )

    private val MD5 get() = MessageDigest.getInstance("MD5")

    private val SHA256 get() = MessageDigest.getInstance("SHA-256")

    fun sha256(bytes: ByteArray): String {
        return encodeHex(SHA256.digest(bytes))
    }

    fun sha256(string: String): String {
        return sha256(string.toByteArray())
    }

    /**
     * Computes the SHA-256 hash of [stream] by reading it in 8 KB chunks.
     * The stream is **not** closed by this function.
     */
    fun sha256(stream: InputStream): String {
        val digest = SHA256
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var bytesRead = stream.read(buffer)
        while (bytesRead != -1) {
            digest.update(buffer, 0, bytesRead)
            bytesRead = stream.read(buffer)
        }
        return encodeHex(digest.digest())
    }

    fun md5(bytes: ByteArray): String {
        return encodeHex(MD5.digest(bytes))
    }

    fun md5(string: String): String {
        return md5(string.toByteArray())
    }

    private fun encodeHex(data: ByteArray): String {
        val l = data.size
        val out = CharArray(l shl 1)
        var i = 0
        var j = 0
        while (i < l) {
            out[j++] = chars[(240 and data[i].toInt()).ushr(4)]
            out[j++] = chars[15 and data[i].toInt()]
            i++
        }
        return String(out)
    }
}
