package com.nomedia.viewer.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nomedia.viewer.StorageConfig
import com.nomedia.viewer.StorageType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    storageConfig: StorageConfig,
    columnCount: Int,
    onColumnCountChange: (Int) -> Unit,
    onFolderAdded: (Uri) -> Unit,
    onSave: (StorageConfig) -> Unit
) {
    var type by remember { mutableStateOf(storageConfig.type) }
    var localPath by remember { mutableStateOf(storageConfig.localPath) }
    var webdavUrl by remember { mutableStateOf(storageConfig.webdavUrl) }
    var webdavUser by remember { mutableStateOf(storageConfig.webdavUser) }
    var webdavPass by remember { mutableStateOf(storageConfig.webdavPass) }
    var smbUrl by remember { mutableStateOf(storageConfig.smbUrl) }
    var smbUser by remember { mutableStateOf(storageConfig.smbUser) }
    var smbPass by remember { mutableStateOf(storageConfig.smbPass) }
    var enabled by remember { mutableStateOf(storageConfig.enabled) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    val dirPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? -> uri?.let { localPath = it.toString(); testResult = "✅ 已选择目录" } }

    val folderAdderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? -> uri?.let { onFolderAdded(it) } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // === Add Folder Section ===
        Text("添加浏览目录", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { folderAdderLauncher.launch(null) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F3460)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.CreateNewFolder, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text("添加手机文件夹", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        Text("添加后，在「文件夹」页长按选择子目录并导入", color = Color(0xFF888888), fontSize = 12.sp)

        Spacer(Modifier.height(24.dp))

        // === Column Count ===
        Text("浏览布局", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(8.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF16213E)), shape = RoundedCornerShape(12.dp)) {
            Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("瀑布流列数", color = Color(0xFFAAAAAA), fontSize = 14.sp, modifier = Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = columnCount == 1, onClick = { onColumnCountChange(1) }, label = { Text("单列", fontSize = 13.sp) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF0F3460), selectedLabelColor = Color.White))
                    FilterChip(selected = columnCount == 2, onClick = { onColumnCountChange(2) }, label = { Text("双列", fontSize = 13.sp) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF0F3460), selectedLabelColor = Color.White))
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // === Storage Settings ===
        Text("收藏存储", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(8.dp))

        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF16213E)), shape = RoundedCornerShape(12.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("自动存储收藏", color = Color.White, fontSize = 16.sp)
                        Text("收藏图片时自动备份", color = Color(0xFF888888), fontSize = 12.sp)
                    }
                    Switch(checked = enabled, onCheckedChange = { enabled = it }, colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF0F3460)))
                }

                AnimatedVisibility(visible = enabled) {
                    Column {
                        Spacer(Modifier.height(16.dp))
                        Text("存储方式", color = Color(0xFFAAAAAA), fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(StorageType.LOCAL to "本地", StorageType.WEBDAV to "WebDAV", StorageType.SMB to "SMB").forEach { (st, label) ->
                                FilterChip(selected = type == st, onClick = { type = st }, label = { Text(label, fontSize = 13.sp) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF0F3460), selectedLabelColor = Color.White))
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Text("配置", color = Color(0xFFAAAAAA), fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))

                        when (type) {
                            StorageType.LOCAL -> {
                                Button(onClick = { dirPickerLauncher.launch(null) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A3A5C)), modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(if (localPath.isEmpty()) "选择存储目录" else "已选择目录")
                                }
                                if (localPath.isNotEmpty()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(localPath, color = Color(0xFF888888), fontSize = 11.sp)
                                }
                            }
                            StorageType.WEBDAV -> {
                                OutlinedTextField(value = webdavUrl, onValueChange = { webdavUrl = it }, label = { Text("WebDAV 地址") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFF0F3460), unfocusedBorderColor = Color(0xFF333333)))
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(value = webdavUser, onValueChange = { webdavUser = it }, label = { Text("用户名") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFF0F3460), unfocusedBorderColor = Color(0xFF333333)))
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(value = webdavPass, onValueChange = { webdavPass = it }, label = { Text("密码") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFF0F3460), unfocusedBorderColor = Color(0xFF333333)))
                                Spacer(Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        isTesting = true
                                        testResult = "测试中..."
                                        scope.launch {
                                            testResult = testWebdavAuto(webdavUrl, webdavUser, webdavPass)
                                            isTesting = false
                                        }
                                    },
                                    enabled = !isTesting,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A3A5C)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(if (isTesting) Icons.Default.HourglassTop else Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(if (isTesting) "测试中..." else "测试连接")
                                }
                            }
                            StorageType.SMB -> {
                                OutlinedTextField(value = smbUrl, onValueChange = { smbUrl = it }, label = { Text("SMB 地址") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFF0F3460), unfocusedBorderColor = Color(0xFF333333)))
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(value = smbUser, onValueChange = { smbUser = it }, label = { Text("用户名") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFF0F3460), unfocusedBorderColor = Color(0xFF333333)))
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(value = smbPass, onValueChange = { smbPass = it }, label = { Text("密码") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFF0F3460), unfocusedBorderColor = Color(0xFF333333)))
                                Spacer(Modifier.height(8.dp))
                                Text("SMB 功能为实验性", color = Color(0xFF666666), fontSize = 11.sp)
                            }
                        }

                        if (testResult != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(testResult!!, color = if (testResult!!.contains("✅")) Color(0xFF4CAF50) else if (testResult!!.contains("❌")) Color(0xFFFF6B6B) else Color(0xFFAAAAAA), fontSize = 13.sp)
                        }
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = {
                            onSave(StorageConfig(type = type, localPath = localPath, webdavUrl = webdavUrl, webdavUser = webdavUser, webdavPass = webdavPass, smbUrl = smbUrl, smbUser = smbUser, smbPass = smbPass, enabled = enabled))
                            testResult = "✅ 设置已保存"
                        }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F3460))) {
                            Text("保存设置")
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

private suspend fun testWebdavAuto(url: String, user: String, pass: String): String = withContext(Dispatchers.IO) {
    if (url.isBlank()) return@withContext "❌ 请先输入 WebDAV 地址"

    var testUrl = url.trim()

    // Auto-add protocol if missing
    if (!testUrl.startsWith("http://") && !testUrl.startsWith("https://")) {
        // Try https first, then http
        val results = listOf("https://", "http://").map { proto ->
            tryProto("$proto$testUrl", user, pass)
        }
        val success = results.firstOrNull { it.startsWith("✅") }
        if (success != null) return@withContext success
        return@withContext results.lastOrNull() ?: "❌ 连接失败"
    }

    tryProto(testUrl, user, pass)
}

private fun tryProto(url: String, user: String, pass: String): String {
    return try {
        val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "OPTIONS"
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        conn.instanceFollowRedirects = true
        if (user.isNotBlank()) {
            val auth = android.util.Base64.encodeToString("$user:$pass".toByteArray(), android.util.Base64.NO_WRAP)
            conn.setRequestProperty("Authorization", "Basic $auth")
        }
        val code = conn.responseCode
        conn.disconnect()
        if (code in 200..299) "✅ 连接成功 (HTTP $code) — $url"
        else if (code in 300..399) "✅ 已重定向 (HTTP $code) — $url"
        else "❌ 服务器返回 HTTP $code — $url"
    } catch (e: java.net.UnknownHostException) {
        "❌ 域名解析失败: ${e.localizedMessage ?: "未知主机"}"
    } catch (e: java.net.ConnectException) {
        "❌ 连接被拒绝: ${e.localizedMessage ?: "拒绝连接"}"
    } catch (e: javax.net.ssl.SSLException) {
        // SSL failed for https, try http instead
        if (url.startsWith("https://")) "❌ SSL 握手失败"
        else "❌ SSL 错误: ${e.localizedMessage}"
    } catch (e: Exception) {
        "❌ 失败: ${e.localizedMessage ?: "未知错误"}"
    }
}
