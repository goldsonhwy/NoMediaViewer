package com.nomedia.viewer

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BrowseState(
    val images: List<ImageFile> = emptyList(),
    val unviewedImages: List<ImageFile> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val favorites: Set<String> = emptySet(),
    val viewedImages: Set<String> = emptySet(),
    val currentTab: Int = 0,
    val columnCount: Int = 1,
    val showBottomBar: Boolean = true,
    val folderTrees: Map<String, FolderNode> = emptyMap(), // rootUri -> tree
    val storageConfig: StorageConfig = StorageConfig(),
    val fullscreenImage: ImageFile? = null
)

class MainViewModel(
    private val repository: ImageRepository,
    private val folderManager: FolderTreeManager,
    private val storageManager: StorageManager
) : ViewModel() {

    private val _state = MutableStateFlow(BrowseState())
    val state: StateFlow<BrowseState> = _state.asStateFlow()

    init {
        _state.value = _state.value.copy(
            isLoading = false,
            columnCount = folderManager.getColumnCount(),
            storageConfig = storageManager.getConfig()
        )
        refreshFolderTrees()
    }

    // ===== Image Scanning =====

    fun loadImages() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                if (folderManager.getRootUris().isEmpty()) {
                    _state.value = _state.value.copy(
                        isLoading = false, error = "请先在「文件夹」页面添加要扫描的目录"
                    )
                    return@launch
                }
                if (folderManager.getCheckedRootUris().isEmpty()) {
                    _state.value = _state.value.copy(
                        isLoading = false, error = "请勾选要扫描的文件夹（勾选母文件夹即可）"
                    )
                    return@launch
                }

                val allImages = repository.scanCheckedFolders(folderManager)
                val viewed = repository.getViewedImages()
                val favorites = repository.getFavorites()
                val unviewed = allImages.filter { it.uri !in viewed }

                _state.value = _state.value.copy(
                    images = allImages, unviewedImages = unviewed,
                    isLoading = false, favorites = favorites
                )
                if (allImages.isEmpty()) {
                    _state.value = _state.value.copy(error = "所选文件夹中未找到图片文件")
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = "扫描出错: ${e.message}")
            }
        }
    }

    // ===== View-once =====

    fun markAsViewed(uri: String) {
        repository.markAsViewed(uri)
        val viewed = repository.getViewedImages()
        _state.value = _state.value.copy(
            unviewedImages = _state.value.images.filter { it.uri !in viewed },
            viewedImages = viewed
        )
    }

    fun resetHistory() {
        repository.resetHistory()
        _state.value = _state.value.copy(unviewedImages = _state.value.images, error = null)
    }

    // ===== Favorites =====

    fun toggleFavorite(uri: String) {
        val added = repository.toggleFavorite(uri)
        _state.value = _state.value.copy(favorites = repository.getFavorites())
        if (added) {
            val image = _state.value.images.find { it.uri == uri }
                ?: repository.getFavoriteImages().find { it.uri == uri }
            if (image != null) {
                viewModelScope.launch { storageManager.saveFavoriteImage(image.uri, image.name) }
            }
        }
    }

    fun isFavorite(uri: String): Boolean = repository.isFavorite(uri)
    fun getFavoriteImages(): List<ImageFile> = repository.getFavoriteImages()

    // ===== Import Flow =====

    fun getImportedPaths(): Set<String> {
        val paths = mutableSetOf<String>()
        for (rootUri in folderManager.getRootUris()) {
            for (relPath in folderManager.getCheckedFolders(rootUri)) {
                paths.add("$rootUri|$relPath")
            }
        }
        return paths
    }

    fun importFolders(folders: List<Pair<String, String>>) {
        // First, clear all checked states for all roots
        for (rootUri in folderManager.getRootUris()) {
            val checked = folderManager.getCheckedFolders(rootUri).toSet()
            for (relPath in checked) {
                folderManager.setFolderChecked(rootUri, relPath, false)
            }
        }
        // Then set the newly selected ones
        for ((rootUri, relPath) in folders) {
            folderManager.setFolderChecked(rootUri, relPath, true)
        }
        refreshFolderTrees()
        loadImages()
    }

    // ===== Tab & UI =====

    fun setTab(index: Int) { _state.value = _state.value.copy(currentTab = index) }

    fun setShowBottomBar(show: Boolean) {
        _state.value = _state.value.copy(showBottomBar = show)
    }

    fun setColumnCount(n: Int) {
        folderManager.setColumnCount(n)
        _state.value = _state.value.copy(columnCount = n)
    }

    // ===== Folder Tree =====

    fun refreshFolderTrees() {
        viewModelScope.launch {
            val trees = mutableMapOf<String, FolderNode>()
            for (uri in folderManager.getRootUriList()) {
                val node = folderManager.getFolderTree(uri)
                if (node != null) trees[uri.toString()] = node
            }
            _state.value = _state.value.copy(folderTrees = trees)
        }
    }

    fun onFolderChecked(rootUri: String, relativePath: String, checked: Boolean) {
        folderManager.setFolderChecked(rootUri, relativePath, checked)
        refreshFolderTrees()
        loadImages()
    }

    fun addRootFolder(uri: Uri) {
        folderManager.addRootFolder(uri)
        refreshFolderTrees()
        loadImages()
    }

    fun removeRootFolder(uri: Uri) {
        folderManager.removeRootFolder(uri)
        refreshFolderTrees()
        loadImages()
    }

    fun getRootUriList(): List<Uri> = folderManager.getRootUriList()

    // ===== Storage =====

    fun getStorageConfig(): StorageConfig = storageManager.getConfig()
    fun saveStorageConfig(config: StorageConfig) {
        storageManager.saveConfig(config)
        _state.value = _state.value.copy(storageConfig = config)
    }

    // ===== Fullscreen =====

    fun openFullscreen(image: ImageFile) { _state.value = _state.value.copy(fullscreenImage = image) }
    fun closeFullscreen() { _state.value = _state.value.copy(fullscreenImage = null) }

    // ===== Factory =====

    class Factory(
        private val repository: ImageRepository,
        private val folderManager: FolderTreeManager,
        private val storageManager: StorageManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(repository, folderManager, storageManager) as T
        }
    }
}
