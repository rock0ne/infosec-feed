package uk.cybertecpro.infosecfeed

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.security.MessageDigest

/**
 * Small two-tier image cache: an in-memory LRU over a disk cache.
 *
 * Deliberately dependency-free — Glide or Coil would pull in a large graph for
 * what is a handful of thumbnails, and the feed only ever loads modest images.
 */
object ImageLoader {

    private const val MAX_EDGE = 1000
    private const val MAX_DOWNLOAD_BYTES = 8 * 1024 * 1024
    private const val MAX_CACHE_BYTES = 40L * 1024 * 1024
    private const val MAX_CACHE_FILES = 80

    private val memory = object : LruCache<String, Bitmap>(
        ((Runtime.getRuntime().maxMemory() / 1024) / 8).toInt()
    ) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }

    fun cached(url: String): Bitmap? = memory.get(url)

    suspend fun load(cacheDir: File, url: String): Bitmap? = withContext(Dispatchers.IO) {
        loadBlocking(cacheDir, url)
    }

    /** Disk/memory-only lookup for RemoteViews, which must not block on the network. */
    fun cachedOrDisk(cacheDir: File, url: String): Bitmap? {
        memory.get(url)?.let { return it }
        val file = cacheFile(cacheDir, url)
        if (!file.exists() || file.length() !in 1..MAX_DOWNLOAD_BYTES.toLong()) return null
        return runCatching { file.readBytes() }.getOrNull()
            ?.let(::decode)
            ?.also { memory.put(url, it) }
    }

    fun trimDiskCache(cacheDir: File) {
        val files = imageDir(cacheDir).listFiles()?.filter { it.isFile }
            ?.sortedByDescending { it.lastModified() } ?: return
        var bytes = 0L
        files.forEachIndexed { index, file ->
            bytes += file.length()
            if (index >= MAX_CACHE_FILES || bytes > MAX_CACHE_BYTES) runCatching { file.delete() }
        }
    }

    private fun loadBlocking(cacheDir: File, url: String): Bitmap? {
        memory.get(url)?.let { return it }

        val file = cacheFile(cacheDir, url)

        val bytes = if (file.exists() && file.length() in 1..MAX_DOWNLOAD_BYTES.toLong()) {
            runCatching { file.readBytes() }.getOrNull()
        } else {
            download(url)?.also { data ->
                val temporary = File(file.parentFile, "${file.name}.tmp")
                runCatching {
                    temporary.writeBytes(data)
                    if (!temporary.renameTo(file)) {
                        file.writeBytes(data)
                        temporary.delete()
                    }
                }
            }
        } ?: return null

        return decode(bytes)?.also { memory.put(url, it) }
    }

    private fun download(initialUrl: String): ByteArray? = runCatching {
        var current = initialUrl
        repeat(5) {
            val target = URL(current)
            if (!isPublicHttps(target)) return null
            val conn = (target.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 12_000
                instanceFollowRedirects = false
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) " +
                        "Chrome/127.0.0.0 Mobile Safari/537.36"
                )
                setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/*;q=0.8")
            }
            try {
                val code = conn.responseCode
                if (code in 300..399) {
                    val location = conn.getHeaderField("Location") ?: return null
                    val resolved = URL(target, location).toString()
                    current = if (resolved.startsWith("http://", ignoreCase = true)) {
                        resolved.replaceFirst(Regex("^http://", RegexOption.IGNORE_CASE), "https://")
                    } else {
                        resolved
                    }
                    return@repeat
                }
                if (code !in 200..299) return null
                if (!conn.contentType.orEmpty().substringBefore(';')
                        .startsWith("image/", ignoreCase = true)
                ) return null
                if (conn.contentLengthLong > MAX_DOWNLOAD_BYTES) return null
                return conn.inputStream.use { input ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(16 * 1024)
                    var total = 0
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_DOWNLOAD_BYTES) return null
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                }
            } finally {
                conn.disconnect()
            }
        }
        null
    }.getOrNull()

    /** Downsamples so a large hero image cannot blow the bitmap budget. */
    private fun decode(bytes: ByteArray): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        var edge = maxOf(bounds.outWidth, bounds.outHeight)
        while (edge / sample > MAX_EDGE) sample *= 2
        BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample }
        )
    }.getOrNull()

    private fun key(url: String): String =
        MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
            .joinToString("") { "%02x".format(it) }.take(40)

    private fun imageDir(cacheDir: File): File = File(cacheDir, "images").apply { mkdirs() }

    private fun cacheFile(cacheDir: File, url: String): File = File(imageDir(cacheDir), key(url))

    private fun isPublicHttps(url: URL): Boolean {
        if (url.protocol != "https" || !url.userInfo.isNullOrEmpty()) return false
        if (url.host.equals("localhost", ignoreCase = true)) return false
        return InetAddress.getAllByName(url.host).none { address ->
            address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
                address.isSiteLocalAddress || address.isMulticastAddress
        }
    }
}
