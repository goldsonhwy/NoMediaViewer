package com.nomedia.viewer

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class StorageManager(private val context: Context) {
    suspend fun saveFavoriteImage(imagePath: String, fileName: String, config: StorageConfig): Result<String> = withContext(Dispatchers.IO) {
        if (!config.enabled) return@withContext Result.failure(Exception("未启用存储"))
        runCatching {
            when (config.type) {
                StorageType.LOCAL -> saveLocal(imagePath, fileName, config.localPath)
                StorageType.WEBDAV -> saveWebDav(imagePath, fileName, config)
                StorageType.SMB -> saveLocal(imagePath, fileName, File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "涩图品鉴收藏").absolutePath)
            }
        }
    }

    private fun saveLocal(imagePath: String, fileName: String, dirPath: String): String {
        val src = File(imagePath)
        require(src.exists()) { "源文件不存在" }
        val dir = File(if (dirPath.isNotBlank()) dirPath else File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "涩图品鉴收藏").absolutePath)
        if (!dir.exists()) dir.mkdirs()
        var dst = File(dir, fileName)
        var i = 1
        while (dst.exists()) {
            val stem = fileName.substringBeforeLast('.', fileName)
            val ext = fileName.substringAfterLast('.', "")
            dst = File(dir, if (ext.isBlank()) "${stem}_$i" else "${stem}_$i.$ext")
            i++
        }
        src.inputStream().use { input -> FileOutputStream(dst).use { input.copyTo(it) } }
        return dst.absolutePath
    }

    private fun saveWebDav(imagePath: String, fileName: String, config: StorageConfig): String {
        val src = File(imagePath)
        require(src.exists()) { "源文件不存在" }
        val base = normalizedWebDavUrl(config.webdavUrl).firstOrNull() ?: throw IllegalArgumentException("WebDAV地址无效")
        val url = base.trimEnd('/') + "/" + java.net.URLEncoder.encode(fileName, "UTF-8").replace("+", "%20")
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
