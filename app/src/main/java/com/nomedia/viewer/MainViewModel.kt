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
    val folders: List<FolderManager.ManagedFolder> = emptyList(),
    val storageConfig: StorageConfig = StorageConfig(),
    val fullscreenImage: ImageFile? = null
)

class MainViewModel(
    private val repository: ImageRepository,
    private val folderManager: FolderManager,
    private val storageManager: StorageManager
) : ViewModel() {

    private val _state = MutableStateFlow(BrowseState())
    val state: StateFlow<BrowseState> = _state.asStateFlow()

    init {
        _state.value = _state.value.copy(
            isLoading = false,
            columnCount = folderManager.getColumnCount(),
            storageConfig = storageManager.getConfig(),
            folders = folderManager.getFolders()
        )
    }

    // ===== Image Scanning =====

    fun loadImages() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val enabledPaths = folderManager.getEnabledPaths()
                if (enabledPaths.isEmpty()) {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = if (folderManager.hasFolders()) "请在「文件夹」页面启用要扫描的目录" else "请先在「设置」中添加手机文件夹"
                    )
                    return@launch
                }

                val allImages = repository.scanFolders(enabledPaths)
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

    // ===== Folder Management =====

    fun addFolder(uri: Uri) {
        folderManager.addFolder(uri)
        refreshFolders()
        loadImages()
    }

    fun removeFolder(uriStr: String) {
        folderManager.removeFolder(uriStr)
        refreshFolders()
        loadImages()
    }

    fun toggleFolder(uriStr: String, enabled: Boolean) {
        folderManager.setEnabled(uriStr, enabled)
        refreshFolders()
        loadImages()
    }

    fun refreshFolders() {
        _state.value = _state.value.copy(folders = folderManager.getFolders())
    }

    fun parseSafPath(uriStr: String): String? = folderManager.safUriToPath(uriStr)

    // ===== Tab & UI =====

    fun setTab(index: Int) {
        _state.value = _state.value.copy(currentTab = index)
        if (index == 1) refreshFolders()
    }

    fun setShowBottomBar(show: Boolean) {
        _state.value = _state.value.copy(showBottomBar = show)
    }

    fun setColumnCount(n: Int) {
        folderManager.setColumnCount(n)
        _state.value = _state.value.copy(columnCount = n)
    }

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
        private val folderManager: FolderManager,
        private val storageManager: StorageManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(repository, folderManager, storageManager) as T
        }
    }
}
