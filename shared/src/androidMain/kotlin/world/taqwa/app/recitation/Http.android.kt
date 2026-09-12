package world.taqwa.app.recitation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Source
import okio.source
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * `HttpURLConnection`, as spec §7 chose: the app has no HTTP client and one dependency for two
 * requests — a manifest and a surah — is not worth it.
 */
actual suspend fun httpGet(url: String): ByteArray? = withContext(Dispatchers.IO) {
    var connection: HttpURLConnection? = null
    try {
        connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MILLIS
            readTimeout = TIMEOUT_MILLIS
            requestMethod = "GET"
        }
        if (connection.responseCode !in 200..299) return@withContext null
        connection.inputStream.use { it.readBytes() }
    } catch (e: IOException) {
        null
    } finally {
        connection?.disconnect()
    }
}

private const val TIMEOUT_MILLIS = 20_000

/**
 * The surah download's bytes. A `Range` header from the `.part`'s length, and the connection is
 * kept alive by the returned [Source] — closing it disconnects, which is the only reason this is
 * not four lines.
 */
class HttpByteSource(
    private val connectTimeoutMillis: Int = TIMEOUT_MILLIS,
    private val readTimeoutMillis: Int = READ_TIMEOUT_MILLIS,
) : ByteSource {

    override suspend fun open(url: String, fromByte: Long): ByteResponse = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = connectTimeoutMillis
                readTimeout = readTimeoutMillis
                requestMethod = "GET"
                // Redirects are followed within a protocol, which covers a GitHub release asset
                // redirecting to its CDN; a cross-protocol redirect would land on a null stream
                // and be reported as SERVER, which is the honest answer for one.
                instanceFollowRedirects = true
                if (fromByte > 0L) setRequestProperty("Range", "bytes=$fromByte-")
            }
            val status = connection.responseCode
            if (status !in 200..299) {
                connection.disconnect()
                return@withContext ByteResponse.Failure(DownloadFailure.SERVER)
            }
            val length = connection.getHeaderField("Content-Length")?.toLongOrNull() ?: -1L
            val stream = connection.inputStream
            val open = connection
            ByteResponse.Body(status, length, ClosingSource(stream.source()) { open.disconnect() })
        } catch (e: IOException) {
            connection?.disconnect()
            ByteResponse.Failure(DownloadFailure.SERVER)
        }
    }

    private class ClosingSource(
        private val delegate: Source,
        private val onClose: () -> Unit,
    ) : Source by delegate {
        override fun close() {
            try {
                delegate.close()
            } finally {
                onClose()
            }
        }
    }

    private companion object {
        /** Long enough for a stalled CDN to recover, short enough to fail a dead one. */
        const val READ_TIMEOUT_MILLIS = 30_000
    }
}
