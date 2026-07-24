package com.nomedia.viewer.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.nomedia.viewer.FolderManager

@Composable
fun FolderPickerScreen(
    folders: List<FolderManager.ManagedFolder>,
    onToggleFolder: (uriStr: String, enabled: Boolean) -> Unit,
    onRemoveFolder: (uriStr: String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("扫描目录管理", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(4.dp))
        Text("开启的目录将递归扫描其下所有子文件夹（包括 .nomedia）", fontSize = 13.sp, color = Color(0xFF888888))
        Spacer(Modifier.height(16.dp))

        if (folders.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.FolderOff, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color(0xFF555555))
                    Spacer(Modifier.height(12.dp))
                    Text("尚未添加任何文件夹", color = Color(0xFF777777), fontSize = 16.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("请先在「设置」中添加手机文件夹", color = Color(0xFF555555), fontSize = 13.sp)
                }
            }
        } else {
            Text("已添加 ${folders.size} 个目录", color = Color(0xFFAAAAAA), fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(folders, key = { _, f -> f.uriStr }) { _, folder ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (folder.isEnabled) Color(0xFF1A3A5C) else Color(0xFF16213E)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Folder,
                                contentDescription = null,
                                tint = if (folder.isEnabled) Color(0xFFE94560) else Color(0xFF555555),
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(folder.name, color = if (folder.isEnabled) Color.White else Color(0xFF888888), fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(folder.path, color = Color(0xFF666666), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            // Toggle switch
                            Switch(
                                checked = folder.isEnabled,
                                onCheckedChange = { onToggleFolder(folder.uriStr, it) },
                                colors = SwitchDefaults.colors(
                                    checkedTrackColor = Color(0xFF0F3460),
                                    checkedThumbColor = Color(0xFFE94560)
                                ),
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                            // Remove
                            IconButton(onClick = { onRemoveFolder(folder.uriStr) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "移除", tint = Color(0x66FF6B6B), modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
