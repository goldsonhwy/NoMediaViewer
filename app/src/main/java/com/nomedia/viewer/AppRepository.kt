package com.nomedia.viewer

import android.content.Context
import android.net.Uri
import android.graphics.BitmapFactory
import java.io.File

class AppRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("setu_pinjian_v10", Context.MODE_PRIVATE)
    private val imageExt = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif")

    fun addRoot(uri: Uri): RootFolder? {
        persistRead(uri)
        val uriStr = uri.toString()
        val path = uriToPath(uriStr) ?: return null
        val roots = prefs.getStringSet("roots", emptySet())!!.toMutableSet()
        roots.add(uriStr)
        prefs.edit().putStringSet("roots", roots).putBoolean(enabledKey(uriStr), true).apply()
        return RootFolder(uriStr, path, File(path).name.ifBlank { path }, true)
    }

    fun roots(): List<RootFolder> = (prefs.getStringSet("roots", emptySet()) ?: emptySet()).mapNotNull { uriStr ->
        val path = uriToPath(uriStr) ?: return@mapNotNull null
        RootFolder(uriStr, path, File(path).name.ifBlank { path }, prefs.getBoolean(enabledKey(uriStr), true))
    }

    fun setRootEnabled(uri: String, enabled: Boolean) = prefs.edit().putBoolean(enabledKey(uri), enabled).apply()
    fun removeRoot(uri: String) {
        val roots = prefs.getStringSet("roots", emptySet())!!.toMutableSet()
        roots.remove(uri)
        prefs.edit().putStringSet("roots", roots).remove(enabledKey(uri)).apply()
    }
    fun deleteAlbumFolder(path: String): Boolean = runCatching { File(path).deleteRecursively() }.getOrDefault(false)

    fun enabledRootPaths(): List<String> = roots().filter { it.enabled }.map { it.path }

    fun networkFolders(): List<NetworkFolder> = (prefs.getStringSet("network_roots", emptySet()) ?: emptySet()).mapNotNull { raw ->
        val p = raw.split("\u001f", limit = 7)
        if (p.size < 7) null else NetworkFolder(p[0], runCatching { NetworkFolderType.valueOf(p[1]) }.getOrDefault(NetworkFolderType.WEBDAV), p[2], p[3], p[4], p[5], p[6].toBoolean())
    }
    fun addNetworkFolder(type: NetworkFolderType, name: String, url: String, user: String, pass: String) {
        val id = "${type.name}_${url.hashCode()}_${System.currentTimeMillis()}"
        val set = (prefs.getStringSet("network_roots", emptySet()) ?: emptySet()).toMutableSet()
        set.add(listOf(id, type.name, name.ifBlank { url }, url, user, pass, "true").joinToString("\u001f"))
        prefs.edit().putStringSet("network_roots", set).apply()
    }
    fun updateNetworkFolder(id: String, type: NetworkFolderType, name: String, url: String, user: String, pass: String, enabled: Boolean = true) {
        val updated = (prefs.getStringSet("network_roots", emptySet()) ?: emptySet()).map { raw ->
            val p = raw.split("\u001f", limit = 7)
            if (p.size == 7 && p[0] == id) listOf(id, type.name, name.ifBlank { url }, url, user, pass, enabled.toString()).joinToString("\u001f") else raw
        }.toSet()
        prefs.edit().putStringSet("network_roots", updated).apply()
    }
    fun removeNetworkFolder(id: String) { prefs.edit().putStringSet("network_roots", (prefs.getStringSet("network_roots", emptySet()) ?: emptySet()).filterNot { it.startsWith("$id\u001f") }.toSet()).apply() }
    fun clearNetworkFolders() = prefs.edit().remove("network_roots").apply()
    fun setNetworkFolderEnabled(id: String, enabled: Boolean) {
        val updated = (prefs.getStringSet("network_roots", emptySet()) ?: emptySet()).map { raw ->
            val p = raw.split("\u001f", limit = 7)
            if (p.size == 7 && p[0] == id) p.take(6).plus(enabled.toString()).joinToString("\u001f") else raw
        }.toSet()
        prefs.edit().putStringSet("network_roots", updated).apply()
    }

    fun scanAlbums(network: NetworkFolderManager? = null): List<FolderAlbum> {
        val grouped = mutableMapOf<String, MutableList<ImageFile>>()
        enabledRootPaths().forEach { root -> scanDir(File(root), grouped, 0) }
        networkFolders().filter { it.enabled }.forEach { nf ->
            val cachedPaths = network?.let { runCatching { kotlinx.coroutines.runBlocking { it.scan(nf) } }.getOrDefault(emptyList()) } ?: emptyList()
            cachedPaths.forEach { p ->
                val img = toImageFile(File(p))
                grouped.getOrPut(img.parentPath) { mutableListOf() }.add(img)
            }
        }
        return grouped.values.mapNotNull { list ->
            val sorted = list.sortedByDescending { it.lastModified }
            val first = sorted.firstOrNull() ?: return@mapNotNull null
            FolderAlbum(
                path = first.parentPath,
                name = File(first.parentPath).name.ifBlank { first.parentPath },
                coverPath = first.path,
                count = list.size,
                latestModified = first.lastModified,
                imagePaths = sorted.map { it.path }
            )
        }.sortedWith(compareByDescending<FolderAlbum> { it.latestModified }.thenBy { it.name.lowercase() })
    }

    fun scanImages(paths: List<String>): List<ImageFile> {
        val all = mutableListOf<ImageFile>()
        paths.distinct().forEach { scanDirFlat(File(it), all, 0) }
        return all.sortedWith(compareBy<ImageFile> { it.width > it.height }.thenByDescending { it.lastModified })
    }

    private fun scanDir(dir: File, grouped: MutableMap<String, MutableList<ImageFile>>, depth: Int) {
        if (depth > 30 || !dir.exists() || !dir.isDirectory || !dir.canRead()) return
        val files = runCatching { dir.listFiles() }.getOrNull() ?: return
        files.forEach { f ->
            if (f.isDirectory) scanDir(f, grouped, depth + 1)
            else if (f.isFile && f.extension.lowercase() in imageExt) {
                grouped.getOrPut(f.parentFile?.absolutePath ?: dir.absolutePath) { mutableListOf() }.add(toImageFile(f))
            }
        }
    }

    private fun scanDirFlat(dir: File, out: MutableList<ImageFile>, depth: Int) {
        if (depth > 30 || !dir.exists() || !dir.isDirectory || !dir.canRead()) return
        val files = runCatching { dir.listFiles() }.getOrNull() ?: return
        files.forEach { f ->
            if (f.isDirectory) scanDirFlat(f, out, depth + 1)
            else if (f.isFile && f.extension.lowercase() in imageExt) {
                out.add(toImageFile(f))
            }
        }
    }

    fun viewed(): Set<String> = prefs.getStringSet("viewed", emptySet()) ?: emptySet()
    fun markViewed(path: String) {
        val set = viewed().toMutableSet().also { it.add(path) }
        val parent = File(path).parent ?: ""
        prefs.edit().putStringSet("viewed", set).putLong("album_viewed_at_${parent.hashCode()}", System.currentTimeMillis()).apply()
    }
    fun markAlbumViewed(path: String) = prefs.edit().putLong("album_viewed_at_${path.hashCode()}", System.currentTimeMillis()).apply()
    fun albumViewedAt(path: String): Long = prefs.getLong("album_viewed_at_${path.hashCode()}", 0L)
    fun unmarkViewed(paths: Collection<String>) = prefs.edit().putStringSet("viewed", viewed().toMutableSet().also { it.removeAll(paths.toSet()) }).apply()
    fun resetViewed() = prefs.edit().remove("viewed").apply()

    fun favorites(): Set<String> = prefs.getStringSet("favorites", emptySet()) ?: emptySet()
    fun toggleFavorite(path: String): Boolean {
        val set = favorites().toMutableSet()
        val added = if (path in set) { set.remove(path); false } else { set.add(path); true }
        prefs.edit().putStringSet("favorites", set).apply()
        return added
    }
    fun favoriteCopyPath(path: String): String = prefs.getString("favorite_copy_${path.hashCode()}", "") ?: ""
    fun setFavoriteCopyPath(path: String, copiedPath: String) = prefs.edit().putString("favorite_copy_${path.hashCode()}", copiedPath).apply()
    fun clearFavoriteCopyPath(path: String) = prefs.edit().remove("favorite_copy_${path.hashCode()}").apply()
    fun favoriteImages(): List<ImageFile> = favorites().mapNotNull { p ->
        val f = File(p)
        if (f.exists()) toImageFile(f) else null
    }.sortedWith(compareBy<ImageFile> { it.width > it.height }.thenByDescending { it.lastModified })

    fun columns(): Int = prefs.getInt("columns", 1).coerceIn(1, 6)
    fun setColumns(v: Int) = prefs.edit().putInt("columns", v.coerceIn(1, 6)).apply()
    fun favoriteColumns(): Int = prefs.getInt("favorite_columns", 2).coerceIn(1, 6)
    fun setFavoriteColumns(v: Int) = prefs.edit().putInt("favorite_columns", v.coerceIn(1, 6)).apply()
    fun scrollSpeed(): Float = prefs.getFloat("scroll_speed", 1f).coerceIn(1f, 4f)
    fun setScrollSpeed(v: Float) = prefs.edit().putFloat("scroll_speed", v.coerceIn(1f, 4f)).apply()

    private fun toImageFile(f: File): ImageFile {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { BitmapFactory.decodeFile(f.absolutePath, opts) }
        return ImageFile(
            f.absolutePath,
            f.name,
            f.parentFile?.absolutePath ?: "",
            f.length(),
            f.lastModified(),
            opts.outWidth.coerceAtLeast(0),
            opts.outHeight.coerceAtLeast(0)
        )
    }

    fun storageConfig(): StorageConfig = StorageConfig(
        enabled = prefs.getBoolean("storage_enabled", false),
        type = runCatching { StorageType.valueOf(prefs.getString("storage_type", "LOCAL")!!) }.getOrDefault(StorageType.LOCAL),
        localEnabled = prefs.getBoolean("storage_local_enabled", true),
        webdavEnabled = prefs.getBoolean("storage_webdav_enabled", false),
        smbEnabled = prefs.getBoolean("storage_smb_enabled", false),
        localUri = prefs.getString("local_uri", "") ?: "",
        localPath = prefs.getString("local_path", "") ?: "",
        webdavUrl = prefs.getString("webdav_url", "") ?: "",
        webdavUser = prefs.getString("webdav_user", "") ?: "",
        webdavPass = prefs.getString("webdav_pass", "") ?: "",
        smbUrl = prefs.getString("smb_url", "") ?: "",
        smbUser = prefs.getString("smb_user", "") ?: "",
        smbPass = prefs.getString("smb_pass", "") ?: ""
    )
    fun saveStorage(c: StorageConfig) = prefs.edit()
        .putBoolean("storage_enabled", c.enabled).putString("storage_type", c.type.name)
        .putBoolean("storage_local_enabled", c.localEnabled).putBoolean("storage_webdav_enabled", c.webdavEnabled).putBoolean("storage_smb_enabled", c.smbEnabled)
        .putString("local_uri", c.localUri).putString("local_path", c.localPath)
        .putString("webdav_url", c.webdavUrl).putString("webdav_user", c.webdavUser).putString("webdav_pass", c.webdavPass)
        .putString("smb_url", c.smbUrl).putString("smb_user", c.smbUser).putString("smb_pass", c.smbPass).apply()

    fun uriToPath(uriStr: String): String? {
        val uri = Uri.parse(uriStr)
        val seg = uri.lastPathSegment ?: return null
        val decoded = Uri.decode(seg)
        val clean = decoded.removePrefix("tree/")
        return when {
            clean.startsWith("primary:") -> "/storage/emulated/0/" + clean.removePrefix("primary:").trimStart('/')
            ":" in clean -> {
                val parts = clean.split(":", limit = 2)
                "/storage/${parts[0]}/${parts.getOrElse(1) { "" }}".trimEnd('/')
            }
            else -> null
        }
    }

    private fun persistRead(uri: Uri) = runCatching {
        context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    private fun enabledKey(uri: String) = "root_enabled_${uri.hashCode()}"
}
