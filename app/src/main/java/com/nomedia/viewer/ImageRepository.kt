package com.nomedia.viewer

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class ImageFile(
    val path: String,
    val uri: String = "",
    val name: String,
    val size: Long,
    val lastModified: Long
)

class ImageRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("nomedia_viewer", Context.MODE_PRIVATE)

    // ===== Scan using File API (reliable, works with MANAGE_EXTERNAL_STORAGE) =====

    suspend fun scanFolders(folderPaths: List<String>): List<ImageFile> = withContext(Dispatchers.IO) {
        val images = mutableListOf<ImageFile>()
        val extensions = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif")

        for (basePath in folderPaths) {
            val baseDir = File(basePath)
            if (!baseDir.exists() || !baseDir.canRead()) continue
            scanFileRecursive(baseDir, extensions, images, depth = 0)
        }

        images.sortedByDescending { it.lastModified }
    }

    private fun scanFileRecursive(dir: File, extensions: Set<String>, result: MutableList<ImageFile>, depth: Int) {
        if (depth > 12) return // safety limit
        try {
            val files = dir.listFiles() ?: return
            for (file in files) {
                if (file.isDirectory) {
                    scanFileRecursive(file, extensions, result, depth + 1)
                } else if (file.isFile) {
                    val ext = file.extension.lowercase()
                    if (ext in extensions) {
                        result.add(ImageFile(
                            path = file.absolutePath,
                            uri = file.absolutePath,
                            name = file.name,
                            size = file.length(),
                            lastModified = file.lastModified()
                        ))
                    }
                }
            }
        } catch (_: SecurityException) {
            // Permission denied directory - skip silently
        }
    }

    // ===== Favorites =====

    fun getFavorites(): Set<String> {
        return prefs.getStringSet("favorites", emptySet()) ?: emptySet()
    }

    fun toggleFavorite(uri: String): Boolean {
        val favs = getFavorites().toMutableSet()
        val added = if (uri in favs) { favs.remove(uri); false } else { favs.add(uri); true }
        prefs.edit().putStringSet("favorites", favs).apply()
        return added
    }

    fun isFavorite(uri: String): Boolean = uri in getFavorites()

    fun getFavoriteImages(): List<ImageFile> {
        return getFavorites().mapNotNull { path ->
            try {
                val file = File(path)
                if (file.exists()) {
                    ImageFile(path = path, uri = path, name = file.name, size = file.length(), lastModified = file.lastModified())
                } else null
            } catch (_: Exception) { null }
        }
    }

    // ===== Viewing History =====

    fun getViewedImages(): Set<String> = prefs.getStringSet("viewed", emptySet()) ?: emptySet()
    fun markAsViewed(uri: String) {
        val viewed = getViewedImages().toMutableSet()
        viewed.add(uri)
        prefs.edit().putStringSet("viewed", viewed).apply()
    }
    fun isViewed(uri: String): Boolean = uri in getViewedImages()
    fun resetHistory() { prefs.edit().remove("viewed").apply() }
}
