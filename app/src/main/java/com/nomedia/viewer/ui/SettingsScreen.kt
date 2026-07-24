package com.nomedia.viewer.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nomedia.viewer.RootFolder
import com.nomedia.viewer.StorageConfig
import com.nomedia.viewer.StorageType

@Composable
fun SettingsScreen(
    roots: List<RootFolder>,
    storageConfig: StorageConfig,
    columns: Int,
    onColumns: (Int) -> Unit,
    onAddRoot: (Uri) -> Unit,
    onRootEnabled: (String, Boolean) -> Unit,
    onRemoveRoot: (String) -> Unit,
    resolvePath: (Uri) -> String?,
    onSaveStorage: (StorageConfig) -> Unit,
    onTestWebDav: (String, String, String, (String) -> Unit) -> Unit
) {
    var cfg by remember(storageConfig) { mutableStateOf(storageConfig) }
    var test by remember { mutableStateOf<String?>(null) }
    val addRoot = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let(onAddRoot) }
    val localPick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) cfg = cfg.copy(localUri = uri.toString(), localPath = resolvePath(uri) ?: uri.toString())
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("设置", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Section("扫描根目录") {
            Button(onClick = { addRoot.launch(null) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F3460))) {
                Icon(Icons.Default.CreateNewFolder, null); Spacer(Modifier.width(8.dp)); Text("添加手机文件夹")
            }
            Spacer(Modifier.height(10.dp))
            roots.forEach { r ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF16213E)), shape = RoundedCornerShape(10.dp)) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Folder, null, tint = Color(0xFFE94560))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(r.path, color = Color(0xFF777777), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Switch(checked = r.enabled, onCheckedChange = { onRootEnabled(r.uri, it) })
                        IconButton(onClick = { onRemoveRoot(r.uri) }) { Icon(Icons.Default.Delete, null, tint = Color(0x99FF6B6B)) }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Section("浏览布局") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(selected = columns == 1, onClick = { onColumns(1) }, label = { Text("单列") })
                FilterChip(selected = columns == 2, onClick = { onColumns(2) }, label = { Text("双列") })
            }
        }
        Spacer(Modifier.height(16.dp))
        Section("收藏存储") {
            Row(verticalAlignment = Alignment.CenterVertically) { Text("自动备份收藏", color = Color.White, modifier = Modifier.weight(1f)); Switch(cfg.enabled, { cfg = cfg.copy(enabled = it) }) }
            if (cfg.enabled) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StorageType.values().forEach { t -> FilterChip(selected = cfg.type == t, onClick = { cfg = cfg.copy(type = t) }, label = { Text(t.name) }) }
                }
                Spacer(Modifier.height(10.dp))
                when (cfg.type) {
                    StorageType.LOCAL -> {
                        Button(onClick = { localPick.launch(null) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A3A5C))) { Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text(if (cfg.localPath.isBlank()) "选择本地存储目录" else "已选择本地目录") }
                        if (cfg.localPath.isNotBlank()) Text(cfg.localPath, color = Color(0xFF888888), fontSize = 11.sp)
                    }
                    StorageType.WEBDAV -> {
                        Field("WebDAV地址", cfg.webdavUrl) { cfg = cfg.copy(webdavUrl = it) }
                        Field("用户名", cfg.webdavUser) { cfg = cfg.copy(webdavUser = it) }
                        Field("密码", cfg.webdavPass) { cfg = cfg.copy(webdavPass = it) }
                        Button(onClick = { test = "测试中..."; onTestWebDav(cfg.webdavUrl, cfg.webdavUser, cfg.webdavPass) { test = it } }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A3A5C))) { Icon(Icons.Default.NetworkCheck, null); Spacer(Modifier.width(8.dp)); Text("自动测试http/https") }
                        test?.let { Text(it, color = if (it.startsWith("✅")) Color(0xFF4CAF50) else Color(0xFFFF6B6B), fontSize = 12.sp) }
                    }
                    StorageType.SMB -> {
                        Field("SMB地址", cfg.smbUrl) { cfg = cfg.copy(smbUrl = it) }
                        Field("用户名", cfg.smbUser) { cfg = cfg.copy(smbUser = it) }
                        Field("密码", cfg.smbPass) { cfg = cfg.copy(smbPass = it) }
                        Text("SMB为实验性，当前失败时会回退到本地收藏目录", color = Color(0xFF888888), fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Button(onClick = { onSaveStorage(cfg); test = "✅ 已保存" }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE94560))) { Text("保存存储设置") }
            }
        }
        Spacer(Modifier.height(50.dp))
    }
}

@Composable private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF16213E)), shape = RoundedCornerShape(12.dp)) { Column(Modifier.fillMaxWidth().padding(14.dp), content = content) }
}
@Composable private fun Field(label: String, value: String, onChange: (String)->Unit) { OutlinedTextField(value, onChange, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)) }
