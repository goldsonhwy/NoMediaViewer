package com.nomedia.viewer

import android.content.Context
import android.os.Environment
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class StorageManager(private val context: Context) {
    suspend fun saveFavoriteImage(imagePath: String, config: StorageConfig, rootPaths: List<String>): Result<String> = withContext(Dispatchers.IO) {
        if (!config.enabled) return@withContext Result.failure(Exception("未启用存储"))
        runCatching {
            val relative = relativePath(imagePath, rootPaths)
            val copied = mutableListOf<String>()
            if (config.localEnabled) copied += saveLocal(imagePath, relative, config.localPath)
            if (config.webdavEnabled) copied += saveWebDav(imagePath, relative, config)
            if (config.smbEnabled) copied += saveLocal(imagePath, relative, File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Yellow-gallery收藏/SMB").absolutePath)
            if (copied.isEmpty()) copied += saveLocal(imagePath, relative, config.localPath)
            copied.joinToString("|")
        }
    }

    suspend fun deleteFavoriteCopy(copiedPathOrUrl: String, config: StorageConfig): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (copiedPathOrUrl.isBlank()) return@runCatching
            copiedPathOrUrl.split('|').filter { it.isNotBlank() }.forEach { target ->
                when {
                    target.startsWith("http://") || target.startsWith("https://") -> deleteWebDav(target, config)
                    else -> File(target).takeIf { it.exists() }?.delete()
                }
            }
            Unit
        }
    }

    private fun relativePath(imagePath: String, rootPaths: List<String>): String {
        val normalized = File(imagePath).absolutePath
        val root = rootPaths.map { File(it).absolutePath.trimEnd('/') + "/" }.firstOrNull { normalized.startsWith(it) }
        return if (root != null) normalized.removePrefix(root) else File(imagePath).name
    }

    private fun saveLocal(imagePath: String, relativePath: String, dirPath: String): String {
        val src = File(imagePath)
        require(src.exists()) { "源文件不存在" }
        val base = File(if (dirPath.isNotBlank()) dirPath else File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Yellow-gallery收藏").absolutePath)
        base.mkdirs()
        runCatching { File(base, ".nomedia").apply { if (!exists()) createNewFile() } }
        val dst = File(base, relativePath)
        dst.parentFile?.mkdirs()
        src.inputStream().use { input -> FileOutputStream(dst).use { input.copyTo(it) } }
        return dst.absolutePath
    }

    private fun saveWebDav(imagePath: String, relativePath: String, config: StorageConfig): String {
        val src = File(imagePath)
        require(src.exists()) { "源文件不存在" }
        val base = normalizedWebDavUrl(config.webdavUrl).firstOrNull() ?: throw IllegalArgumentException("WebDAV地址无效")
        val encodedPath = relativePath.split('/', '\\').joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
        val url = base.trimEnd('/') + "/" + encodedPath
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "PUT"
            conn.connectTimeout = 10000
            conn.readTimeout = 20000
            conn.doOutput = true
            if (config.webdavUser.isNotBlank()) {
                val auth = Base64.encodeToString("${config.webdavUser}:${config.webdavPass}".toByteArray(), Base64.NO_WRAP)
                conn.setRequestProperty("Authorization", "Basic $auth")
            }
            conn.setRequestProperty("Content-Type", "application/octet-stream")
            src.inputStream().use { input -> conn.outputStream.use { input.copyTo(it) } }
            val code = conn.responseCode
            if (code !in 200..299) throw IllegalStateException("WebDAV上传失败 HTTP $code")
            return url
        } finally { conn.disconnect() }
    }

    private fun deleteWebDav(url: String, config: StorageConfig) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "DELETE"
            conn.connectTimeout = 8000
            conn.readTimeout = 12000
            if (config.webdavUser.isNotBlank()) {
                val auth = Base64.encodeToString("${config.webdavUser}:${config.webdavPass}".toByteArray(), Base64.NO_WRAP)
                conn.setRequestProperty("Authorization", "Basic $auth")
            }
            conn.responseCode
        } finally { conn.disconnect() }
    }

    suspend fun testWebDavAuto(url: String, user: String, pass: String): String = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext "❌ 请输入WebDAV地址"
        val candidates = normalizedWebDavUrl(url)
        val errors = mutableListOf<String>()
        for (u in candidates) {
            val r = tryTest(u, user, pass)
            if (r.startsWith("✅")) return@withContext r
            errors.add(r)
        }
        errors.joinToString("\n")
    }

    private fun normalizedWebDavUrl(raw: String): List<String> {
        val s = raw.trim().trimEnd('/')
        if (s.startsWith("https://") || s.startsWith("http://")) return listOf(s)
        return listOf("https://$s", "http://$s")
    }

    private fun tryTest(url: String, user: String, pass: String): String = try {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "OPTIONS"
        conn.connectTimeout = 6000
        conn.readTimeout = 6000
        if (user.isNotBlank()) {
            val auth = Base64.encodeToString("$user:$pass".toByteArray(), Base64.NO_WRAP)
            conn.setRequestProperty("Authorization", "Basic $auth")
        }
        val code = conn.responseCode
        conn.disconnect()
        if (code in 200..299 || code == 207) "✅ 连接成功 HTTP $code\n$url" else "❌ HTTP $code\n$url"
    } catch (e: Exception) { "❌ ${e.localizedMessage ?: e.javaClass.simpleName}\n$url" }
}
