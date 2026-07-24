package com.nomedia.viewer

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ImageFile(
    val path: String,
    val name: String,
    val size: Long,
    val lastModified: Long
)

class ImageRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("nomedia_viewer", Context.MODE_PRIVATE)

    // ===================== Image Scanning =====================

    suspend fun scanNoMediaImages(): List<ImageFile> = withContext(Dispatchers.IO) {
        val images = mutableListOf<ImageFile>()
        val extensions = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif")

        // Get all external storage directories
        val storageDirs = mutableListOf<File>()

        // Primary external storage
        Environment.getExternalStorageDirectory()?.let { storageDirs.add(it) }

        // Additional storage paths
        val additionalPaths = listOf(
            "/storage/emulated/0",
            "/sdcard",
            "/mnt/sdcard",
            "/storage",
        )
        for (path in additionalPaths) {
            val dir = File(path)
            if (dir.exists() && dir !in storageDirs) {
                storageDirs.add(dir)
            }
        }

        // Also scan from context's external media dirs
        context.getExternalFilesDirs(null).forEach { file ->
            if (file != null && file.exists()) {
                val parent = file.parentFile?.parentFile
                if (parent != null && parent !in storageDirs) {
                    storageDirs.add(parent)
                }
            }
        }

        // For each storage dir, find .nomedia directories and scan images
        for (baseDir in storageDirs) {
            if (!baseDir.exists() || !baseDir.canRead()) continue
            findNoMediaDirs(baseDir).forEach { nomediaDir ->
                scanImagesInDir(nomediaDir, extensions).let { images.addAll(it) }
            }
        }

        // Sort by modification time (newest first)
        images.sortedByDescending { it.lastModified }
    }

    private fun findNoMediaDirs(dir: File): List<File> {
        val result = mutableListOf<File>()
        try {
            val files = dir.listFiles() ?: return result
            for (file in files) {
                if (file.isDirectory && file.canRead()) {
                    // Check if this directory has .nomedia
                    if (File(file, ".nomedia").exists()) {
                        result.add(file)
                    }
                    // Recurse into subdirectories (limit depth)
                    if (file.absolutePath.count { it == '/' } - dir.absolutePath.count { it == '/' } < 4) {
                        result.addAll(findNoMediaDirs(file))
                    }
                }
            }
        } catch (e: Exception) {
            // Skip inaccessible directories
        }
        return result
    }

    private fun scanImagesInDir(dir: File, extensions: Set<String>): List<ImageFile> {
        val images = mutableListOf<ImageFile>()
        try {
            val files = dir.listFiles() ?: return images
            for (file in files) {
                if (file.isFile && file.canRead()) {
                    val ext = file.extension.lowercase()
                    if (ext in extensions) {
                        images.add(
                            ImageFile(
                                path = file.absolutePath,
                                name = file.name,
                                size = file.length(),
                                lastModified = file.lastModified()
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            // Skip inaccessible
        }
        return images
    }

    // ===================== Favorites =====================

    fun getFavorites(): Set<String> {
        return prefs.getStringSet("favorites", emptySet()) ?: emptySet()
    }

    fun toggleFavorite(path: String): Boolean {
        val favs = getFavorites().toMutableSet()
        val added = if (path in favs) {
            favs.remove(path)
            false
        } else {
            favs.add(path)
            true
        }
        prefs.edit().putStringSet("favorites", favs).apply()
        return added
    }

    fun isFavorite(path: String): Boolean {
        return path in getFavorites()
    }

    fun getFavoriteImages(): List<ImageFile> {
        val favPaths = getFavorites()
        return favPaths.mapNotNull { path ->
            val file = File(path)
            if (file.exists()) {
                ImageFile(
                    path = file.absolutePath,
                    name = file.name,
                    size = file.length(),
                    lastModified = file.lastModified()
                )
            } else {
                // Remove stale favorites
                removeFavorite(path)
                null
            }
        }
    }

    private fun removeFavorite(path: String) {
        val favs = getFavorites().toMutableSet()
        favs.remove(path)
        prefs.edit().putStringSet("favorites", favs).apply()
    }

    // ===================== Viewing History =====================

    fun getViewedImages(): Set<String> {
        return prefs.getStringSet("viewed", emptySet()) ?: emptySet()
    }

    fun markAsViewed(path: String) {
        val viewed = getViewedImages().toMutableSet()
        viewed.add(path)
        prefs.edit().putStringSet("viewed", viewed).apply()
    }

    fun isViewed(path: String): Boolean {
        return path in getViewedImages()
    }

    fun resetHistory() {
        prefs.edit().remove("viewed").apply()
    }
}
