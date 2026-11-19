package com.bird.fiber.ui.screens.sidebar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bird.fiber.data.local.TagNode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun TagList(
    tags: List<TagNode>,
    organizing: Boolean,
    onTagClick: (String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Deliberately local: rebuilding the view resets expansion, never persisted.
    var expanded by remember { mutableStateOf(emptySet<String>()) }
    val rows = buildList<Pair<TagNode, Int>> {
        fun append(nodes: List<TagNode>, depth: Int) {
            nodes.forEach { node ->
                add(node to depth)
                if (node.path in expanded) append(node.children, depth + 1)
            }
        }
        append(tags, 0)
    }
    LazyColumn(modifier.fillMaxWidth()) {
        if (organizing) item { Text("标签数据仍在整理，结果将陆续更新", Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall) }
        if (tags.isEmpty() && !organizing) item {
            Text("在笔记正文中输入 #标签 整理笔记，也可点击上方加号添加标签。", Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
        }
        items(rows, key = { it.first.path }) { (node, depth) ->
            Row(Modifier.fillMaxWidth().padding(start = (8 + depth * 12).dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (node.children.isNotEmpty()) {
                    IconButton(onClick = {
                        expanded = if (node.path in expanded) expanded - node.path else expanded + node.path
                    }, modifier = Modifier.size(32.dp)) {
                        Icon(if (node.path in expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                            if (node.path in expanded) "折叠 ${node.displayPath}" else "展开 ${node.displayPath}")
                    }
                } else Spacer(Modifier.width(32.dp))
                Row(Modifier.weight(1f).clickable { onTagClick(node.path) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(node.displayPath.substringAfterLast('/'), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(node.noteCount.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (node.canDelete) IconButton(onClick = { onDelete(node.path) }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.DeleteOutline, "删除空标签 ${node.displayPath}", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
internal fun AddTagDialog(onDismiss: () -> Unit, onAdd: suspend (String) -> String, onAdded: (String) -> Unit) {
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("添加标签") },
        text = {
            Column {
                OutlinedTextField(input, { input = it; error = null }, label = { Text("标签名称") },
                    placeholder = { Text("读书/心理学") }, singleLine = true, isError = error != null, enabled = !saving)
                Text(error ?: "可带 # 前缀，多级标签用 / 分隔。", color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = {
            TextButton(enabled = !saving && input.isNotBlank(), onClick = {
                saving = true
                scope.launch {
                    try { onAdded(onAdd(input)) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) { error = failure.message ?: "添加失败，请重试" }
                    finally { saving = false }
                }
            }) { Text("添加") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("取消") } }
    )
}
