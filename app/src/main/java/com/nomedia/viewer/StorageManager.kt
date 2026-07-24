package com.nomedia.viewer

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

enum class StorageType {
    LOCAL, WEBDAV, SMB
}

data class StorageConfig(
    val type: StorageType = StorageType.LOCAL,
    val localPath: String = "",
    val webdavUrl: String = "",
    val webdavUser: String = "",
    val webdavPass: String = "",
    val smbUrl: String = "",
    val smbUser: String = "",
    val smbPass: String = "",
    val enabled: Boolean = false
)

class StorageManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("storage", Context.MODE_PRIVATE)

    fun getConfig(): StorageConfig {
        return StorageConfig(
            type = StorageType.valueOf(prefs.getString("storage_type", "LOCAL") ?: "LOCAL"),
            localPath = prefs.getString("local_path", "") ?: "",
            webdavUrl = prefs.getString("webdav_url", "") ?: "",
            webdavUser = prefs.getString("webdav_user", "") ?: "",
            webdavPass = prefs.getString("webdav_pass", "") ?: "",
            smbUrl = prefs.getString("smb_url", "") ?: "",
            smbUser = prefs.getString("smb_user", "") ?: "",
            smbPass = prefs.getString("smb_pass", "") ?: "",
            enabled = prefs.getBoolean("enabled", false)
        )
    }

    fun saveConfig(config: StorageConfig) {
        prefs.edit()
            .putString("storage_type", config.type.name)
            .putString("local_path", config.localPath)
            .putString("webdav_url", config.webdavUrl)
            .putString("webdav_user", config.webdavUser)
            .putString("webdav_pass", config.webdavPass)
            .putString("smb_url", config.smbUrl)
            .putString("smb_user", config.smbUser)
            .putString("smb_pass", config.smbPass)
            .putBoolean("enabled", config.enabled)
            .apply()
    }

    suspend fun saveFavoriteImage(imagePath: String, fileName: String): Result<String> {
        val config = getConfig()
        if (!config.enabled) return Result.failure(Exception("存储未启用"))

        return withContext(Dispatchers.IO) {
            try {
                when (config.type) {
                    StorageType.LOCAL -> saveToLocal(imagePath, fileName, config.localPath)
                    StorageType.WEBDAV -> saveToWebdav(imagePath, fileName, config)
                    StorageType.SMB -> saveToSmb(imagePath, fileName, config)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private fun saveToLocal(imagePath: String, fileName: String, destPath: String): Result<String> {
        val destDir = if (destPath.isNotEmpty()) {
            File(destPath)
        } else {
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "NoMediaFavorites")
        }

        if (!destDir.exists()) destDir.mkdirs()

        val sourceFile = File(imagePath)
        if (!sourceFile.exists()) return Result.failure(Exception("源文件不存在"))

        val destFile = File(destDir, fileName)
        // Avoid overwrite
        var finalFile = destFile
        var counter = 1
        while (finalFile.exists()) {
            val name = fileName.substringBeforeLast(".")
            val ext = fileName.substringAfterLast(".", "")
            finalFile = File(destDir, "${name}_$counter.$ext")
            counter++
        }

        sourceFile.inputStream().use { input ->
            FileOutputStream(finalFile).use { output ->
                input.copyTo(output)
            }
        }

        return Result.success(finalFile.absolutePath)
    }

    private fun saveToWebdav(imagePath: String, fileName: String, config: StorageConfig): Result<String> {
        // WebDAV: HTTP PUT to configured URL
        val url = "${config.webdavUrl.trimEnd('/')}/$fileName"
        val file = File(imagePath)
        if (!file.exists()) return Result.failure(Exception("源文件不存在"))

        val bytes = file.readBytes()

        val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        return try {
            connection.requestMethod = "PUT"
            connection.setRequestProperty("Content-Type", "image/jpeg")
            val auth = android.util.Base64.encodeToString(
                "${config.webdavUser}:${config.webdavPass}".toByteArray(),
                android.util.Base64.NO_WRAP
            )
            connection.setRequestProperty("Authorization", "Basic $auth")
            connection.doOutput = true
            connection.outputStream.use { it.write(bytes) }

            val code = connection.responseCode
            if (code in 200..299) {
                Result.success("${config.webdavUrl.trimEnd('/')}/$fileName")
            } else {
                Result.failure(Exception("WebDAV 上传失败: HTTP $code"))
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun saveToSmb(imagePath: String, fileName: String, config: StorageConfig): Result<String> {
        // SMB via jCIFS or similar - simplified HTTP-based approach
        // For a full implementation, we'd need the jCIFS library
        // This is a placeholder that falls back to local copy
        return saveToLocal(imagePath, fileName, 
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                .resolve("NoMediaFavorites").absolutePath)
    }
}
