package ephyra.data.backup

import android.content.Context
import android.net.Uri
import ephyra.data.backup.models.Backup
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import java.io.IOException
import java.util.zip.GZIPInputStream

/**
 * Class used to decode a backup file.
 */
@OptIn(ExperimentalSerializationApi::class)
class BackupDecoder(
    private val context: Context,
    private val protoBuf: ProtoBuf,
) {

    /**
     * Decodes a backup file from the given uri.
     *
     * @param uri the uri of the backup file.
     * @return the decoded backup.
     */
    fun decode(uri: Uri): Backup {
        return (
            context.contentResolver.openInputStream(uri)
                ?: throw IOException("Unable to open input stream")
            ).use {
            decode(it)
        }
    }

    /**
     * Decodes a backup file from the given input stream.
     *
     * Handles both gzip-compressed streams (the format written by [BackupCreator] and
     * exported by Mihon) and plain uncompressed streams.
     *
     * @param inputStream the input stream of the backup file.
     * @return the decoded backup.
     */
    fun decode(inputStream: java.io.InputStream): Backup {
        return try {
            // Buffer the stream so we can inspect the first two bytes (gzip magic number
            // 0x1F 0x8B) without consuming them, then reset before decompression.
            val buffered = inputStream.buffered()
            buffered.mark(2)
            val b1 = buffered.read()
            val b2 = buffered.read()
            buffered.reset()

            val bytes = if (b1 == 0x1F && b2 == 0x8B) {
                GZIPInputStream(buffered).use { it.readBytes() }
            } else {
                buffered.readBytes()
            }

            protoBuf.decodeFromByteArray(Backup.serializer(), bytes)
        } catch (e: Exception) {
            throw IOException(e)
        }
    }
}
