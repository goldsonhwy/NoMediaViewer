package com.nomedia.viewer.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.saveable.rememberSaveable
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
    networkFolders: List<com.nomedia.viewer.NetworkFolder>,
    storageConfig: StorageConfig,
    columns: Int,
    favoriteColumns: Int,
    scrollSpeed: Float,
    onColumns: (Int) -> Unit,
    onFavoriteColumns: (Int) -> Unit,
    onScrollSpeed: (Float) -> Unit,
    onAddRoot: (Uri) -> Unit,
    onAddNetworkFolder: (com.nomedia.viewer.NetworkFolderType, String, String, String, String, (String) -> Unit) -> Unit,
    onUpdateNetworkFolder: (String, com.nomedia.viewer.NetworkFolderType, String, String, String, String, Boolean, (String) -> Unit) -> Unit,
    onRemoveNetworkFolder: (String) -> Unit,
    onExportLogs: () -> Unit,
    onRefreshNetwork: () -> Unit,
    onClearNetworkRecovery: () -> Unit,
    onNetworkEnabled: (String, Boolean) -> Unit,
    onProbeNetwork: (com.nomedia.viewer.NetworkFolderType, String, String, String, (com.nomedia.viewer.NetworkProbeResult) -> Unit) -> Unit,
    onScanLan: ((List<String>) -> Unit) -> Unit,
    onRootEnabled: (String, Boolean) -> Unit,
    onRemoveRoot: (String) -> Unit,
    resolvePath: (Uri) -> String?,
    onResetViewed: () -> Unit,
    onSaveStorage: (StorageConfig) -> Unit,
    onTestWebDav: (String, String, String, (String) -> Unit) -> Unit
) {
    var cfg by remember(storageConfig) { mutableStateOf(storageConfig) }
    var netType by remember { mutableStateOf(com.nomedia.viewer.NetworkFolderType.WEBDAV) }
    var netName by remember { mutableStateOf("") }
    var netUrl by remember { mutableStateOf("") }
    var netUser by remember { mutableStateOf("") }
    var netPass by remember { mutableStateOf("") }
    var probe by remember { mutableStateOf<com.nomedia.viewer.NetworkProbeResult?>(null) }
    var lanIps by remember { mutableStateOf<List<String>>(emptyList()) }
    var netBusy by remember { mutableStateOf(false) }
    var showNetworkDialog by remember { mutableStateOf(false) }
    var editingNet by remember { mutableStateOf<com.nomedia.viewer.NetworkFolder?>(null) }
    var test by remember { mutableStateOf<String?>(null) }
    val addRoot = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let(onAddRoot) }
    val localPick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) cfg = cfg.copy(localUri = uri.toString(), localPath = resolvePath(uri) ?: uri.toString())
    }

    if (showNetworkDialog) {
        AlertDialog(
            onDismissRequest = { showNetworkDialog = false },
            confirmButton = {
                Button(shape = RoundedCornerShape(8.dp), onClick = {
                    if (netUrl.isNotBlank()) {
                        netBusy = true
                        val edit = editingNet
                        val done: (String) -> Unit = { msg ->
                            test = msg; netBusy = false
                            if (msg.startsWith("✅")) { showNetworkDialog = false; editingNet = null; netName = ""; netUrl = ""; netUser = ""; netPass = ""; probe = null }
                        }
                        if (edit != null) onUpdateNetworkFolder(edit.id, netType, netName, netUrl, netUser, netPass, edit.enabled, done)
                        else onAddNetworkFolder(netType, netName, netUrl, netUser, netPass, done)
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB000), contentColor = Color.Black)) { Text(if (netBusy) "验证中..." else "验证并添加") }
            },
            dismissButton = { TextButton(shape = RoundedCornerShape(8.dp), onClick = { showNetworkDialog = false; editingNet = null }) { Text("取消") } },
            title = { Text(if (netType == com.nomedia.viewer.NetworkFolderType.WEBDAV) "添加WebDAV" else "添加Samba") },
            text = {
                Column {
                    Field("显示名称", netName) { netName = it }
                    Field(if (netType == com.nomedia.viewer.NetworkFolderType.WEBDAV) "地址/IP（自动http/https）" else "IP或smb://地址（自动补全smb://）", netUrl) { netUrl = it }
                    Field("用户名", netUser) { netUser = it }
                    Field("密码", netPass) { netPass = it }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(shape = RoundedCornerShape(8.dp), onClick = { netBusy = true; onProbeNetwork(netType, netUrl, netUser, netPass) { probe = it; netUrl = it.normalizedUrl.ifBlank { netUrl }; test = it.message; netBusy = false } }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF202020), contentColor = Color(0xFFFFB000))) { Text("测试") }
                        Button(shape = RoundedCornerShape(8.dp), onClick = { netBusy = true; onScanLan { lanIps = it; netBusy = false } }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF202020), contentColor = Color(0xFFFFB000))) { Text("扫IP") }
                    }
                    test?.let { Text(it, color = if (it.startsWith("✅")) Color(0xFF4CAF50) else Color(0xFFFFB000), fontSize = 12.sp) }
                    probe?.directories?.take(6)?.forEach { dir -> Text(dir, color = Color(0xFFFFB000), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().clickable { netUrl = dir }.padding(vertical = 2.dp)) }
                    if (lanIps.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { lanIps.take(3).forEach { ip -> AssistChip(shape = RoundedCornerShape(8.dp), onClick = { netUrl = ip }, label = { Text(ip) }) } }
                }
            },
            containerColor = Color(0xFF111111),
            titleContentColor = Color.White,
            textContentColor = Color.White
        )
    }

    var savedScroll by rememberSaveable { mutableIntStateOf(0) }
    val scrollState = rememberScrollState(initial = savedScroll)
    LaunchedEffect(scrollState) { snapshotFlow { scrollState.value }.collect { savedScroll = it } }
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().verticalScroll(scrollState).padding(16.dp)) {
        Text("设置", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Section("扫描根目录") {
            Button(shape = RoundedCornerShape(8.dp), onClick = { addRoot.launch(null) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB000), contentColor = Color.Black)) {
                Icon(Icons.Default.CreateNewFolder, null); Spacer(Modifier.width(8.dp)); Text("添加手机文件夹")
            }
            Spacer(Modifier.height(10.dp))
            roots.forEach { r ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF111111)), shape = RoundedCornerShape(10.dp)) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Folder, null, tint = Color(0xFFFFB000))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(r.path, color = Color(0xFFB0B0B0), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Switch(checked = r.enabled, onCheckedChange = { onRootEnabled(r.uri, it) })
                        IconButton(onClick = { onRemoveRoot(r.uri) }) { Icon(Icons.Default.Delete, null, tint = Color(0x99FFB000)) }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Section("网络文件夹") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(shape = RoundedCornerShape(8.dp), onClick = { netType = com.nomedia.viewer.NetworkFolderType.WEBDAV; showNetworkDialog = true }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB000), contentColor = Color.Black)) { Text("添加WebDAV") }
                Button(shape = RoundedCornerShape(8.dp), onClick = { netType = com.nomedia.viewer.NetworkFolderType.SMB; showNetworkDialog = true }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB000), contentColor = Color.Black)) { Text("添加Samba") }
            }
            networkFolders.forEach { nf ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF111111)), shape = RoundedCornerShape(10.dp)) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${if (nf.type == com.nomedia.viewer.NetworkFolderType.WEBDAV) "WebDAV" else "Samba"} · ${nf.name}", color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(nf.url, color = Color(0xFFB0B0B0), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Switch(checked = nf.enabled, onCheckedChange = { onNetworkEnabled(nf.id, it) })
                        IconButton(onClick = { editingNet = nf; netType = if (nf.type == com.nomedia.viewer.NetworkFolderType.WEBDAV) com.nomedia.viewer.NetworkFolderType.WEBDAV else com.nomedia.viewer.NetworkFolderType.SMB; netName = nf.name; netUrl = nf.url; netUser = nf.user; netPass = nf.pass; probe = null; test = null; showNetworkDialog = true }) { Icon(Icons.Default.Edit, null, tint = Color(0xFFFFB000)) }
                        IconButton(onClick = { onRemoveNetworkFolder(nf.id) }) { Icon(Icons.Default.Delete, null, tint = Color(0x99FFB000)) }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Section("浏览布局") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..6).forEach { n ->
                    FilterChip(shape = RoundedCornerShape(8.dp), 
                        selected = columns == n,
                        onClick = { onColumns(n) },
                        label = { Text("${n}列") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFFFB000),
                            selectedLabelColor = Color.Black,
                            containerColor = Color(0xFF202020),
                            labelColor = Color(0xFFFFB000)
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = columns == n,
                            borderColor = Color(0xFFFFB000),
                            selectedBorderColor = Color(0xFFFFB000)
                        )
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Section("收藏布局") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..6).forEach { n ->
                    FilterChip(shape = RoundedCornerShape(8.dp), 
                        selected = favoriteColumns == n,
                        onClick = { onFavoriteColumns(n) },
                        label = { Text("${n}列") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFFFB000),
                            selectedLabelColor = Color.Black,
                            containerColor = Color(0xFF202020),
                            labelColor = Color(0xFFFFB000)
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = favoriteColumns == n,
                            borderColor = Color(0xFFFFB000),
                            selectedBorderColor = Color(0xFFFFB000)
                        )
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Section("滑动速度") {
            Text("浏览页滑动速度：${String.format("%.1f", scrollSpeed)}x", color = Color.White, fontSize = 14.sp)
            Slider(
                value = scrollSpeed,
                onValueChange = onScrollSpeed,
                valueRange = 1f..3f,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFFFFB000),
                    activeTrackColor = Color(0xFFFFB000),
                    inactiveTrackColor = Color(0xFF444444)
                )
            )
            Text("正常为1x，最高3x；只影响浏览界面上下滑动速度", color = Color(0xFFB0B0B0), fontSize = 12.sp)
        }
        Spacer(Modifier.height(16.dp))
        Section("收藏存储") {
            Row(verticalAlignment = Alignment.CenterVertically) { Text("自动备份收藏", color = Color.White, modifier = Modifier.weight(1f)); Switch(cfg.enabled, { cfg = cfg.copy(enabled = it) }) }
            if (cfg.enabled) {
                Row(verticalAlignment = Alignment.CenterVertically) { Text("本地文件夹", color = Color.White, modifier = Modifier.weight(1f)); Switch(cfg.localEnabled, { cfg = cfg.copy(localEnabled = it) }) }
                if (cfg.localEnabled) {
                    Button(shape = RoundedCornerShape(8.dp), onClick = { localPick.launch(null) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF202020), contentColor = Color(0xFFFFB000))) { Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text(if (cfg.localPath.isBlank()) "选择本地存储目录" else "已选择本地目录") }
                    if (cfg.localPath.isNotBlank()) Text(cfg.localPath, color = Color(0xFFB0B0B0), fontSize = 11.sp)
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) { Text("WebDAV", color = Color.White, modifier = Modifier.weight(1f)); Switch(cfg.webdavEnabled, { cfg = cfg.copy(webdavEnabled = it) }) }
                if (cfg.webdavEnabled) {
                    Field("WebDAV地址", cfg.webdavUrl) { cfg = cfg.copy(webdavUrl = it) }
                    Field("用户名", cfg.webdavUser) { cfg = cfg.copy(webdavUser = it) }
                    Field("密码", cfg.webdavPass) { cfg = cfg.copy(webdavPass = it) }
                    Button(shape = RoundedCornerShape(8.dp), onClick = { test = "测试中..."; onTestWebDav(cfg.webdavUrl, cfg.webdavUser, cfg.webdavPass) { test = it } }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF202020), contentColor = Color(0xFFFFB000))) { Icon(Icons.Default.NetworkCheck, null); Spacer(Modifier.width(8.dp)); Text("自动测试http/https") }
                    test?.let { Text(it, color = if (it.startsWith("✅")) Color(0xFF4CAF50) else Color(0xFFFFB000), fontSize = 12.sp) }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) { Text("SMB/Samba", color = Color.White, modifier = Modifier.weight(1f)); Switch(cfg.smbEnabled, { cfg = cfg.copy(smbEnabled = it) }) }
                if (cfg.smbEnabled) {
                    Field("SMB地址", cfg.smbUrl) { cfg = cfg.copy(smbUrl = it) }
                    Field("用户名", cfg.smbUser) { cfg = cfg.copy(smbUser = it) }
                    Field("密码", cfg.smbPass) { cfg = cfg.copy(smbPass = it) }
                    Text("SMB为实验性；当前会复制到本地 SMB 备份目录", color = Color(0xFFB0B0B0), fontSize = 11.sp)
                }
                Spacer(Modifier.height(10.dp))
                Button(shape = RoundedCornerShape(8.dp), onClick = { onSaveStorage(cfg); test = "✅ 已保存" }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB000), contentColor = Color.Black)) { Text("保存存储设置") }
            }
        }
        Button(shape = RoundedCornerShape(8.dp), onClick = onExportLogs, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB000), contentColor = Color.Black)) {
            Icon(Icons.Default.BugReport, null); Spacer(Modifier.width(8.dp)); Text("导出日志/崩溃信息")
        }
        Spacer(Modifier.height(50.dp))
    }
        val progress = if (scrollState.maxValue == 0) 1f else scrollState.value.toFloat() / scrollState.maxValue
        RightScrollProgressBar(progress, visibleFraction = if (scrollState.maxValue == 0) 1f else 0.22f)
    }
}

@Composable private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF111111)), shape = RoundedCornerShape(12.dp)) { Column(Modifier.fillMaxWidth().padding(14.dp), content = content) }
}
@Composable private fun Field(label: String, value: String, onChange: (String)->Unit) { OutlinedTextField(value, onChange, label = { Text(label, color = Color(0xFFE0E0E0)) }, modifier = Modifier.fillMaxWidth(), singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedLabelColor = Color.White, unfocusedLabelColor = Color(0xFFE0E0E0), focusedBorderColor = Color(0xFFFFB000), unfocusedBorderColor = Color(0xFF9A9A9A), cursorColor = Color(0xFFFFB000))) }
