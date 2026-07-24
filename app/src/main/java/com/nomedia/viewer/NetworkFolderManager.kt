package com.nomedia.viewer

import android.content.Context
import android.util.Base64
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.EnumSet

class NetworkFolderManager(private val context: Context) {
    private val imageExt = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif")
    private val cacheRoot = File(context.cacheDir, "network_folders").apply { mkdirs() }

    suspend fun scan(folder: NetworkFolder): List<String> = withContext(Dispatchers.IO) {
        runCatching {
            when (folder.type) {
                NetworkFolderType.WEBDAV -> scanWebDav(folder)
                NetworkFolderType.SMB -> scanSmb(folder)
            }
        }.getOrElse { emptyList() }
    }

    private fun scanWebDav(folder: NetworkFolder): List<String> {
        val out = mutableListOf<String>()
        val visited = mutableSetOf<String>()
        fun walk(url: String, depth: Int) {
            if (depth > 20 || !visited.add(url)) return
            val entries = propfind(url, folder.user, folder.pass)
            entries.forEach { href ->
                val abs = resolveWebDavUrl(url, href)
                val clean = abs.substringBefore('?').trimEnd('/')
                val name = URLDecoder.decode(clean.substringAfterLast('/'), "UTF-8")
                if (abs.trimEnd('/') == url.trimEnd('/')) return@forEach
                if (looksLikeImage(name)) {
                    download(abs, folder, name)?.let(out::add)
                } else if (!name.contains('.') || href.endsWith('/')) {
                    walk(abs.trimEnd('/') + "/", depth + 1)
                }
            }
        }
        walk(normalizeUrl(folder.url), 0)
        return out
    }

    private fun propfind(url: String, user: String, pass: String): List<String> {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "PROPFIND"
            conn.connectTimeout = 8000
            conn.readTimeout = 12000
            conn.setRequestProperty("Depth", "1")
            if (user.isNotBlank()) conn.setRequestProperty("Authorization", basic(user, pass))
            val code = conn.responseCode
            if (code !in listOf(207, 200)) return emptyList()
            val xml = conn.inputStream.bufferedReader().use { it.readText() }
            return Regex("<[^:>]*:?href[^>]*>(.*?)</[^:>]*:?href>", RegexOption.IGNORE_CASE)
                .findAll(xml).map { it.groupValues[1].trim() }.toList()
        } finally { conn.disconnect() }
    }

    private fun download(url: String, folder: NetworkFolder, fileName: String): String? = runCatching {
        val dir = File(cacheRoot, safe(folder.id) + "/" + safe(parentKey(url))).apply { mkdirs() }
        val dst = File(dir, fileName)
        if (dst.exists() && dst.length() > 0) return@runCatching dst.absolutePath
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10000
            conn.readTimeout = 20000
            if (folder.user.isNotBlank()) conn.setRequestProperty("Authorization", basic(folder.user, folder.pass))
            conn.inputStream.use { input -> dst.outputStream().use { input.copyTo(it) } }
            dst.absolutePath
        } finally { conn.disconnect() }
    }.getOrNull()

    private fun scanSmb(folder: NetworkFolder): List<String> {
        val parsed = parseSmb(folder.url) ?: return emptyList()
        val out = mutableListOf<String>()
        SMBClient().use { client ->
            client.connect(parsed.host).use { conn ->
                val ac = AuthenticationContext(folder.user.ifBlank { "guest" }, folder.pass.toCharArray(), parsed.domain)
                val session = conn.authenticate(ac)
                (session.connectShare(parsed.share) as DiskShare).use { share ->
                    fun walk(path: String, depth: Int) {
                        if (depth > 20) return
                        val list = runCatching { share.list(path) }.getOrNull() ?: return
                        list.forEach { info: FileIdBothDirectoryInformation ->
                            val name = info.fileName
                            if (name == "." || name == "..") return@forEach
                            val child = if (path.isBlank()) name else "$path/$name"
                            if (info.fileAttributes and 0x10L != 0L) walk(child, depth + 1)
                            else if (looksLikeImage(name)) downloadSmb(share, child, folder)?.let(out::add)
                        }
                    }
                    walk(parsed.path, 0)
                }
            }
        }
        return out
    }

    private fun downloadSmb(share: DiskShare, path: String, folder: NetworkFolder): String? = runCatching {
        val dst = File(cacheRoot, safe(folder.id) + "/" + path).apply { parentFile?.mkdirs() }
        if (dst.exists() && dst.length() > 0) return@runCatching dst.absolutePath
        share.openFile(
            path,
            setOf(AccessMask.GENERIC_READ),
            null,
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OPEN,
            null
        ).use { file -> file.inputStream.use { input -> dst.outputStream().use { input.copyTo(it) } } }
        dst.absolutePath
    }.getOrNull()

    private fun normalizeUrl(raw: String): String {
        val s = raw.trim().trimEnd('/')
        return when {
            s.startsWith("http://") || s.startsWith("https://") -> "$s/"
            else -> "https://$s/"
        }
    }
    private fun resolveWebDavUrl(base: String, href: String): String = runCatching { URI(base).resolve(href).toString() }.getOrDefault(href)
    private fun basic(user: String, pass: String) = "Basic " + Base64.encodeToString("$user:$pass".toByteArray(), Base64.NO_WRAP)
    private fun looksLikeImage(name: String) = name.substringAfterLast('.', "").lowercase() in imageExt
    private fun safe(s: String) = MessageDigest.getInstance("MD5").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun parentKey(url: String) = url.substringBeforeLast('/', "root")

    private data class SmbParts(val host: String, val share: String, val path: String, val domain: String = "")
    private fun parseSmb(raw: String): SmbParts? {
        val s = raw.removePrefix("smb://").trim('/')
        val parts = s.split('/').filter { it.isNotBlank() }
        if (parts.size < 2) return null
        return SmbParts(parts[0], parts[1], parts.drop(2).joinToString("/"))
    }
}
