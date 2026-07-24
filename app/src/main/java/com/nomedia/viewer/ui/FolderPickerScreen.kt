package com.nomedia.viewer.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.nomedia.viewer.FolderNode

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderPickerScreen(
    folderTrees: Map<String, FolderNode>,
    importedPaths: Set<String>, // set of "rootUri|relativePath" that are imported
    onImportFolders: (List<Pair<String, String>>) -> Unit, // (rootUri, relativePath) pairs
    onRemoveRootFolder: (Uri) -> Unit
) {
    var multiSelectMode by remember { mutableStateOf(false) }
    val selectedPaths = remember { mutableStateMapOf<String, Boolean>() }
    var allNodes by remember { mutableStateOf<List<Pair<String, FolderNode>>>(emptyList()) }

    // Flatten tree into list for display
    LaunchedEffect(folderTrees) {
        val flat = mutableListOf<Pair<String, FolderNode>>()
        folderTrees.forEach { (rootUri, rootNode) ->
            flattenTree(rootUri, rootNode, flat)
        }
        allNodes = flat
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text("选择扫描目录", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(2.dp))
                Text("长按子文件夹可多选，选完后点「导入」", fontSize = 13.sp, color = Color(0xFF888888))
            }
        }

        Spacer(Modifier.height(12.dp))

        // Import button (when in multi-select or has selections)
        AnimatedVisibility(visible = selectedPaths.isNotEmpty()) {
            Button(
                onClick = {
                    val imports = selectedPaths.keys.map { key ->
                        val parts = key.split("|", limit = 2)
                        parts[0] to (parts.getOrElse(1) { "" })
                    }
                    onImportFolders(imports)
                    selectedPaths.clear()
                    multiSelectMode = false
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE94560)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("导入所选 (${selectedPaths.size})", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (selectedPaths.isNotEmpty()) Spacer(Modifier.height(8.dp))

        if (allNodes.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.FolderOff, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color(0xFF555555))
                    Spacer(Modifier.height(12.dp))
                    Text("暂无文件夹", color = Color(0xFF777777), fontSize = 16.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("请先在「设置」中添加手机文件夹", color = Color(0xFF555555), fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                items(allNodes, key = { it.first + "|" + it.second.relativePath }) { (rootUri, node) ->
                    val nodeKey = "$rootUri|${node.relativePath}"
                    val isSelected = selectedPaths.containsKey(nodeKey)
                    val isImported = nodeKey in importedPaths
                    val isLeaf = node.children.isEmpty()
                    val isRoot = node.depth == 0

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = (node.depth * 20).dp)
                            .combinedClickable(
                                onClick = {
                                    if (multiSelectMode || isImported) {
                                        // In multi-select: toggle selection
                                        if (isSelected) selectedPaths.remove(nodeKey)
                                        else selectedPaths[nodeKey] = true
                                    }
                                    // Single tap on non-multiselect = expand handled by depth indicator
                                },
                                onLongClick = {
                                    if (!isRoot) {
                                        multiSelectMode = true
                                        selectedPaths[nodeKey] = true
                                    }
                                }
                            ),
                        colors = CardDefaults.cardColors(
                            containerColor = when {
                                isSelected -> Color(0xFF2A1A3E)
                                isImported -> Color(0xFF1A3A2E)
                                else -> Color(0xFF16213E)
                            }
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Expand indicator for root/dirs with children
                            if (isRoot) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color(0xFFE94560), modifier = Modifier.size(20.dp))
                            } else if (isLeaf) {
                                Spacer(Modifier.width(20.dp))
                            } else {
                                Icon(Icons.Default.ArrowRight, contentDescription = null, tint = Color(0xFF888888), modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(6.dp))

                            // Icon
                            Icon(
                                when {
                                    isRoot -> Icons.Default.Storage
                                    isLeaf -> Icons.Default.Image
                                    else -> Icons.Default.Folder
                                },
                                contentDescription = null,
                                tint = if (isImported) Color(0xFF4CAF50) else if (isSelected) Color(0xFFE94560) else Color(0xFF888888),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))

                            // Name
                            Text(
                                node.name,
                                color = if (isImported) Color(0xFF88CC88) else if (isSelected) Color.White else Color(0xFFCCCCCC),
                                fontSize = 14.sp,
                                fontWeight = if (isRoot) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )

                            // Status badge
                            if (isImported && !isSelected) {
                                Text("已导入", color = Color(0xFF4CAF50), fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp).background(Color(0x334CAF50), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                            if (isSelected) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFFE94560), modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun flattenTree(rootUri: String, node: FolderNode, result: MutableList<Pair<String, FolderNode>>) {
    result.add(rootUri to node)
    for (child in node.children) {
        flattenTree(rootUri, child, result)
    }
}
