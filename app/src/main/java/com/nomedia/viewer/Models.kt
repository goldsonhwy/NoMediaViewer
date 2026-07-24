package com.nomedia.viewer

data class ImageFile(
    val path: String,
    val name: String,
    val parentPath: String,
    val size: Long,
    val lastModified: Long,
    val width: Int = 0,
    val height: Int = 0
)

data class FolderAlbum(
    val path: String,
    val name: String,
    val coverPath: String,
    val count: Int,
    val latestModified: Long,
    val imagePaths: List<String> = emptyList()
)

enum class StorageType { LOCAL, WEBDAV, SMB }

data class StorageConfig(
    val enabled: Boolean = false,
    val type: StorageType = StorageType.LOCAL,
    val localEnabled: Boolean = true,
    val webdavEnabled: Boolean = false,
    val smbEnabled: Boolean = false,
    val localUri: String = "",
    val localPath: String = "",
    val webdavUrl: String = "",
    val webdavUser: String = "",
    val webdavPass: String = "",
    val smbUrl: String = "",
    val smbUser: String = "",
    val smbPass: String = ""
)

enum class NetworkFolderType { WEBDAV, SMB, FEINIU_NAS }

data class NetworkFolder(
    val id: String,
    val type: NetworkFolderType,
    val name: String,
    val url: String,
    val user: String = "",
    val pass: String = "",
    val enabled: Boolean = true
)

data class NetworkProbeResult(
    val ok: Boolean,
    val message: String,
    val normalizedUrl: String = "",
    val directories: List<String> = emptyList()
)

data class RootFolder(
    val uri: String,
    val path: String,
    val name: String,
    val enabled: Boolean = true
)
